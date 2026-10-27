package com.jassemsendi.unpacked.ui.imports

import com.jassemsendi.unpacked.data.ImportRepository
import com.jassemsendi.unpacked.data.ImportUseCase
import com.jassemsendi.unpacked.ingest.ExportLocation
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EmptyResult
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.ImportSummary
import com.jassemsendi.unpacked.model.Inventory
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The stored Imports in memory; a test that wants the library to refuse sets [failure]. */
class FakeImportRepository(
    vararg initial: Import,
) : ImportRepository {
    private val imports = initial.toMutableList()
    var failure: DataError.Local? = null

    override suspend fun all(): Result<List<Import>, DataError.Local> =
        failure?.let { Result.Error(it) } ?: Result.Success(imports.toList())

    override suspend fun get(id: ImportId): Result<Import, DataError.Local> =
        failure?.let { Result.Error(it) }
            ?: imports.find { it.id == id }?.let { Result.Success(it) }
            ?: Result.Error(DataError.Local.NOT_FOUND)

    override suspend fun delete(id: ImportId): EmptyResult<DataError.Local> {
        failure?.let { return Result.Error(it) }
        imports.removeAll { it.id == id }
        return Result.Success(Unit)
    }

    fun add(import: Import) {
        imports += import
    }
}

/**
 * A scripted Import: the test pushes steps through [steps] as if the library were emitting them,
 * and whatever is Stored lands in the [repository] the way a real run would.
 */
class FakeImportUseCase(
    private val repository: FakeImportRepository,
) : ImportUseCase {
    val steps = Channel<ImportStep>(Channel.UNLIMITED)
    val runs = mutableListOf<ExportLocation>()

    override fun run(location: ExportLocation): Flow<ImportStep> {
        runs += location
        return steps.receiveAsFlow().onEach { if (it is ImportStep.Stored) repository.add(it.import) }
    }
}

class FakeExportPicker(
    var zip: ExportLocation? = null,
    var folder: ExportLocation? = null,
    override val canPickZip: Boolean = true,
    override val canPickFolder: Boolean = true,
) : ExportPicker {
    override suspend fun pickZip(): ExportLocation? = zip

    override suspend fun pickFolder(): ExportLocation? = folder
}

const val FIRST_START_MILLIS = 1_793_095_920_000L // 2026-10-27T10:12:00Z

fun storedImport(
    id: Long,
    exportName: String,
    files: List<FileResult> = emptyList(),
    complete: Boolean = true,
): Import {
    val startedAt = Instant.fromEpochMilliseconds(FIRST_START_MILLIS) + (id - 1).toInt().minutes
    return Import(
        id = ImportId(id),
        startedAt = startedAt,
        completedAt = if (complete) startedAt + 1.minutes else null,
        summary = ImportSummary(ExportId(exportName), Source.Instagram, files, Inventory(files.size, 0, 0)),
    )
}
