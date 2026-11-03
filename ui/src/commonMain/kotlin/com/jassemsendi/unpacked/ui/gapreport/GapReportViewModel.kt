package com.jassemsendi.unpacked.ui.gapreport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.data.ImportRepository
import com.jassemsendi.unpacked.insights.GapReportReader
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.andThen
import com.jassemsendi.unpacked.model.map
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.readRecord
import com.jassemsendi.unpacked.ui.toMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The Gap report screen: the nine Verdicts for one Import, chosen among the stored ones, with the Record the
 * user opened from a Verdict's evidence. Nothing is read until the screen asks for a [GapReportAction.Refresh].
 */
class GapReportViewModel(
    private val imports: ImportRepository,
    private val events: EventRepository,
    private val claims: ClaimRepository,
    private val timeZone: TimeZone,
    private val reader: GapReportReader,
) : ViewModel() {
    private val _state = MutableStateFlow(GapReportState())
    val state = _state.asStateFlow()

    /** Bumped by every reload so a report in flight for an older choice is dropped on arrival. */
    private var generation = 0

    /** The Imports and the report over the chosen one, read together so the chooser never disagrees with the report. */
    private data class ReportChoice(
        val imports: List<Import>,
        val selected: Import?,
        val report: GapReportUi?,
    )

    fun onAction(action: GapReportAction) {
        when (action) {
            GapReportAction.Refresh -> reload(_state.value.selected)
            is GapReportAction.Select -> reload(action.id)
            is GapReportAction.Open -> open(action.ref)
            GapReportAction.CloseDetail -> _state.update { it.copy(detail = null) }
        }
    }

    private fun reload(requested: ImportId?) {
        val current = ++generation
        // Another Import empties the report at once; a plain refresh keeps the last one on screen until the new one lands.
        _state.update {
            if (requested == it.selected) {
                it.copy(isLoading = true)
            } else {
                it.copy(isLoading = true, selected = requested, report = null)
            }
        }
        viewModelScope.launch {
            val read = choose(requested)
            if (current != generation) return@launch
            _state.update { state ->
                when (read) {
                    is Result.Success -> {
                        state.copy(
                            isLoading = false,
                            error = null,
                            imports = read.data.imports.map { it.toChoice(timeZone) },
                            selected = read.data.selected?.id,
                            report = read.data.report,
                        )
                    }

                    is Result.Error -> {
                        state.copy(isLoading = false, error = read.error.toMessage())
                    }
                }
            }
        }
    }

    /**
     * Newest Import first. The one asked for when it is still there; otherwise the latest that finished, or
     * failing that the latest at all, since an Import that stopped early still has Verdicts over what landed.
     */
    private suspend fun choose(requested: ImportId?): Result<ReportChoice, DataError.Local> =
        imports.all().andThen { stored ->
            val newestFirst = stored.asReversed()
            val selected =
                newestFirst.firstOrNull { it.id == requested }
                    ?: newestFirst.firstOrNull { it.completedAt != null }
                    ?: newestFirst.firstOrNull()
            if (selected == null) {
                Result.Success(ReportChoice(newestFirst, null, null))
            } else {
                reader.read(selected.id).map { ReportChoice(newestFirst, selected, it.toUi()) }
            }
        }

    private fun open(ref: RecordRef) {
        viewModelScope.launch {
            val read = readRecord(ref, events, claims, timeZone)
            _state.update {
                when (read) {
                    is Result.Success -> it.copy(detail = read.data, error = null)
                    is Result.Error -> it.copy(detail = null, error = read.error.toMessage())
                }
            }
        }
    }
}
