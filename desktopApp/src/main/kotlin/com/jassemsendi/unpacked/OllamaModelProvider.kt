package com.jassemsendi.unpacked

import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.toLLModel
import ai.koog.prompt.llm.LLMCapability
import com.jassemsendi.unpacked.agent.AgentUnavailable
import com.jassemsendi.unpacked.agent.ConnectedModel
import com.jassemsendi.unpacked.agent.InstalledModel
import com.jassemsendi.unpacked.agent.LocalModelProvider
import com.jassemsendi.unpacked.agent.ModelInfo
import com.jassemsendi.unpacked.agent.ModelLocality
import com.jassemsendi.unpacked.agent.PREFERRED_MODELS
import com.jassemsendi.unpacked.agent.chooseModel
import com.jassemsendi.unpacked.model.Result

/**
 * The desktop binding of [LocalModelProvider] (spec story 44, ADR 0008): Ollama on localhost, a local model, so the person's data
 * never leaves the machine. Every question asks Ollama which models it has, so one started or stopped since the
 * last question is noticed; the chosen model is the one named by `UNPACKED_OLLAMA_MODEL` when installed, else the
 * first preferred family that calls Tools.
 */
class OllamaModelProvider(
    private val baseUrl: String = OllamaClient.DEFAULT_BASE_URL,
    private val requested: String? = System.getenv(MODEL_VARIABLE),
    private val preferred: List<String> = PREFERRED_MODELS,
) : LocalModelProvider {
    override val isOffered: Boolean = true

    private val client: OllamaClient by lazy { OllamaClient(baseUrl) }
    private val executor: PromptExecutor by lazy { MultiLLMPromptExecutor(client) }

    override suspend fun connect(): Result<ConnectedModel, AgentUnavailable> {
        val cards =
            try {
                client.getModels()
            } catch (_: LLMClientException) {
                null
            } ?: return Result.Error(AgentUnavailable.OllamaNotRunning(baseUrl))
        val installed = cards.map { InstalledModel(it.name, LLMCapability.Tools in it.capabilities) }
        val chosen = chooseModel(installed, preferred, requested)
        return if (chosen == null) {
            Result.Error(AgentUnavailable.NoToolModel(baseUrl, installed.map { it.name }))
        } else {
            val card = cards.first { it.name == chosen }
            Result.Success(ConnectedModel(ModelInfo(chosen, ModelLocality.Local), card.toLLModel(), executor))
        }
    }

    companion object {
        /** Names the Ollama model to use, when the person has several installed. */
        const val MODEL_VARIABLE: String = "UNPACKED_OLLAMA_MODEL"
    }
}
