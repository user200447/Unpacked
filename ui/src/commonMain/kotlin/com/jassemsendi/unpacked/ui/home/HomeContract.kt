package com.jassemsendi.unpacked.ui.home

import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Provenance

data class HomeState(
    /** True until the first page and the Kind counts have been read; the list is not yet meaningful. */
    val isLoading: Boolean = true,
    /** Events in the whole library, before any filter. */
    val totalCount: Int = 0,
    /** Every Kind with at least one Event, in Kind order, each with its count from the repository. */
    val kinds: List<KindCountUi> = emptyList(),
    /** The Kind the timeline is narrowed to, or null for every Kind. */
    val filter: EventKind? = null,
    /** The Events read so far, newest first. */
    val entries: List<TimelineEntryUi> = emptyList(),
    /** True while the repository holds Events beyond [entries] for the current filter. */
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** The record opened from the timeline, if any. */
    val detail: RecordDetailUi? = null,
)

data class KindCountUi(
    val kind: EventKind,
    val label: String,
    val count: Int,
)

/** One Event as the timeline lists it. */
data class TimelineEntryUi(
    val ref: RecordRef.Event,
    val kindLabel: String,
    /** When it happened, in the device's time zone, to the minute. */
    val time: String,
    /** What it concerned: the Entities it names, or failing that its first recorded value. */
    val summary: String,
    val rawKind: String,
)

/** Which stored record to open: an Event or a Claim, told apart because each has its own repository. */
sealed interface RecordRef {
    val provenance: Provenance

    data class Event(
        override val provenance: Provenance,
    ) : RecordRef

    data class Claim(
        override val provenance: Provenance,
    ) : RecordRef
}

/** Exactly what was recorded and where it came from: the record's own label and value pairs plus its Provenance. */
data class RecordDetailUi(
    val ref: RecordRef,
    val kindLabel: String,
    val time: RecordTimeUi,
    val rawKind: String,
    /** In the order the record listed them. */
    val attributes: List<AttributeUi>,
    /** The display names of the Entities the record refers to, in record order. */
    val entities: List<String>,
    val exportName: String,
    val path: String,
    val recordIndex: Int,
    /** Which item of a record that lists several things; 0 for a record that is one thing. */
    val itemIndex: Int,
)

/** How a record's time reads: an Event happened at a moment; a Claim was at most last updated at one. */
sealed interface RecordTimeUi {
    data class Happened(
        val at: String,
    ) : RecordTimeUi

    data class LastUpdated(
        val at: String,
    ) : RecordTimeUi

    data object Undated : RecordTimeUi
}

data class AttributeUi(
    val label: String,
    val value: String,
)

sealed interface HomeAction {
    /** Re-read the counts and the first page; the screen sends it whenever it appears, since Imports may have changed. */
    data object Refresh : HomeAction

    data class Filter(
        val kind: EventKind?,
    ) : HomeAction

    data object LoadMore : HomeAction

    data class Open(
        val ref: RecordRef,
    ) : HomeAction

    data object CloseDetail : HomeAction
}
