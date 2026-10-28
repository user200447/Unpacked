package com.jassemsendi.unpacked.data

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jassemsendi.unpacked.data.db.LibraryDatabase
import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.ingest.ImportError
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.ingest.Importer
import com.jassemsendi.unpacked.ingest.SourceAdapter
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.map
import com.jassemsendi.unpacked.model.onSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlin.time.Clock

/**
 * The user's local library: every Import, held on the device and read through the repository
 * interfaces, written only by [import]. Each platform source set adds the `open` and `inMemory`
 * factories, because Room on Android needs a `Context` to build a database and the others do not.
 */
public class Library internal constructor(
    private val db: LibraryDatabase,
    private val clock: Clock,
) : AutoCloseable {
    public val imports: ImportRepository = RoomImportRepository(db)
    public val events: EventRepository = RoomEventRepository(db)
    public val claims: ClaimRepository = RoomClaimRepository(db)
    public val entities: EntityRepository = RoomEntityRepository(db)
    public val search: SearchRepository = RoomSearchRepository(db)

    /**
     * Runs one Import of [export] through [adapter] and writes it into the library file by file.
     * Emits the Importer's steps as they happen and ends with [ImportStep.Stored] in place of
     * [ImportStep.Completed]. A write the library refuses ends the run with [ImportStep.Failed] instead,
     * and the Import stays with no completion time, as it does when the flow is cancelled.
     */
    public fun import(
        export: Export,
        adapter: SourceAdapter,
    ): Flow<ImportStep> =
        flow {
            var write: ImportWrite? = null
            val steps =
                Importer(adapter).run(export).transformWhile { step ->
                    val written: Result<ImportStep, DataError.Local> =
                        when (step) {
                            is ImportStep.Started -> {
                                localCall { ImportWrite.begin(db, step, clock.now()) }
                                    .onSuccess { write = it }
                                    .map { step }
                            }

                            is ImportStep.FileStarted, is ImportStep.Failed -> {
                                Result.Success(step)
                            }

                            is ImportStep.FileFinished -> {
                                localCall { checkNotNull(write).writeFile(step.outcome) }.map { step }
                            }

                            is ImportStep.Completed -> {
                                stored(checkNotNull(write))
                            }

                            is ImportStep.Stored -> {
                                error("an Importer never emits Stored")
                            }
                        }
                    when (written) {
                        is Result.Success -> {
                            emit(written.data)
                            written.data !is ImportStep.Failed
                        }

                        is Result.Error -> {
                            emit(ImportStep.Failed(ImportError.Writing(written.error)))
                            false
                        }
                    }
                }
            emitAll(steps)
        }

    /** Marks the Import complete and reads it back as the run's last step. */
    private suspend fun stored(write: ImportWrite): Result<ImportStep, DataError.Local> =
        when (val completed = localCall { write.complete(clock.now()) }) {
            is Result.Error -> completed
            is Result.Success -> imports.get(write.id).map { ImportStep.Stored(it) }
        }

    override fun close(): Unit = db.close()

    public companion object {
        internal fun open(
            builder: RoomDatabase.Builder<LibraryDatabase>,
            clock: Clock,
        ): Library =
            Library(
                builder
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    // No shipped build exists to migrate from yet; revisit before the first release.
                    .fallbackToDestructiveMigration(true)
                    .build(),
                clock,
            )
    }
}
