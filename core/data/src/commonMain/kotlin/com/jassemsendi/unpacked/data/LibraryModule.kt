package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.ingest.ExportFileReader
import com.jassemsendi.unpacked.ingest.FileSystemExportFileReader
import okio.FileSystem
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Binds one open [library] to the interfaces the rest of the app reads it through, plus the Import
 * use case that writes to it and the reader the Explorer opens an Import's Export files with, both
 * over the same [fileSystem]. Each app opens its library at a platform path and passes it here, and
 * may wrap the Import use case in [hostImport] with what its platform does around a long run: Android
 * hosts it in a foreground service (ADR 0008); the others run it as it is.
 */
public fun libraryModule(
    library: Library,
    fileSystem: FileSystem,
    hostImport: (ImportUseCase) -> ImportUseCase = { it },
): Module =
    module {
        single<ImportRepository> { library.imports }
        single<EventRepository> { library.events }
        single<ClaimRepository> { library.claims }
        single<EntityRepository> { library.entities }
        single<SearchRepository> { library.search }
        single<ImportUseCase> { hostImport(LibraryImportUseCase(library, fileSystem)) }
        single<ExportFileReader> { FileSystemExportFileReader(fileSystem) }
    }
