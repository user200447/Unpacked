package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.Error
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem

/**
 * One entry of an Export file as the Source wrote it (glossary: Record): its position in the file and its label
 * and value pairs in the file's order, with no Kind assigned. What the Explorer shows of a file, whether or not
 * the Import made anything of it.
 */
public data class Record(
    val index: Int,
    val fields: Map<String, String>,
)

/**
 * The Records of one Export file, read on demand. [sourceFailure] is null when the Source read the file in its own
 * dialects, as the Import does; otherwise it is why the Source could not, and [records] are what a plain reading
 * of the JSON made of it, every object one Record: empty when the text is not JSON at all.
 */
public data class FileRecords(
    val records: List<Record>,
    val sourceFailure: String? = null,
)

/** Why the Explorer could not read a file from an Import's Export. */
public sealed interface ExplorerError : Error {
    /** The Export is not where the Import read it: moved or deleted since, or no longer something that opens as one. */
    public data class ExportGone(
        val error: ExportError,
    ) : ExplorerError

    /** The Export opens, but this file is no longer in it, or will not read. */
    public data object FileGone : ExplorerError
}

/** Reads one file of a stored Import's Export as its Records, from wherever the Import remembers the Export being. */
public fun interface ExportFileReader {
    public suspend fun read(
        location: ExportLocation,
        source: Source,
        path: String,
    ): Result<FileRecords, ExplorerError>
}

/** Reads through [fileSystem], off the caller's thread, with the adapter of the Import's Source. */
public class FileSystemExportFileReader(
    private val fileSystem: FileSystem,
) : ExportFileReader {
    override suspend fun read(
        location: ExportLocation,
        source: Source,
        path: String,
    ): Result<FileRecords, ExplorerError> =
        withContext(Dispatchers.IO) {
            when (val opened = Export.open(fileSystem, location)) {
                is Result.Error -> {
                    Result.Error(ExplorerError.ExportGone(opened.error))
                }

                is Result.Success -> {
                    val file = ExportFile(opened.data.id, path)
                    when (val text = exportCall { opened.data.readText(file) }) {
                        is Result.Error -> Result.Error(ExplorerError.FileGone)
                        is Result.Success -> Result.Success(SourceAdapter.of(source).records(file, text.data))
                    }
                }
            }
        }
}
