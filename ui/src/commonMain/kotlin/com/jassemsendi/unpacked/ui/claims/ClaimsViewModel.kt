package com.jassemsendi.unpacked.ui.claims

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.map
import com.jassemsendi.unpacked.ui.home.KindCountUi
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.RecordRowUi
import com.jassemsendi.unpacked.ui.home.readRecord
import com.jassemsendi.unpacked.ui.home.toRow
import com.jassemsendi.unpacked.ui.home.toUi
import com.jassemsendi.unpacked.ui.toMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The Claims screen: what the Source believes about the user, one group per Claim Kind with its count,
 * the Claims of the opened group in pages, and the Record the user opened from them. Nothing is read
 * until the screen asks for a [ClaimsAction.Refresh].
 */
class ClaimsViewModel(
    private val claims: ClaimRepository,
    private val events: EventRepository,
    private val timeZone: TimeZone,
    private val pageSize: Int = PAGE_SIZE,
) : ViewModel() {
    private val _state = MutableStateFlow(ClaimsState())
    val state = _state.asStateFlow()

    /** Bumped by every reload so a page that was in flight for an older group is dropped on arrival. */
    private var generation = 0

    /** The Kind counts and the opened group's first page, read together so the screen never shows one without the other. */
    private data class FirstPage(
        val kinds: List<KindCountUi<ClaimKind>>,
        val expanded: ClaimKind?,
        val claims: List<RecordRowUi>,
    )

    fun onAction(action: ClaimsAction) {
        when (action) {
            ClaimsAction.Refresh -> reload(_state.value.expanded)
            is ClaimsAction.Expand -> reload(action.kind)
            ClaimsAction.NextPage -> nextPage()
            is ClaimsAction.Open -> open(action.ref)
            ClaimsAction.CloseDetail -> _state.update { it.copy(detail = null) }
        }
    }

    private fun reload(requested: ClaimKind?) {
        val current = ++generation
        // Another group empties the list at once; a plain refresh keeps the last read on screen until the new one lands.
        _state.update {
            if (requested == it.expanded) {
                it.copy(isLoading = true)
            } else {
                it.copy(isLoading = true, expanded = requested, claims = emptyList(), hasMore = false)
            }
        }
        viewModelScope.launch {
            val read = readFirstPage(requested)
            if (current != generation) return@launch
            _state.update { state ->
                when (read) {
                    is Result.Success -> {
                        val page = read.data
                        state
                            .copy(
                                isLoading = false,
                                error = null,
                                expanded = page.expanded,
                                totalCount = page.kinds.sumOf { it.count },
                                kinds = page.kinds,
                                claims = page.claims,
                            ).withHasMore()
                    }

                    is Result.Error -> {
                        state.copy(isLoading = false, error = read.error.toMessage())
                    }
                }
            }
        }
    }

    private suspend fun readFirstPage(requested: ClaimKind?): Result<FirstPage, DataError.Local> =
        when (val counted = claims.countByKind()) {
            is Result.Error -> {
                counted
            }

            is Result.Success -> {
                val counts = counted.data
                val kinds = ClaimKind.entries.mapNotNull { kind -> counts[kind]?.let { kind.toUi(it) } }
                // A group the library no longer holds, say after an Import was deleted, is no group to keep open.
                val expanded = requested?.takeIf { it in counts }
                if (expanded == null) {
                    Result.Success(FirstPage(kinds, null, emptyList()))
                } else {
                    claims.byKind(expanded, pageSize, 0).map { page ->
                        FirstPage(kinds, expanded, page.map { it.toRow(timeZone) })
                    }
                }
            }
        }

    private fun nextPage() {
        val before = _state.value
        val expanded = before.expanded?.takeIf { before.hasMore } ?: return
        if (before.isLoading || before.isReadingNextPage) return
        val current = generation
        _state.update { it.copy(isReadingNextPage = true) }
        viewModelScope.launch {
            val read = claims.byKind(expanded, pageSize, before.claims.size)
            if (current != generation) return@launch
            _state.update { state ->
                when (read) {
                    is Result.Success -> {
                        // An Import landing meanwhile shifts the offsets; a Claim read twice is listed once.
                        val rows = (state.claims + read.data.map { it.toRow(timeZone) }).distinctBy { it.ref }
                        state.copy(isReadingNextPage = false, error = null, claims = rows).withHasMore()
                    }

                    // The spinner would otherwise ask again at once; the next Refresh offers the rest again.
                    is Result.Error -> {
                        state.copy(isReadingNextPage = false, hasMore = false, error = read.error.toMessage())
                    }
                }
            }
        }
    }

    /** More to read while the repository counts more Claims in the opened group than are listed. */
    private fun ClaimsState.withHasMore(): ClaimsState {
        val group = kinds.firstOrNull { it.kind == expanded } ?: return copy(hasMore = false)
        return copy(hasMore = claims.size < group.count)
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

    companion object {
        const val PAGE_SIZE = 50
    }
}
