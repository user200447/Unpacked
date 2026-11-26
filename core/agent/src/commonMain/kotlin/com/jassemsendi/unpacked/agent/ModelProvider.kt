package com.jassemsendi.unpacked.agent

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.jassemsendi.unpacked.model.Error
import com.jassemsendi.unpacked.model.Result

/** Where a model runs: on this device, or at a cloud provider that receives Tool outputs and nothing else (ADR 0006). */
public enum class ModelLocality {
    Local,
    Cloud,
}

/** The model an answer came from, as the Ask screen marks every answer (spec story 44). */
public data class ModelInfo(
    val name: String,
    val locality: ModelLocality,
)

/**
 * Why no model can answer right now; what the Ask screen explains, each reason in its own words, before pointing
 * at the cards and the Gap report (spec story 42).
 */
public sealed interface AgentUnavailable : Error {
    /** This platform binds no model of its own: the phones, where only a cloud model can answer. */
    public data object NoProvider : AgentUnavailable

    /** Nothing answered at the Ollama address: Ollama is not installed, or it stopped. */
    public data class OllamaNotRunning(
        val baseUrl: String,
    ) : AgentUnavailable

    /** Ollama answered, but none of the models it has can call Tools. */
    public data class NoToolModel(
        val baseUrl: String,
        val installed: List<String>,
    ) : AgentUnavailable

    /** The user switched the agent off in Settings. */
    public data object Off : AgentUnavailable

    /** The cloud is chosen, but no Gemini key is saved. */
    public data object NoKey : AgentUnavailable

    /** A key may be saved, but the platform's secure storage would not hand it over. */
    public data object KeyUnreadable : AgentUnavailable

    /** Google refused the key. */
    public data object KeyRejected : AgentUnavailable

    /** Google accepted the key but the free tier's quota is spent for now. */
    public data object QuotaExceeded : AgentUnavailable

    /** The cloud could not be reached at all, most often with no network; [message] is the client's own wording. */
    public data class CloudUnreachable(
        val message: String,
    ) : AgentUnavailable
}

/**
 * The binding (ADR 0008) that says which model answers and how to reach it. Asked anew for every question, so a
 * model that stopped since the last one, a key saved since, or a changed Settings choice is noticed.
 */
public interface ModelProvider {
    public suspend fun connect(): Result<ConnectedModel, AgentUnavailable>
}

/**
 * The platform's own model, the one the Settings choice "local" means: Ollama on localhost on desktop; nothing on
 * the phones, which bind [NoModelProvider] so Settings does not offer the choice.
 */
public interface LocalModelProvider : ModelProvider {
    /** False where the platform has no local model to offer. */
    public val isOffered: Boolean
}

/** A model that answered: what to call it, how Koog addresses it, and the executor that talks to it. */
public class ConnectedModel(
    public val info: ModelInfo,
    public val model: LLModel,
    public val executor: PromptExecutor,
)

/** The local binding of a platform with no model of its own: every question is answered with why. */
public object NoModelProvider : LocalModelProvider {
    override val isOffered: Boolean = false

    override suspend fun connect(): Result<ConnectedModel, AgentUnavailable> = Result.Error(AgentUnavailable.NoProvider)
}

/**
 * The provider the agent is given (ADR 0006): it follows the Settings choice at every connection, to the platform's
 * [local] model, to the [cloud] one, or to nobody.
 */
public class SwitchingModelProvider(
    private val settings: AgentSettings,
    private val local: ModelProvider,
    private val cloud: ModelProvider,
) : ModelProvider {
    override suspend fun connect(): Result<ConnectedModel, AgentUnavailable> =
        when (settings.provider()) {
            ProviderChoice.Local -> local.connect()
            ProviderChoice.Cloud -> cloud.connect()
            ProviderChoice.Off -> Result.Error(AgentUnavailable.Off)
        }
}
