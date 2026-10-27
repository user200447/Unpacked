package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.ImportSummary
import com.jassemsendi.unpacked.model.Inventory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One step of an Import, emitted in the order files are visited. */
public sealed interface ImportStep {
    public data class FileStarted(
        val path: String,
        val index: Int,
        val total: Int,
    ) : ImportStep

    public data class FileFinished(
        val result: FileResult,
        val events: List<Event>,
        val claims: List<Claim>,
        val index: Int,
        val total: Int,
    ) : ImportStep

    public data class Completed(
        val summary: ImportSummary,
    ) : ImportStep
}

/**
 * One run of reading one Export. Walks every file, hands JSON files to the adapter, counts media
 * without opening it, and never aborts because one file is bad.
 */
public class Import(
    private val adapter: SourceAdapter,
) {
    public fun run(export: Export): Flow<ImportStep> =
        flow {
            val files = export.files()
            val jsonFiles = files.filter { it.isJson }
            val mediaFiles = files.count { it.isMedia }
            val results = ArrayList<FileResult>(jsonFiles.size)
            jsonFiles.forEachIndexed { index, file ->
                emit(ImportStep.FileStarted(file.path, index, jsonFiles.size))
                val outcome = parse(export, file)
                results += outcome.result
                emit(ImportStep.FileFinished(outcome.result, outcome.events, outcome.claims, index, jsonFiles.size))
            }
            val inventory =
                Inventory(
                    jsonFiles = jsonFiles.size,
                    mediaFiles = mediaFiles,
                    otherFiles = files.size - jsonFiles.size - mediaFiles,
                )
            emit(ImportStep.Completed(ImportSummary(export.id, adapter.source, results, inventory)))
        }

    private fun parse(
        export: Export,
        file: ExportFile,
    ): FileOutcome =
        runCatching { adapter.parse(file, export.readText(file), export.id) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                FileOutcome(FileResult.Failed(file.path, file.rawKind, error.describe()))
            }

    private fun Throwable.describe(): String = message ?: this::class.simpleName ?: "unknown error"
}
