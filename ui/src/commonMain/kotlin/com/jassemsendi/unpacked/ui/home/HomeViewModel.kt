package com.jassemsendi.unpacked.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.map
import com.jassemsendi.unpacked.ui.toMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The Home timeline: the library's Events newest first in pages, narrowed to one Kind on request,
 * and the record the user opened. Nothing is read until the screen asks for a [HomeAction.Refresh].
 */
class HomeViewModel(
    private val events: EventRepository,
    private val claims: ClaimRepository,
    private val timeZone: TimeZone,
    private val pageSize: Int = PAGE_SIZE,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    /** Bumped by every reload so a page that was in flight for an older filter is dropped on arrival. */
    private var generation = 0

    /** The Kind counts and the first page, read together so the screen never shows one without the other. */
    private data class FirstPage(
        val kinds: List<KindCountUi>,
        val filter: EventKind?,
        val entries: List<TimelineEntryUi>,
    )

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> reload(_state.value.filter)
            is HomeAction.Filter -> reload(action.kind)
            HomeAction.NextPage -> nextPage()
            is HomeAction.Open -> open(action.ref)
            HomeAction.CloseDetail -> _state.update { it.copy(detail = null) }
        }
    }

    private fun reload(requested: EventKind?) {
        val current = ++generation
        // A new Kind empties the list at once; a plain refresh keeps the last read on screen until the new one lands.
        _state.update {
            if (requested == it.filter) {
                it.copy(isLoading = true)
            } else {
                it.copy(isLoading = true, filter = requested, entries = emptyList(), hasMore = false)
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
                                filter = page.filter,
                                totalCount = page.kinds.sumOf { it.count },
                                kinds = page.kinds,
                                entries = page.entries,
                            ).withHasMore()
                    }

                    is Result.Error -> {
                        state.copy(isLoading = false, error = read.error.toMessage())
                    }
                }
            }
        }
    }

    private suspend fun readFirstPage(requested: EventKind?): Result<FirstPage, DataError.Local> =
        when (val counted = events.countByKind()) {
            is Result.Error -> {
                counted
            }

            is Result.Success -> {
                val counts = counted.data
                val kinds = EventKind.entries.mapNotNull { kind -> counts[kind]?.let { kind.toUi(it) } }
                // A Kind the library no longer holds, say after an Import was deleted, is no filter to keep.
                val filter = requested?.takeIf { it in counts }
                events.newestFirst(filter, pageSize, 0).map { page ->
                    FirstPage(kinds, filter, page.map { it.toEntry(timeZone) })
                }
            }
        }

    private fun nextPage() {
        val before = _state.value
        if (before.isLoading || before.isReadingNextPage || !before.hasMore) return
        val current = generation
        _state.update { it.copy(isReadingNextPage = true) }
        viewModelScope.launch {
            val read = events.newestFirst(before.filter, pageSize, before.entries.size)
            if (current != generation) return@launch
            _state.update { state ->
                when (read) {
                    is Result.Success -> {
                        // An Import landing meanwhile shifts the offsets; an entry read twice is listed once.
                        val entries = (state.entries + read.data.map { it.toEntry(timeZone) }).distinctBy { it.ref }
                        state.copy(isReadingNextPage = false, error = null, entries = entries).withHasMore()
                    }

                    // The spinner would otherwise ask again at once; the next Refresh offers the rest again.
                    is Result.Error -> {
                        state.copy(isReadingNextPage = false, hasMore = false, error = read.error.toMessage())
                    }
                }
            }
        }
    }

    /** More to read while the repository counts more Events for the filter than are listed. */
    private fun HomeState.withHasMore(): HomeState {
        val visible = if (filter == null) totalCount else kinds.first { it.kind == filter }.count
        return copy(hasMore = entries.size < visible)
    }

    private fun open(ref: RecordRef) {
        viewModelScope.launch {
            val read =
                when (ref) {
                    is RecordRef.Event -> events.byProvenance(ref.provenance).map { it.toDetail(timeZone) }
                    is RecordRef.Claim -> claims.byProvenance(ref.provenance).map { it.toDetail(timeZone) }
                }
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
