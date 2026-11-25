package com.jassemsendi.unpacked.ui.ask

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
    /** The Record opened from a citation, if any. */
    val detail: RecordDetailUi? = null,
    /** Why the last ask or open failed, until the next one succeeds. */
    val error: String? = null,
)

/** Whether a model can answer right now, as the screen tells the user before they type. */
sealed interface ModelUi {
    data object Checking : ModelUi

    /** A model is ready; [marker] is how every answer is marked, such as "local · qwen3:8b". */
    data class Ready(
        val marker: String,
    ) : ModelUi

    /** No model answers; [message] says why and what answers without one. */
    data class Off(
        val message: String,
    ) : ModelUi
}

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

/** A chart the model asked for, as rows the screen draws as bars. */
data class ChartUi(
    val title: String,
    val valueLabel: String,
    val rows: List<ChartRowUi>,
)

data class ChartRowUi(
    val label: String,
    val value: Int,
    /** 0 to 1: this row's share of the largest value, the bar's width. */
    val share: Float,
)

sealed interface AskAction {
    data class QuestionChanged(
        val question: String,
    ) : AskAction

    /** Send the question as typed; nothing happens for a blank one. */
    data object Ask : AskAction

    /** Ask again whether a model answers, say after starting Ollama. */
    data object CheckModel : AskAction

    /** Open the Record a citation marker names. */
    data class OpenCitation(
        val provenance: Provenance,
    ) : AskAction

    data object CloseDetail : AskAction
}
