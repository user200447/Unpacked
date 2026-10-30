package com.jassemsendi.unpacked.ui.search

import com.jassemsendi.unpacked.ui.home.RecordDetailUi
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.RecordRowUi

data class SearchState(
    /** What the user typed, as typed. */
    val text: String = "",
    /** True while the first page for [text] is being read; what is listed meanwhile is the last search. */
    val isSearching: Boolean = false,
    /** The hits read so far for [text]: newest first, undated Claims last. */
    val hits: List<RecordRowUi> = emptyList(),
    /** True while the last page read was full, so the library may hold more. */
    val hasMore: Boolean = false,
    val isReadingNextPage: Boolean = false,
    /** The Record opened from the hits, if any. */
    val detail: RecordDetailUi? = null,
    /** Why the last read failed, until the next one succeeds; what is listed is the last successful read. */
    val error: String? = null,
)

sealed interface SearchAction {
    /** The text changed; blank text clears the hits, anything else is searched for at once. */
    data class TextChanged(
        val text: String,
    ) : SearchAction

    /** Read the page after the last one listed. */
    data object NextPage : SearchAction

    data class Open(
        val ref: RecordRef,
    ) : SearchAction

    data object CloseDetail : SearchAction
}
