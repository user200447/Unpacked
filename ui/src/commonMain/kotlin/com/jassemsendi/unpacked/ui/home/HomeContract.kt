package com.jassemsendi.unpacked.ui.home

import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Provenance

data class HomeState(
    /**
     * True from the start until the Kind counts and the first page have been read, and again during a
     * refresh; what is listed meanwhile is the last read.
     */
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
    val isReadingNextPage: Boolean = false,
    /** The Record opened from the timeline, if any. */
    val detail: RecordDetailUi? = null,
    /** Why the last read failed, until the next one succeeds; what is listed is the last successful read. */
    val error: String? = null,
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

/** The four lines a list shows of one Event or Claim: its time, its Kind, what it concerned, and where it came from. */
internal data class RecordCardLines(
    val time: String,
    val kindLabel: String,
    val summary: String,
    val footnote: String,
)

/** Whose Record to open: an Event's or a Claim's, told apart because each has its own repository. */
sealed interface RecordRef {
    val provenance: Provenance

    data class Event(
        override val provenance: Provenance,
    ) : RecordRef

    data class Claim(
        override val provenance: Provenance,
    ) : RecordRef
}

/** A Record as stored and where it came from: its label and value pairs, plus the Provenance carried by [ref]. */
data class RecordDetailUi(
    val ref: RecordRef,
    val kindLabel: String,
    val time: RecordTimeUi,
    val rawKind: String,
    /** In the order the Record listed them. */
    val attributes: List<AttributeUi>,
    /** The display names of the Entities the Record refers to, in Record order. */
    val entities: List<String>,
)

/** How a Record's time reads: an Event happened at a moment; a Claim was at most last updated at one. */
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

    /** Read the page after the last one listed. */
    data object NextPage : HomeAction

    data class Open(
        val ref: RecordRef,
    ) : HomeAction

    data object CloseDetail : HomeAction
}
