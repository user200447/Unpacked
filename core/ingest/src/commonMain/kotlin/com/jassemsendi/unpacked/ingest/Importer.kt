package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportSummary
import com.jassemsendi.unpacked.model.Inventory
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Position of one file in the Import: the [index]th of [total] files that get a result. */
public data class ImportProgress(
    val index: Int,
    val total: Int,
)

/** One step of an Import, emitted in the order files are visited. */
public sealed interface ImportStep {
    /** The first step: the Export has been listed and its inventory is known before any file is read. */
    public data class Started(
        val exportId: ExportId,
        val source: Source,
        val inventory: Inventory,
    ) : ImportStep

    public data class FileStarted(
        val path: String,
        val progress: ImportProgress,
    ) : ImportStep

    public data class FileFinished(
        val outcome: FileOutcome,
        val progress: ImportProgress,
    ) : ImportStep

    /** The last step of a run that only reads: the summary of every file visited. */
    public data class Completed(
        val summary: ImportSummary,
    ) : ImportStep

    /** The last step of a run that writes into a library, in place of [Completed]: the Import as stored. */
    public data class Stored(
        val import: Import,
    ) : ImportStep
}

/**
 * Runs one Import of one Export. Walks every file, hands JSON files to the adapter's reader, records every
 * other non-media file as Skipped, counts media without opening it, and never aborts because one
 * file is bad.
 */
public class Importer(
    private val adapter: SourceAdapter,
) {
    public fun run(export: Export): Flow<ImportStep> =
        flow {
            val files = export.files()
            val visited = files.filterNot { it.isMedia }
            val inventory =
                Inventory(
                    jsonFiles = files.count { it.isJson },
                    mediaFiles = files.size - visited.size,
                    otherFiles = visited.count { !it.isJson },
                )
            emit(ImportStep.Started(export.id, adapter.source, inventory))
            val reader = adapter.begin(export)
            val results = ArrayList<FileResult>(visited.size)
            visited.forEachIndexed { index, file ->
                val progress = ImportProgress(index, visited.size)
                emit(ImportStep.FileStarted(file.path, progress))
                val outcome = if (file.isJson) parse(reader, export, file) else notJson(file)
                results += outcome.result
                emit(ImportStep.FileFinished(outcome, progress))
            }
            emit(ImportStep.Completed(ImportSummary(export.id, adapter.source, results, inventory)))
        }

    private fun parse(
        reader: ExportReader,
        export: Export,
        file: ExportFile,
    ): FileOutcome =
        runCatching { reader.parse(file, export.readText(file)) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                FileOutcome(FileResult.Failed(file.path, file.rawKind, error.describe()))
            }

    private fun notJson(file: ExportFile): FileOutcome =
        FileOutcome(FileResult.Skipped(file.path, file.rawKind, "not a JSON file"))

    private fun Throwable.describe(): String = message ?: this::class.simpleName ?: "unknown error"
}
