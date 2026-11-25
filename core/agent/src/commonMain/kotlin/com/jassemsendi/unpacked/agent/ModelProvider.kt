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

/** Why no model can answer right now; what the Ask screen explains before pointing at the cards and the Gap report. */
public sealed interface AgentUnavailable : Error {
    /** This platform binds no model: the phones, until a cloud model is offered. */
    public data object NoProvider : AgentUnavailable

    /** Nothing answered at the Ollama address. */
    public data class OllamaNotRunning(
        val baseUrl: String,
    ) : AgentUnavailable

    /** Ollama answered, but none of the models it has can call Tools. */
    public data class NoToolModel(
        val baseUrl: String,
        val installed: List<String>,
    ) : AgentUnavailable
}

/**
 * The platform binding (ADR 0008) that says which model answers and how to reach it: Ollama on localhost on
 * desktop, nothing on the phones until a cloud model is offered. Asked anew for every question, so a model that
 * stopped since the last one is noticed.
 */
public interface ModelProvider {
    public suspend fun connect(): Result<ConnectedModel, AgentUnavailable>
}

/** A model that answered: what to call it, how Koog addresses it, and the executor that talks to it. */
public class ConnectedModel(
    public val info: ModelInfo,
    public val model: LLModel,
    public val executor: PromptExecutor,
)

/** The binding of a platform with no model: every question is answered with why the agent is off. */
public object NoModelProvider : ModelProvider {
    override suspend fun connect(): Result<ConnectedModel, AgentUnavailable> = Result.Error(AgentUnavailable.NoProvider)
}
