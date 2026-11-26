package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.ingest.ExportLocation
import com.jassemsendi.unpacked.ingest.ImportError
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.ingest.InstagramAdapter
import com.jassemsendi.unpacked.ingest.open
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.FileSystem

/**
 * The Import use case: one run of reading the Export at a location into the library. The only way
 * the UI writes to the library; it observes the run as [ImportStep]s and ends with [ImportStep.Stored].
 */
public fun interface ImportUseCase {
    /**
     * Never throws for what can go wrong in use: a location that will not open as an Export, or a
     * library that will not take a write, ends the run with [ImportStep.Failed] naming the [ImportError].
     */
    public fun run(location: ExportLocation): Flow<ImportStep>
}

/** Imports through a [Library], reading the Export from [fileSystem] off the caller's thread. */
public class LibraryImportUseCase(
    private val library: Library,
    private val fileSystem: FileSystem,
) : ImportUseCase {
    override fun run(location: ExportLocation): Flow<ImportStep> =
        flow {
            when (val opened = Export.open(fileSystem, location)) {
                is Result.Success -> emitAll(library.import(opened.data, InstagramAdapter))
                is Result.Error -> emit(ImportStep.Failed(ImportError.Reading(opened.error)))
            }
        }.flowOn(Dispatchers.Default)
}
