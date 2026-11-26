package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.model.Error
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.Result

/**
 * The agent the Ask screen talks to: one plain-language question in, one [AskOutcome] out, with the Tool trace
 * that produced it and a Provenance behind every citation. Answers only questions; it writes no letters and maps
 * no files (ADR 0006).
 */
public interface AskAgent {
    /** Whether a model can answer now, and which one. */
    public suspend fun available(): Result<ModelInfo, AgentUnavailable>

    /**
     * Answers [question]. When the model is a cloud one, its first request is shown to [inspector] before it leaves
     * the device, and a refusal ends the question with nothing sent (ADR 0006); a local model never asks.
     */
    public suspend fun ask(
        question: String,
        inspector: PayloadInspector = PayloadInspector.SendAll,
    ): Result<AskOutcome, AskError>
}

/** How a question ended: with an answer, or with the agent stopped at its step cap (spec story 43). */
public sealed interface AskOutcome {
    public data class Answered(
        val answer: Answer,
    ) : AskOutcome

    /** The model kept calling Tools until the cap; [trace] is what it called before it was stopped. */
    public data class StepsExhausted(
        val stepCap: Int,
        val trace: List<TraceStep>,
        val model: ModelInfo,
    ) : AskOutcome
}

/**
 * An answer as the user sees it: the text with its citation markers, the Provenance each marker opens, the
 * Tool trace behind it, the numbers the validator could not find in any Tool output (none when the answer is
 * verified), the chart the model asked for, if any, and the model that wrote it.
 */
public data class Answer(
    val text: String,
    val citations: List<Citation>,
    val trace: List<TraceStep>,
    val unverifiedNumbers: List<String>,
    val chart: ChartSpec?,
    val model: ModelInfo,
) {
    /** True when every number in [text] appears in some Tool output; otherwise the answer is shown flagged. */
    val isVerified: Boolean get() = unverifiedNumbers.isEmpty()
}

/** One marker in an answer, such as `[3]`, and the Provenance it opens. */
public data class Citation(
    val marker: Int,
    val provenance: Provenance,
)

/** One Tool call of the trace (glossary: Tool trace): what was called with what, and what came back. */
public data class TraceStep(
    val number: Int,
    val tool: String,
    /** The call's input as JSON, as the model wrote it. */
    val input: String,
    /** The output as the model saw it: JSON, or the reason the call failed. */
    val output: String,
    val failed: Boolean,
)

/** Why a question got no outcome (ADR 0009). */
public sealed interface AskError : Error {
    public data class Unavailable(
        val reason: AgentUnavailable,
    ) : AskError

    public data object BlankQuestion : AskError

    /** The user refused the payload in the Payload inspector; nothing left the device. */
    public data object PayloadRefused : AskError

    /** The model stopped answering mid-way, or answered with nothing the agent could use. */
    public data class ModelFailed(
        val message: String,
    ) : AskError
}
