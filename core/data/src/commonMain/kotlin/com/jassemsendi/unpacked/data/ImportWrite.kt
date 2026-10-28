package com.jassemsendi.unpacked.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.jassemsendi.unpacked.data.db.ImportRow
import com.jassemsendi.unpacked.data.db.LibraryDatabase
import com.jassemsendi.unpacked.data.db.forEachChunk
import com.jassemsendi.unpacked.data.db.inChunks
import com.jassemsendi.unpacked.ingest.FileOutcome
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.ImportId
import kotlin.time.Instant

/**
 * Writes one Import as it runs (ADR 0007): the Import row first, then one transaction per file in
 * visit order, then the completion time. A re-imported record replaces its earlier row, so the
 * latest Import owns it. One instance per run; [begin] creates it.
 */
internal class ImportWrite private constructor(
    private val db: LibraryDatabase,
    val id: ImportId,
) {
    private var position = 0

    suspend fun writeFile(outcome: FileOutcome): Unit =
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                db.importDao().insertFile(outcome.result.toRow(id, position++))
                val events = outcome.output.events
                val claims = outcome.output.claims
                writeEntities((events.flatMap { it.entities } + claims.flatMap { it.entities }).distinctBy { it.id })
                events.map { it.id }.forEachChunk { db.eventDao().delete(it) }
                db.eventDao().insert(events.map { it.toRow(id) })
                db.eventDao().insertAttributes(events.flatMap { it.attributeRows() })
                db.eventDao().insertLinks(events.flatMap { it.linkRows() })
                db.searchDao().insertEvents(events.mapNotNull { it.searchRow() })
                claims.map { it.id }.forEachChunk { db.claimDao().delete(it) }
                db.claimDao().insert(claims.map { it.toRow(id) })
                db.claimDao().insertAttributes(claims.flatMap { it.attributeRows() })
                db.claimDao().insertLinks(claims.flatMap { it.linkRows() })
                db.searchDao().insertClaims(claims.mapNotNull { it.searchRow() })
            }
        }

    suspend fun complete(at: Instant): Unit = db.importDao().complete(id.value, at.toEpochMillis())

    /**
     * Insert the Entities this file names for the first time and update the ones already stored,
     * decided by a lookup rather than by catching a constraint error, so every platform behaves alike.
     * A file that names one Entity twice keeps the first description.
     */
    private suspend fun writeEntities(entities: List<Entity>) {
        if (entities.isEmpty()) return
        val ids = entities.map { it.id }
        val known = ids.inChunks { db.entityDao().byIds(it) }.map { it.id }.toSet()
        val (existing, new) = entities.partition { it.id in known }
        ids.forEachChunk { db.entityDao().deleteAttributes(it) }
        db.entityDao().insert(new.map { it.toRow() })
        db.entityDao().update(existing.map { it.toRow() })
        db.entityDao().insertAttributes(entities.flatMap { it.attributeRows() })
    }

    companion object {
        /** Records that an Import of this Export began [at] and returns the write that will fill it. */
        suspend fun begin(
            db: LibraryDatabase,
            started: ImportStep.Started,
            at: Instant,
        ): ImportWrite {
            val rowId =
                db.importDao().insert(
                    ImportRow(
                        exportId = started.exportId.value,
                        source = started.source.id,
                        startedAt = at.toEpochMillis(),
                        completedAt = null,
                        jsonFiles = started.inventory.jsonFiles,
                        mediaFiles = started.inventory.mediaFiles,
                        otherFiles = started.inventory.otherFiles,
                    ),
                )
            return ImportWrite(db, ImportId(rowId))
        }
    }
}
