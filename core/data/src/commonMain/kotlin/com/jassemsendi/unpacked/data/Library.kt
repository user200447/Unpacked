package com.jassemsendi.unpacked.data

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jassemsendi.unpacked.data.db.LibraryDatabase
import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.ingest.Importer
import com.jassemsendi.unpacked.ingest.SourceAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    /**
     * Runs one Import of [export] through [adapter] and writes it into the library file by file.
     * Emits the Importer's steps as they happen and ends with [ImportStep.Stored] in place of
     * [ImportStep.Completed]. If the flow fails or is cancelled, the Import stays with no completion time.
     */
    public fun import(
        export: Export,
        adapter: SourceAdapter,
    ): Flow<ImportStep> =
        flow {
            var write: ImportWrite? = null
            Importer(adapter).run(export).collect { step ->
                when (step) {
                    is ImportStep.Started -> {
                        write = ImportWrite.begin(db, step, clock.now())
                        emit(step)
                    }

                    is ImportStep.FileStarted -> {
                        emit(step)
                    }

                    is ImportStep.FileFinished -> {
                        checkNotNull(write).writeFile(step.outcome)
                        emit(step)
                    }

                    is ImportStep.Completed -> {
                        val id = checkNotNull(write).also { it.complete(clock.now()) }.id
                        emit(ImportStep.Stored(checkNotNull(imports.get(id))))
                    }

                    is ImportStep.Stored -> {
                        error("an Importer never emits Stored")
                    }
                }
            }
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
