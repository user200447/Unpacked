package com.jassemsendi.unpacked.ui.explorer

import com.jassemsendi.unpacked.ingest.ExplorerError
import com.jassemsendi.unpacked.ingest.ExportFileReader
import com.jassemsendi.unpacked.ingest.FileRecords
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source

/**
 * The files of one Export in memory, by path; a test that wants the Export to be gone sets [gone]. Records every
 * location it was asked to read from, so a test can see the Import's remembered place being used.
 */
class FakeExportFileReader(
    private val files: Map<String, FileRecords>,
) : ExportFileReader {
    var gone: ExplorerError.ExportGone? = null
    val readFrom = mutableListOf<ExportLocation>()

    override suspend fun read(
        location: ExportLocation,
        source: Source,
        path: String,
    ): Result<FileRecords, ExplorerError> {
        readFrom += location
        gone?.let { return Result.Error(it) }
        return files[path]?.let { Result.Success(it) } ?: Result.Error(ExplorerError.FileGone)
    }
}
