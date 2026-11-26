package com.jassemsendi.unpacked.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The request as the agent hands it to a cloud model's client, and as the Payload inspector shows it (spec story 40,
 * ADR 0006): the model asked, every message in full, in order, and every Tool the model is offered. The client adds
 * protocol framing and nothing else, so what is here is what leaves the device.
 */
@Serializable
public data class CloudPayload(
    val model: String,
    val messages: List<PayloadMessage>,
    val tools: List<PayloadTool>,
) {
    /** The payload as text a person reads: JSON, pretty-printed. */
    public fun render(): String = payloadJson.encodeToString(this)

    public companion object {
        public fun of(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): CloudPayload = CloudPayload(model.id, prompt.messages.map { it.toPayload() }, tools.map { it.toPayload() })
    }
}

/** One message of the request: who says it and what it holds, text, Tool calls or Tool results. */
@Serializable
public data class PayloadMessage(
    val role: String,
    val text: String? = null,
    val toolCalls: List<PayloadToolCall> = emptyList(),
    val toolResults: List<PayloadToolResult> = emptyList(),
)

@Serializable
public data class PayloadToolCall(
    val tool: String,
    val args: String,
)

@Serializable
public data class PayloadToolResult(
    val tool: String,
    val content: String,
)

/** One Tool as the model is told about it: its name, what it does, and what it takes. */
@Serializable
public data class PayloadTool(
    val name: String,
    val description: String,
    val parameters: List<PayloadParameter>,
)

@Serializable
public data class PayloadParameter(
    val name: String,
    val description: String,
    val type: String,
    val required: Boolean,
)

/**
 * What decides whether a cloud request may leave the device: the Ask screen, which shows the payload and waits for
 * the user. Consulted once per question, before its first request; a refusal ends the question with nothing sent.
 */
public fun interface PayloadInspector {
    public suspend fun approve(payload: CloudPayload): Boolean

    public companion object {
        /** Sends without asking: for a local model, which has no payload leaving the device. */
        public val SendAll: PayloadInspector = PayloadInspector { true }
    }
}

/** Thrown out of the executor when the inspector refuses, so the question ends before anything is sent. */
internal class PayloadRefusedException : Exception("The payload was refused; nothing was sent.")

/**
 * The executor a cloud question runs on: the first request is shown to the [inspector] before it goes, and goes
 * only if approved; every later request of the same question follows without asking.
 */
internal class InspectedExecutor(
    private val delegate: PromptExecutor,
    private val inspector: PayloadInspector,
) : PromptExecutor() {
    private var approved = false

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        if (!approved) {
            if (!inspector.approve(CloudPayload.of(prompt, model, tools))) throw PayloadRefusedException()
            approved = true
        }
        return delegate.execute(prompt, model, tools)
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

    /** The delegate belongs to the provider, which keeps it for the next question. */
    override fun close() = Unit
}

private val payloadJson = Json { prettyPrint = true }

private fun Message.toPayload(): PayloadMessage =
    PayloadMessage(
        role =
            when (this) {
                is Message.System -> "system"
                is Message.User -> "user"
                is Message.Assistant -> "assistant"
                else -> "other"
            },
        text = parts.text().ifEmpty { null },
        toolCalls = parts.filterIsInstance<MessagePart.Tool.Call>().map { PayloadToolCall(it.tool, it.args) },
        toolResults =
            parts.filterIsInstance<MessagePart.Tool.Result>().map { result ->
                PayloadToolResult(result.tool, result.parts.text())
            },
    )

private fun List<MessagePart>.text(): String = filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }

private fun ToolDescriptor.toPayload(): PayloadTool {
    val required = requiredParameters.map { it.toPayload(required = true) }
    val optional = optionalParameters.map { it.toPayload(required = false) }
    return PayloadTool(name, description, required + optional)
}

private fun ToolParameterDescriptor.toPayload(required: Boolean): PayloadParameter =
    PayloadParameter(name, description, type.describe(), required)

private fun ToolParameterType.describe(): String =
    when (this) {
        is ToolParameterType.String -> {
            "string"
        }

        is ToolParameterType.Integer -> {
            "integer"
        }

        is ToolParameterType.Float -> {
            "number"
        }

        is ToolParameterType.Boolean -> {
            "boolean"
        }

        is ToolParameterType.Enum -> {
            "one of ${entries.joinToString()}"
        }

        is ToolParameterType.List -> {
            "list of ${itemsType.describe()}"
        }

        is ToolParameterType.Object -> {
            "object"
        }

        // An optional parameter is "its type or null"; the person reads the type, the `required` flag says the rest.
        is ToolParameterType.AnyOf -> {
            types
                .map { it.type }
                .filterNot { it is ToolParameterType.Null }
                .joinToString(" or ") { it.describe() }
                .ifEmpty { "null" }
        }

        else -> {
            "value"
        }
    }
