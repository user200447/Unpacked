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
 * the key the user saved in the platform's secure storage. Every connection reads the key again and probes the
 * cloud with it, so a key saved, removed or revoked since the last question, or a quota spent since, is known
 * before anything is sent. The model only ever receives Tool outputs, shown first in the Payload inspector.
 */
public class GeminiModelProvider(
    private val keys: SecureKeyStore,
    private val connectCloud: suspend (key: String) -> PromptExecutor = GeminiConnection()::executor,
) : ModelProvider {
    override suspend fun connect(): Result<ConnectedModel, AgentUnavailable> =
        when (val read = keys.read(AgentSettings.GEMINI_KEY)) {
            is Result.Error -> Result.Error(AgentUnavailable.KeyUnreadable)
            is Result.Success -> read.data?.let { connectWith(it) } ?: Result.Error(AgentUnavailable.NoKey)
        }

    private suspend fun connectWith(key: String): Result<ConnectedModel, AgentUnavailable> =
        try {
            val executor = connectCloud(key)
            Result.Success(ConnectedModel(ModelInfo(MODEL.id, ModelLocality.Cloud), MODEL, executor))
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.Error(cloudFailure(e) ?: AgentUnavailable.CloudUnreachable(e.message ?: "The cloud did not answer."))
        }

    public companion object {
        /** The one cloud model offered: Gemini Flash, whose free tier is why nothing but Tool outputs may reach it. */
        public val MODEL: LLModel = GoogleModels.Gemini2_5Flash
    }
}

/**
 * The live connection: one Google client per key, kept until the key changes, and asked for its model list before
 * a question so that a rejected key or a spent quota comes back as a typed reason rather than mid-answer.
 */
internal class GeminiConnection {
    private var current: Pair<String, GoogleLLMClient>? = null

    suspend fun executor(key: String): PromptExecutor {
        val client = clientFor(key)
        client.models()
        return MultiLLMPromptExecutor(client)
    }

    private fun clientFor(key: String): GoogleLLMClient {
        current?.let { (known, client) -> if (known == key) return client else client.close() }
        return GoogleLLMClient(key, GoogleClientSettings(), KtorKoogHttpClient.Factory()).also { current = key to it }
    }
}

/**
 * What a cloud model's refusal means, read from the HTTP status wherever it sits in the cause chain: too many
 * requests is the quota, any other client-side refusal is the key. Null for anything else, which is not a refusal.
 */
internal fun cloudFailure(failure: Throwable): AgentUnavailable? {
    val status =
        generateSequence(failure) { it.cause?.takeIf { cause -> cause !== it } }
            .filterIsInstance<KoogHttpClientException>()
            .firstNotNullOfOrNull { it.statusCode }
    return when (status) {
        TOO_MANY_REQUESTS -> AgentUnavailable.QuotaExceeded
        BAD_REQUEST, UNAUTHORIZED, FORBIDDEN -> AgentUnavailable.KeyRejected
        else -> null
    }
}

private const val BAD_REQUEST = 400
private const val UNAUTHORIZED = 401
private const val FORBIDDEN = 403
private const val TOO_MANY_REQUESTS = 429
