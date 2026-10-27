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
import kotlin.time.Instant

/**
 * Writes one Import as it runs: the Import row first, then one transaction per file, then the
 * completion time. A re-imported record replaces its earlier row, so the latest Import owns it.
 */
internal class LibraryWriter(
    private val db: LibraryDatabase,
) {
    suspend fun start(
        started: ImportStep.Started,
        at: Instant,
    ): Long =
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

    suspend fun writeFile(
        importId: Long,
        position: Int,
        outcome: FileOutcome,
    ): Unit =
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                db.importDao().insertFile(outcome.result.toRow(importId, position))
                val events = outcome.output.events
                val claims = outcome.output.claims
                writeEntities((events.flatMap { it.entities } + claims.flatMap { it.entities }).distinctBy { it.id })
                events.map { it.id }.forEachChunk { db.eventDao().delete(it) }
                db.eventDao().insert(events.map { it.toRow(importId) })
                db.eventDao().insertAttributes(events.flatMap { it.attributeRows() })
                db.eventDao().insertLinks(events.flatMap { it.linkRows() })
                claims.map { it.id }.forEachChunk { db.claimDao().delete(it) }
                db.claimDao().insert(claims.map { it.toRow(importId) })
                db.claimDao().insertAttributes(claims.flatMap { it.attributeRows() })
                db.claimDao().insertLinks(claims.flatMap { it.linkRows() })
            }
        }

    suspend fun complete(
        importId: Long,
        at: Instant,
    ): Unit = db.importDao().complete(importId, at.toEpochMillis())

    /**
     * Insert the Entities this file names for the first time and update the ones already stored,
     * decided by a lookup rather than by catching a constraint error, so every platform behaves alike.
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
}
