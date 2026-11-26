package com.jassemsendi.unpacked.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** One request the scripted model received: the prompt as sent and the Tools it was offered. */
class ModelCall(
    val prompt: Prompt,
    val tools: List<ToolDescriptor>,
) {
    val lastUserText: String
        get() =
            prompt.messages
                .filterIsInstance<Message.User>()
                .last()
                .parts
                .filterIsInstance<MessagePart.Text>()
                .joinToString("\n") { it.text }
}

/**
 * The fake LLM of seam 4: answers each request with the next scripted reply, the last reply repeating once the
 * script runs out, and keeps every request so a test can see what the model was asked and which Tools it had.
 */
class ScriptedModel(
    private vararg val script: Message.Assistant,
) : PromptExecutor() {
    val calls = mutableListOf<ModelCall>()

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        calls += ModelCall(prompt, tools)
        return script.getOrNull(calls.size - 1) ?: script.last()
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = error("The Ask agent does not stream")

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult = error("The Ask agent does not moderate")

    override fun close() = Unit
}

/** A model that fails every request, the way a connection dropping mid-answer does. */
class FailingModel(
    message: String,
) : ThrowingModel(IllegalStateException(message))

/** A model that fails every request with the given exception, the way a cloud refusing a key or a quota does. */
open class ThrowingModel(
    private val failure: Exception,
) : PromptExecutor() {
    private val message: String get() = failure.message ?: "failed"

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant = throw failure

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = error(message)

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult = error(message)

    override fun close() = Unit
}

private val meta = ResponseMetaInfo(Instant.DISTANT_PAST)

fun says(text: String): Message.Assistant = Message.Assistant(text, meta)

fun calls(
    tool: String,
    args: String = "{}",
): Message.Assistant = Message.Assistant(MessagePart.Tool.Call(id = "call-$tool", tool = tool, args = args), meta)

/** A provider that hands out the given executor as a local model (or a cloud one), or a scripted refusal. */
class FakeModelProvider(
    private val executor: PromptExecutor?,
    private val refusal: AgentUnavailable = AgentUnavailable.NoProvider,
    private val locality: ModelLocality = ModelLocality.Local,
) : LocalModelProvider {
    var connections = 0

    override val isOffered: Boolean = true

    override suspend fun connect(): Result<ConnectedModel, AgentUnavailable> {
        connections++
        return executor?.let {
            Result.Success(
                ConnectedModel(
                    ModelInfo("scripted", locality),
                    if (locality == ModelLocality.Cloud) {
                        GeminiModelProvider.MODEL
                    } else {
                        LLModel(LLMProvider.Ollama, "scripted", listOf(LLMCapability.Tools))
                    },
                    it,
                ),
            )
        } ?: Result.Error(refusal)
    }
}
