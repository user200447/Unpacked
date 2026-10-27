package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.ingest.ExportLocation
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.ingest.InstagramAdapter
import com.jassemsendi.unpacked.ingest.open
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.FileSystem

/**
 * The Import use case: one run of reading the Export at a location into the library. The only way
 * the UI writes to the library; it observes the run as [ImportStep]s and ends with [ImportStep.Stored].
 */
public fun interface ImportUseCase {
    /** Fails, rather than emitting, when the location cannot be opened as an Export at all. */
    public fun run(location: ExportLocation): Flow<ImportStep>
}

/** Imports through a [Library], reading the Export from [fileSystem] off the caller's thread. */
public class LibraryImportUseCase(
    private val library: Library,
    private val fileSystem: FileSystem,
) : ImportUseCase {
    override fun run(location: ExportLocation): Flow<ImportStep> =
        flow {
            val export = Export.open(fileSystem, location)
            library.import(export, InstagramAdapter).collect { emit(it) }
        }.flowOn(Dispatchers.Default)
}
