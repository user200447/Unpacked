package com.jassemsendi.unpacked.ui.ask

import com.jassemsendi.unpacked.agent.ChartKind
import com.jassemsendi.unpacked.agent.ModelLocality
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.ui.home.RecordDetailUi

data class AskState(
    /** What the user typed, as typed. */
    val question: String = "",
    /** Whether a model can answer: being checked, ready with its name, or off with the reason. */
    val model: ModelUi = ModelUi.Checking,
    /** True from Ask until the outcome lands; the last outcome stays on screen meanwhile. */
    val isAsking: Boolean = false,
    /** How the last question ended, until the next one does. */
    val outcome: OutcomeUi? = null,
    /** The payload waiting in the Payload inspector for the user to send or refuse, while a cloud question is asked. */
    val payload: PayloadUi? = null,
    /** The Record opened from a citation, if any. */
    val detail: RecordDetailUi? = null,
    /** Why the last ask or open failed, until the next one succeeds. */
    val error: String? = null,
)

/** Whether a model can answer right now, as the screen tells the user before they type. */
sealed interface ModelUi {
    data object Checking : ModelUi

    /**
     * A model is ready; [marker] is how every answer is marked, such as "local · qwen3:8b", and [locality] says
     * whether a question leaves the device, in which case the Payload inspector shows it first.
     */
    data class Ready(
        val marker: String,
        val locality: ModelLocality,
    ) : ModelUi

    /** No model answers; [message] says why and what answers without one. */
    data class Off(
        val message: String,
    ) : ModelUi
}

/** What would leave the device for a cloud model (spec story 40): the model, and the exact request as text. */
data class PayloadUi(
    val model: String,
    val text: String,
)

/** How a question ended: with an answer, or with the agent stopped at its step cap. */
sealed interface OutcomeUi {
    data class Answered(
        val answer: AnswerUi,
    ) : OutcomeUi

    /** The model was still calling Tools at the cap; [message] says so and [trace] shows what it called. */
    data class Stopped(
        val message: String,
        val trace: List<TraceStepUi>,
    ) : OutcomeUi
}

/** An answer as shown: its text with the markers in it, each marker's Record, the trace, and how it is marked. */
data class AnswerUi(
    val text: String,
    val citations: List<CitationUi>,
    val trace: List<TraceStepUi>,
    /** Where the answer came from, such as "local · qwen3:8b" (spec story 44). */
    val modelMarker: String,
    /** The numbers the validator found in no Tool output; the answer is shown flagged when this is not empty. */
    val unverifiedNumbers: List<String>,
    val chart: ChartUi?,
) {
    val isVerified: Boolean get() = unverifiedNumbers.isEmpty()
}

/** One marker in the answer, such as `[3]`, and the Provenance of the Record it opens. */
data class CitationUi(
    val marker: Int,
    val provenance: Provenance,
)

/** One Tool call of the trace, in call order: what was called with what, and what came back. */
data class TraceStepUi(
    val number: Int,
    val tool: String,
    val input: String,
    val output: String,
    val failed: Boolean,
)

/**
 * A chart as drawn under an answer (glossary: Chart): a line over time or bars across Entities, every number the
 * Tool's own, with both axes named.
 */
data class ChartUi(
    val kind: ChartKind,
    val title: String,
    /** The value axis: what the numbers count, such as "AdShown". */
    val valueLabel: String,
    /** The point axis: "Month" or "Day" for a line, the Entity Kind for bars. */
    val pointLabel: String,
    val rows: List<ChartRowUi>,
    /** The top of the value axis: the largest value; 0 when every value is, and then the axis shows no top. */
    val top: Int,
)

/** One point of a chart: what it stands for and the number, exactly as the Tool output has them. */
data class ChartRowUi(
    val label: String,
    val value: Int,
)

sealed interface AskAction {
    data class QuestionChanged(
        val question: String,
    ) : AskAction

    /** Send the question as typed; nothing happens for a blank one. */
    data object Ask : AskAction

    /** Ask again whether a model answers, say after starting Ollama. */
    data object CheckModel : AskAction

    /** Let the payload in the inspector leave the device; the question goes on. */
    data object SendPayload : AskAction

    /** Keep the payload on the device; the question ends with nothing sent. */
    data object RefusePayload : AskAction

    /** Open the Record a citation marker names. */
    data class OpenCitation(
        val provenance: Provenance,
    ) : AskAction

    data object CloseDetail : AskAction
}

/** Where the Ask screen leads: back to the timeline, to the Gap report, and to Settings when the agent is off. */
data class AskDestinations(
    val onBack: () -> Unit,
    val onGapReport: () -> Unit,
    val onSettings: () -> Unit,
)
