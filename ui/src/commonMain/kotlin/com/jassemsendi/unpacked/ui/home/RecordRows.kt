package com.jassemsendi.unpacked.ui.home

import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.ui.time.toDeviceMinute
import kotlinx.datetime.TimeZone

// How an Event or Claim reads in any list but the timeline: the Search hits, a Claim group, an Entity's own lists.

fun Event.toRow(timeZone: TimeZone): RecordRowUi =
    RecordRowUi(
        ref = RecordRef.Event(provenance),
        kindLabel = kind.label(),
        time = RecordTimeUi.Happened(time.toDeviceMinute(timeZone)),
        summary = summaryOf(entities, attributes),
    )

fun Claim.toRow(timeZone: TimeZone): RecordRowUi =
    RecordRowUi(
        ref = RecordRef.Claim(provenance),
        kindLabel = kind.label(),
        time = asOf?.let { RecordTimeUi.LastUpdated(it.toDeviceMinute(timeZone)) } ?: RecordTimeUi.Undated,
        summary = summaryOf(entities, attributes),
    )

/** How a Record's time reads on screen, whichever screen shows it. */
fun RecordTimeUi.text(): String =
    when (this) {
        is RecordTimeUi.Happened -> "Happened $at"
        is RecordTimeUi.LastUpdated -> "Last updated $at"
        RecordTimeUi.Undated -> "No time recorded"
    }

/** Where a Record came from, in one line: its file, its record and, for one of several items, which. */
fun Provenance.text(): String = "$path, record $recordIndex" + if (itemIndex > 0) ", item $itemIndex" else ""
