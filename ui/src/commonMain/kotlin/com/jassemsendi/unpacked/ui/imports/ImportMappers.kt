package com.jassemsendi.unpacked.ui.imports

import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.ui.time.toDeviceMinute
import kotlinx.datetime.TimeZone

fun Import.toUi(timeZone: TimeZone): ImportUi =
    ImportUi(
        id = id,
        exportName = summary.exportId.value,
        sourceName = summary.source.name,
        startedAt = startedAt.toDeviceMinute(timeZone),
        isComplete = completedAt != null,
        eventCount = summary.eventCount,
        claimCount = summary.claimCount,
        parsed = summary.parsed.map { it.toUi() },
        skipped = summary.skipped.map { it.toUi() },
        failed = summary.failed.map { it.toUi() },
    )

fun FileResult.toUi(): FileResultUi =
    FileResultUi(
        path = path,
        detail =
            when (this) {
                is FileResult.Parsed -> describeCounts()
                is FileResult.Skipped -> reason
                is FileResult.Failed -> reason
            },
    )

private fun FileResult.Parsed.describeCounts(): String {
    val counts = "$recordCount records: $eventCount Events, $claimCount Claims"
    if (droppedCount == 0) return counts
    val dropped = "$counts, $droppedCount dropped"
    return if (droppedReason == null) dropped else "$dropped: $droppedReason"
}
