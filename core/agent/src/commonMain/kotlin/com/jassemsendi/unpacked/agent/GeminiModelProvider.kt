package com.jassemsendi.unpacked.agent

import ai.koog.http.client.KoogHttpClientException
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.CancellationException

/**
 * The cloud binding of [ModelProvider] (spec story 39, ADR 0006): Google Gemini Flash on its free tier, reached with
 * the key the user saved in the platform's secure storage. Every connection reads the key again and asks the cloud
 * which models it serves to that key, so a key saved, removed or revoked since the last question, or a quota spent
 * since, is known before anything is sent, and the newest Flash model Google still serves is the one used, since
 * Google retires them for new accounts. The model only ever receives Tool outputs, shown first in the Payload inspector.
 */
public class GeminiModelProvider(
    private val keys: SecureKeyStore,
    private val connectCloud: suspend (key: String) -> ConnectedCloud = GeminiConnection()::connect,
) : ModelProvider {
    override suspend fun connect(): Result<ConnectedModel, AgentUnavailable> =
        when (val read = keys.read(AgentSettings.GEMINI_KEY)) {
            is Result.Error -> Result.Error(AgentUnavailable.KeyUnreadable)
            is Result.Success -> read.data?.let { connectWith(it) } ?: Result.Error(AgentUnavailable.NoKey)
        }

    private suspend fun connectWith(key: String): Result<ConnectedModel, AgentUnavailable> =
        try {
            val cloud = connectCloud(key)
            Result.Success(ConnectedModel(ModelInfo(cloud.model.id, ModelLocality.Cloud), cloud.model, cloud.executor))
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.Error(cloudFailure(e) ?: AgentUnavailable.CloudUnreachable(e.message ?: "The cloud did not answer."))
        }

    public companion object {
        /**
         * The shape of every Gemini Flash model offered, under the id Google recommends today; the live list decides
         * the id actually used. Only Tool outputs may reach it, its free tier being why.
         */
        public val MODEL: LLModel = GoogleModels.Gemini3_7Flash.copy(id = "gemini-3.8-flash")
    }
}

/** A cloud model ready for questions: which one Google serves, and the executor that reaches it. */
public class ConnectedCloud(
    public val model: LLModel,
    public val executor: PromptExecutor,
)

/**
 * The newest Gemini Flash model among those Google lists for a key, a Flash Lite only when no plain Flash is there;
 * null when none is. Ids may come as "models/gemini-3.8-flash" or bare.
 */
internal fun chooseFlash(offered: List<String>): LLModel? {
    val flash =
        offered
            .map { it.removePrefix("models/") }
            .mapNotNull { id -> FLASH.matchEntire(id)?.let { match -> Flash(id, match) } }
            .maxWithOrNull(compareBy<Flash> { !it.lite }.thenBy { it.major }.thenBy { it.minor })
    return flash?.let { GeminiModelProvider.MODEL.copy(id = it.id) }
}

private class Flash(
    val id: String,
    match: MatchResult,
) {
    val major: Int = match.groupValues[1].toInt()
    val minor: Int = match.groupValues[2].toIntOrNull() ?: 0
    val lite: Boolean = match.groupValues[3].isNotEmpty()
}

private val FLASH = Regex("""gemini-(\d+)(?:\.(\d+))?-flash(-lite)?""")

/** Google lists no Flash model for the key; [offered] is what it lists instead. */
internal class NoFlashModelException(
    val offered: List<String>,
) : Exception("Google lists no Gemini Flash model: ${offered.ifEmpty { listOf("nothing") }.joinToString()}")

/**
 * The live connection: one Google client per key, kept until the key changes, and asked for its model list before
 * a question so that a rejected key or a spent quota comes back as a typed reason rather than mid-answer, and so
 * that the Flash model asked for is one Google still serves.
 */
internal class GeminiConnection {
    private var current: Pair<String, GoogleLLMClient>? = null

    suspend fun connect(key: String): ConnectedCloud {
        val client = clientFor(key)
        val offered = client.models().map { it.id }
        val model = chooseFlash(offered) ?: throw NoFlashModelException(offered)
        return ConnectedCloud(model, MultiLLMPromptExecutor(client))
    }

    private fun clientFor(key: String): GoogleLLMClient {
        current?.let { (known, client) -> if (known == key) return client else client.close() }
        return GoogleLLMClient(key, GoogleClientSettings(), KtorKoogHttpClient.Factory()).also { current = key to it }
    }
}

/**
 * What a cloud model's refusal means, read from the HTTP status wherever it sits in the cause chain: too many
 * requests is the quota, not found is the model gone, any other client-side refusal is the key; a missing Flash
 * model is its own case. Null for anything else, which is not a refusal.
 */
internal fun cloudFailure(failure: Throwable): AgentUnavailable? {
    val chain = generateSequence(failure) { it.cause?.takeIf { cause -> cause !== it } }.toList()
    chain.filterIsInstance<NoFlashModelException>().firstOrNull()?.let {
        return AgentUnavailable.NoCloudModel(it.message ?: "Google lists no Gemini Flash model")
    }
    val status = chain.filterIsInstance<KoogHttpClientException>().firstNotNullOfOrNull { it.statusCode }
    return when (status) {
        TOO_MANY_REQUESTS -> AgentUnavailable.QuotaExceeded
        NOT_FOUND -> AgentUnavailable.NoCloudModel("Google no longer serves the model it listed")
        BAD_REQUEST, UNAUTHORIZED, FORBIDDEN -> AgentUnavailable.KeyRejected
        else -> null
    }
}

private const val BAD_REQUEST = 400
private const val UNAUTHORIZED = 401
private const val FORBIDDEN = 403
private const val NOT_FOUND = 404
private const val TOO_MANY_REQUESTS = 429
