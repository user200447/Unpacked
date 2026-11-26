package com.jassemsendi.unpacked.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassemsendi.unpacked.agent.Answer
import com.jassemsendi.unpacked.agent.AskAgent
import com.jassemsendi.unpacked.agent.AskError
import com.jassemsendi.unpacked.agent.AskOutcome
import com.jassemsendi.unpacked.agent.ChartSpec
import com.jassemsendi.unpacked.agent.ModelInfo
import com.jassemsendi.unpacked.agent.ModelLocality
import com.jassemsendi.unpacked.agent.PayloadInspector
import com.jassemsendi.unpacked.agent.TraceStep
import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.ui.home.RecordDetailUi
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.readRecord
import com.jassemsendi.unpacked.ui.toMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The Ask screen: whether a model answers, the question, and the outcome with its Tool trace and citations, each
 * citation opening the Record it names. The model is checked when the screen opens and whenever the user asks for
 * it again, so one started after the screen opened is found. For a cloud model the screen is the Payload inspector
 * too: the first request of a question waits in [AskState.payload] until the user sends or refuses it (ADR 0006).
 */
class AskViewModel(
    private val agent: AskAgent,
    private val events: EventRepository,
    private val claims: ClaimRepository,
    private val timeZone: TimeZone,
) : ViewModel() {
    private val _state = MutableStateFlow(AskState())
    val state = _state.asStateFlow()

    /** The user's pending answer to the Payload inspector, while a payload is shown. */
    private var pendingPayload: CompletableDeferred<Boolean>? = null

    private val inspector =
        PayloadInspector { payload ->
            val answer = CompletableDeferred<Boolean>()
            pendingPayload = answer
            _state.update { it.copy(payload = PayloadUi(payload.model, payload.render())) }
            try {
                answer.await()
            } finally {
                pendingPayload = null
                _state.update { it.copy(payload = null) }
            }
        }

    init {
        checkModel()
    }

    fun onAction(action: AskAction) {
        when (action) {
            is AskAction.QuestionChanged -> _state.update { it.copy(question = action.question) }
            AskAction.Ask -> ask()
            AskAction.CheckModel -> checkModel()
            AskAction.SendPayload -> pendingPayload?.complete(true)
            AskAction.RefusePayload -> pendingPayload?.complete(false)
            is AskAction.OpenCitation -> open(action.provenance)
            AskAction.CloseDetail -> _state.update { it.copy(detail = null) }
        }
    }

    private fun checkModel() {
        _state.update { it.copy(model = ModelUi.Checking) }
        viewModelScope.launch {
            val model =
                when (val available = agent.available()) {
                    is Result.Success -> {
                        ModelUi.Ready(available.data.marker(), available.data.locality)
                    }

                    is Result.Error -> {
                        ModelUi.Off(available.error.toMessage())
                    }
                }
            _state.update { it.copy(model = model) }
        }
    }

    private fun ask() {
        val before = _state.value
        if (before.question.isBlank() || before.isAsking) return
        _state.update { it.copy(isAsking = true, error = null) }
        viewModelScope.launch {
            val asked = agent.ask(before.question, inspector)
            _state.update { state ->
                when (asked) {
                    is Result.Success -> {
                        state.copy(isAsking = false, outcome = asked.data.toUi())
                    }

                    is Result.Error -> {
                        when (val error = asked.error) {
                            // The model went away since the check: the screen says so where the check does.
                            is AskError.Unavailable -> {
                                state.copy(isAsking = false, model = ModelUi.Off(error.reason.toMessage()))
                            }

                            else -> {
                                state.copy(isAsking = false, error = error.toMessage())
                            }
                        }
                    }
                }
            }
        }
    }

    /** A citation names a Provenance, not which repository holds it: an Event is tried first, then a Claim. */
    private fun open(provenance: Provenance) {
        viewModelScope.launch {
            val asEvent = readRecord(RecordRef.Event(provenance), events, claims, timeZone)
            val read =
                if (asEvent is Result.Error && asEvent.error == DataError.Local.NOT_FOUND) {
                    readRecord(RecordRef.Claim(provenance), events, claims, timeZone)
                } else {
                    asEvent
                }
            _state.update {
                when (read) {
                    is Result.Success -> it.copy(detail = read.data, error = null)
                    is Result.Error -> it.copy(detail = null, error = read.error.toMessage())
                }
            }
        }
    }
}

internal fun AskOutcome.toUi(): OutcomeUi =
    when (this) {
        is AskOutcome.Answered -> {
            OutcomeUi.Answered(answer.toUi())
        }

        is AskOutcome.StepsExhausted -> {
            OutcomeUi.Stopped(
                message =
                    "The model was still calling Tools after $stepCap calls, the most one question may spend, so it " +
                        "was stopped without an answer. Try a narrower question; the calls it made are below.",
                trace = trace.map(TraceStep::toUi),
            )
        }
    }

internal fun Answer.toUi(): AnswerUi =
    AnswerUi(
        text = text,
        citations = citations.map { CitationUi(it.marker, it.provenance) },
        trace = trace.map(TraceStep::toUi),
        modelMarker = model.marker(),
        unverifiedNumbers = unverifiedNumbers,
        chart = chart?.toUi(),
    )

internal fun TraceStep.toUi(): TraceStepUi = TraceStepUi(number, tool, input, output, failed)

internal fun ChartSpec.toUi(): ChartUi =
    ChartUi(
        kind = kind,
        title = title,
        valueLabel = valueLabel,
        pointLabel = pointLabel,
        rows = points.map { ChartRowUi(it.label, it.value) },
        top = points.maxOfOrNull { it.value } ?: 0,
    )

/** How an answer is marked with where it came from: "local" for a model on this device (spec story 44). */
internal fun ModelInfo.marker(): String =
    when (locality) {
        ModelLocality.Local -> "local · $name"
        ModelLocality.Cloud -> "cloud · $name"
    }
