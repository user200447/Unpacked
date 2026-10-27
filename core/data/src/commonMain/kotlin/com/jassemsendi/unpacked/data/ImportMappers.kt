package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.data.db.ImportFileRow
import com.jassemsendi.unpacked.data.db.ImportRow
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.ImportSummary
import com.jassemsendi.unpacked.model.Inventory
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Source
import kotlin.time.Instant

// Both directions between the canonical model and Room rows (ADR 0005).

internal fun Instant.toEpochMillis(): Long = toEpochMilliseconds()

internal fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

internal fun sourceOf(id: String): Source = Source.entries.first { it.id == id }

internal fun FileResult.toRow(
    importId: Long,
    position: Int,
): ImportFileRow =
    when (this) {
        is FileResult.Parsed -> {
            ImportFileRow(
                importId = importId,
                path = path,
                rawKind = rawKind.value,
                position = position,
                status = ImportFileRow.PARSED,
                recordCount = recordCount,
                eventCount = eventCount,
                claimCount = claimCount,
                droppedCount = droppedCount,
                reason = null,
            )
        }

        is FileResult.Skipped -> {
            notParsed(importId, position, ImportFileRow.SKIPPED, reason)
        }

        is FileResult.Failed -> {
            notParsed(importId, position, ImportFileRow.FAILED, reason)
        }
    }

private fun FileResult.notParsed(
    importId: Long,
    position: Int,
    status: String,
    reason: String,
): ImportFileRow =
    ImportFileRow(
        importId = importId,
        path = path,
        rawKind = rawKind.value,
        position = position,
        status = status,
        recordCount = 0,
        eventCount = 0,
        claimCount = 0,
        droppedCount = 0,
        reason = reason,
    )

internal fun ImportFileRow.toModel(): FileResult =
    when (status) {
        ImportFileRow.PARSED -> {
            FileResult.Parsed(path, RawKind(rawKind), recordCount, eventCount, claimCount, droppedCount)
        }

        ImportFileRow.SKIPPED -> {
            FileResult.Skipped(path, RawKind(rawKind), reason.orEmpty())
        }

        ImportFileRow.FAILED -> {
            FileResult.Failed(path, RawKind(rawKind), reason.orEmpty())
        }

        else -> {
            error("unknown import file status '$status'")
        }
    }

internal fun ImportRow.toModel(files: List<FileResult>): Import =
    Import(
        id = ImportId(id),
        startedAt = startedAt.toInstant(),
        completedAt = completedAt?.toInstant(),
        summary =
            ImportSummary(
                exportId = ExportId(exportId),
                source = sourceOf(source),
                files = files,
                inventory = Inventory(jsonFiles = jsonFiles, mediaFiles = mediaFiles, otherFiles = otherFiles),
            ),
    )
