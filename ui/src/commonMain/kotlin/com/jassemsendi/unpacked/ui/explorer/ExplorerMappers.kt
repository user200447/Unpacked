package com.jassemsendi.unpacked.ui.explorer

import com.jassemsendi.unpacked.ingest.ExportError
import com.jassemsendi.unpacked.ingest.FileRecords
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.Source
import com.jassemsendi.unpacked.ui.home.AttributeUi
import com.jassemsendi.unpacked.ui.imports.toUi

/** Every file the Import gave a result, in its order, plus the media it only counted. */
fun Import.toInventory(): InventoryUi =
    InventoryUi(
        exportName = summary.exportId.value,
        sourceName = summary.source.name,
        location = location.describe(),
        files = summary.files.map { it.toExplorerUi() },
        mediaFiles = summary.inventory.mediaFiles,
    )

/** The result as the Import screen words it, and whether the Explorer can read the file: only JSON opens as Records. */
fun FileResult.toExplorerUi(): ExplorerFileUi =
    ExplorerFileUi(
        path = path,
        rawKind = rawKind.value,
        outcome =
            when (this) {
                is FileResult.Parsed -> "Parsed"
                is FileResult.Skipped -> "Skipped"
                is FileResult.Failed -> "Failed"
            },
        detail = toUi().detail,
        canOpen = path.endsWith(JSON_SUFFIX, ignoreCase = true),
    )

/**
 * The Records read now, under a note when the [source] could not read the file as the Import does: that what is shown
 * is its plain JSON, or that nothing in it could be read at all.
 */
fun FileRecords.toOpened(
    file: ExplorerFileUi,
    source: Source,
): OpenedFileUi =
    OpenedFileUi(
        file = file,
        records =
            records.map { record ->
                RecordUi(record.index, record.fields.map { (label, value) -> AttributeUi(label, value) })
            },
        note =
            when {
                sourceFailure == null -> {
                    null
                }

                records.isEmpty() -> {
                    "Nothing in this file could be read as records: $sourceFailure."
                }

                else -> {
                    "${source.name} could not read this file as it does during an Import ($sourceFailure), " +
                        "so its JSON is shown as written."
                }
            },
    )

/** Where the Import remembers the Export, as a phrase: "the zip at …" or "the folder at …". */
fun ExportLocation.describe(): String =
    when (this) {
        is ExportLocation.Zip -> "the zip at $path"
        is ExportLocation.Folder -> "the folder at $path"
    }

/** Why the Export at [location] could not be opened, and that picking it again is the way back to its files. */
fun ExportError.toExportGone(
    location: ExportLocation,
    canPickAgain: Boolean,
): ExportGoneUi =
    ExportGoneUi(
        message =
            when (this) {
                ExportError.NOT_FOUND -> {
                    "The Export is no longer at ${location.path}. $PICK_AGAIN"
                }

                ExportError.NOT_READABLE -> {
                    "What is at ${location.path} is no longer an Export that can be read. $PICK_AGAIN"
                }
            },
        canPickAgain = canPickAgain,
    )

private const val JSON_SUFFIX = ".json"
private const val PICK_AGAIN = "Pick it again to open its files."
