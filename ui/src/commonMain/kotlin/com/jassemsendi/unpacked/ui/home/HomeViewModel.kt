package com.jassemsendi.unpacked.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.model.EventKind
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

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> reload(_state.value.filter)
            is HomeAction.Filter -> reload(action.kind)
            HomeAction.LoadMore -> loadMore()
            is HomeAction.Open -> open(action.ref)
            HomeAction.CloseDetail -> _state.update { it.copy(detail = null) }
        }
    }

    private fun reload(requested: EventKind?) {
        val current = ++generation
        _state.update { it.copy(isLoading = true, filter = requested, entries = emptyList(), hasMore = false) }
        viewModelScope.launch {
            val counts = events.countByKind()
            val kinds = EventKind.entries.mapNotNull { kind -> counts[kind]?.let { kind.toUi(it) } }
            // A Kind the library no longer holds, say after an Import was deleted, is no filter to keep.
            val filter = requested?.takeIf { it in counts }
            val total = kinds.sumOf { it.count }
            val visible = if (filter == null) total else counts.getValue(filter)
            val page = events.newestFirst(filter, pageSize, 0).map { it.toEntry(timeZone) }
            if (current != generation) return@launch
            _state.update {
                it.copy(
                    isLoading = false,
                    filter = filter,
                    totalCount = total,
                    kinds = kinds,
                    entries = page,
                    hasMore = page.size < visible,
                )
            }
        }
    }

    private fun loadMore() {
        val before = _state.value
        if (before.isLoading || before.isLoadingMore || !before.hasMore) return
        val current = generation
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val page = events.newestFirst(before.filter, pageSize, before.entries.size).map { it.toEntry(timeZone) }
            if (current != generation) return@launch
            _state.update {
                it.copy(isLoadingMore = false, entries = it.entries + page, hasMore = page.size == pageSize)
            }
        }
    }

    private fun open(ref: RecordRef) {
        viewModelScope.launch {
            val detail =
                when (ref) {
                    is RecordRef.Event -> events.byProvenance(ref.provenance)?.toDetail(timeZone)
                    is RecordRef.Claim -> claims.byProvenance(ref.provenance)?.toDetail(timeZone)
                }
            _state.update { it.copy(detail = detail) }
        }
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}
