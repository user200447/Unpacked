package com.jassemsendi.unpacked.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.jassemsendi.unpacked.data.db.ClaimRow
import com.jassemsendi.unpacked.data.db.EventRow
import com.jassemsendi.unpacked.data.db.LibraryDatabase
import com.jassemsendi.unpacked.data.db.inChunks
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Provenance

internal class RoomImportRepository(
    private val db: LibraryDatabase,
) : ImportRepository {
    private val dao get() = db.importDao()

    override suspend fun all(): List<Import> {
        val rows = dao.all()
        val files = rows.map { it.id }.inChunks { dao.files(it) }.groupBy { it.importId }
        return rows.map { row -> row.toModel(files[row.id].orEmpty().map { it.toModel() }) }
    }

    override suspend fun get(id: ImportId): Import? {
        val row = dao.byId(id.value) ?: return null
        return row.toModel(dao.files(listOf(id.value)).map { it.toModel() })
    }

    override suspend fun delete(id: ImportId): Unit =
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                dao.delete(id.value)
                db.entityDao().deleteOrphans()
            }
        }
}

internal class RoomEntityRepository(
    private val db: LibraryDatabase,
) : EntityRepository {
    private val dao get() = db.entityDao()

    override suspend fun byId(id: String): Entity? = dao.byId(id)?.let { byIds(listOf(id)).single() }

    override suspend fun all(): List<Entity> = hydrate(dao.all().map { it.id })

    /** Entities in the order of [ids]; ids that no longer exist are left out. */
    suspend fun byIds(ids: List<String>): List<Entity> = hydrate(ids)

    private suspend fun hydrate(ids: List<String>): List<Entity> {
        if (ids.isEmpty()) return emptyList()
        val rows = ids.inChunks { dao.byIds(it) }.associateBy { it.id }
        val attributes = ids.inChunks { dao.attributes(it) }.groupBy { it.entityId }
        return ids.mapNotNull { id -> rows[id]?.toModel(attributes[id].orEmpty()) }
    }
}

internal class RoomEventRepository(
    private val db: LibraryDatabase,
) : EventRepository {
    private val dao get() = db.eventDao()
    private val entities = RoomEntityRepository(db)

    override suspend fun countByKind(importId: ImportId?): Map<EventKind, Int> =
        (importId?.let { dao.countByKindForImport(it.value) } ?: dao.countByKind()).toMap(EventKind::valueOf)

    override suspend fun byProvenance(provenance: Provenance): Event? =
        dao.byId(provenance.hash)?.let { hydrate(listOf(it)).single() }

    override suspend fun forImport(importId: ImportId): List<Event> = hydrate(dao.forImport(importId.value))

    private suspend fun hydrate(rows: List<EventRow>): List<Event> {
        val ids = rows.map { it.id }
        val attributes = ids.inChunks { dao.attributes(it) }.groupBy { it.eventId }
        val links = ids.inChunks { dao.links(it) }.groupBy { it.eventId }
        val entityById =
            entities
                .byIds(
                    links.values
                        .flatten()
                        .map { it.entityId }
                        .distinct(),
                ).associateBy { it.id }
        return rows.map { row ->
            row.toModel(attributes[row.id].orEmpty(), links[row.id].orEmpty().mapNotNull { entityById[it.entityId] })
        }
    }
}

internal class RoomClaimRepository(
    private val db: LibraryDatabase,
) : ClaimRepository {
    private val dao get() = db.claimDao()
    private val entities = RoomEntityRepository(db)

    override suspend fun countByKind(importId: ImportId?): Map<ClaimKind, Int> =
        (importId?.let { dao.countByKindForImport(it.value) } ?: dao.countByKind()).toMap(ClaimKind::valueOf)

    override suspend fun byProvenance(provenance: Provenance): Claim? =
        dao.byId(provenance.hash)?.let { hydrate(listOf(it)).single() }

    override suspend fun forImport(importId: ImportId): List<Claim> = hydrate(dao.forImport(importId.value))

    private suspend fun hydrate(rows: List<ClaimRow>): List<Claim> {
        val ids = rows.map { it.id }
        val attributes = ids.inChunks { dao.attributes(it) }.groupBy { it.claimId }
        val links = ids.inChunks { dao.links(it) }.groupBy { it.claimId }
        val entityById =
            entities
                .byIds(
                    links.values
                        .flatten()
                        .map { it.entityId }
                        .distinct(),
                ).associateBy { it.id }
        return rows.map { row ->
            row.toModel(attributes[row.id].orEmpty(), links[row.id].orEmpty().mapNotNull { entityById[it.entityId] })
        }
    }
}
