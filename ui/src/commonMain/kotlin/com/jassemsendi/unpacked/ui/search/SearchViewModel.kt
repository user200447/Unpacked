package com.jassemsendi.unpacked.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.data.SearchRepository
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.readRecord
import com.jassemsendi.unpacked.ui.toMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The Search screen: the Events and Claims that mention what the user typed, a page at a time, and
 * the Record the user opened from them. Every change to the text is searched for at once; a search
 * that lands after the text changed again is dropped.
 */
class SearchViewModel(
    private val search: SearchRepository,
    private val events: EventRepository,
    private val claims: ClaimRepository,
    private val timeZone: TimeZone,
    private val pageSize: Int = PAGE_SIZE,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    /** Bumped by every change of text so a page that was in flight for older text is dropped on arrival. */
    private var generation = 0

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.TextChanged -> textChanged(action.text)
            SearchAction.NextPage -> nextPage()
            is SearchAction.Open -> open(action.ref)
            SearchAction.CloseDetail -> _state.update { it.copy(detail = null) }
        }
    }

    private fun textChanged(text: String) {
        val current = ++generation
        if (text.isBlank()) {
            _state.update { SearchState(text = text, detail = it.detail) }
            return
        }
        // The last hits stay on screen until the new ones land, so the list does not flicker as the user types.
        _state.update { it.copy(text = text, isSearching = true) }
        viewModelScope.launch {
            val read = search.search(text, pageSize, 0)
            if (current != generation) return@launch
            _state.update { state ->
                when (read) {
                    is Result.Success -> {
                        state.copy(
                            isSearching = false,
                            isReadingNextPage = false,
                            error = null,
                            hits = read.data.map { it.toUi(timeZone) },
                            hasMore = read.data.size == pageSize,
                        )
                    }

                    is Result.Error -> {
                        state.copy(isSearching = false, isReadingNextPage = false, error = read.error.toMessage())
                    }
                }
            }
        }
    }

    private fun nextPage() {
        val before = _state.value
        if (before.isSearching || before.isReadingNextPage || !before.hasMore) return
        val current = generation
        _state.update { it.copy(isReadingNextPage = true) }
        viewModelScope.launch {
            val read = search.search(before.text, pageSize, before.hits.size)
            if (current != generation) return@launch
            _state.update { state ->
                when (read) {
                    is Result.Success -> {
                        // An Import landing meanwhile shifts the offsets; a hit read twice is listed once.
                        val hits = (state.hits + read.data.map { it.toUi(timeZone) }).distinctBy { it.ref }
                        state.copy(
                            isReadingNextPage = false,
                            error = null,
                            hits = hits,
                            hasMore = read.data.size == pageSize,
                        )
                    }

                    // The spinner would otherwise ask again at once; the next change of text offers the rest again.
                    is Result.Error -> {
                        state.copy(isReadingNextPage = false, hasMore = false, error = read.error.toMessage())
                    }
                }
            }
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

    companion object {
        const val PAGE_SIZE = 50
    }
}
