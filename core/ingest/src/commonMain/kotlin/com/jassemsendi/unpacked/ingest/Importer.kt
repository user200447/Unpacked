package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.ImportSummary
import com.jassemsendi.unpacked.model.Inventory
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
    public data class FileStarted(
        val path: String,
        val progress: ImportProgress,
    ) : ImportStep

    public data class FileFinished(
        val outcome: FileOutcome,
        val progress: ImportProgress,
    ) : ImportStep

    public data class Completed(
        val summary: ImportSummary,
    ) : ImportStep
}

/**
 * Runs one Import of one Export. Walks every file, hands JSON files to the adapter, records every
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
            val results = ArrayList<FileResult>(visited.size)
            visited.forEachIndexed { index, file ->
                val progress = ImportProgress(index, visited.size)
                emit(ImportStep.FileStarted(file.path, progress))
                val outcome = if (file.isJson) parse(export, file) else notJson(file)
                results += outcome.result
                emit(ImportStep.FileFinished(outcome, progress))
            }
            val inventory =
                Inventory(
                    jsonFiles = files.count { it.isJson },
                    mediaFiles = files.size - visited.size,
                    otherFiles = visited.count { !it.isJson },
                )
            emit(ImportStep.Completed(ImportSummary(export.id, adapter.source, results, inventory)))
        }

    private fun parse(
        export: Export,
        file: ExportFile,
    ): FileOutcome =
        runCatching { adapter.parse(file, export.readText(file)) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                FileOutcome(FileResult.Failed(file.path, file.rawKind, error.describe()))
            }

    private fun notJson(file: ExportFile): FileOutcome =
        FileOutcome(FileResult.Skipped(file.path, file.rawKind, "not a JSON file"))

    private fun Throwable.describe(): String = message ?: this::class.simpleName ?: "unknown error"
}
