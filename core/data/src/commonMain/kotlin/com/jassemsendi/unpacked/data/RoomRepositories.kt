package com.jassemsendi.unpacked.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.jassemsendi.unpacked.data.db.ClaimRow
import com.jassemsendi.unpacked.data.db.EntityLink
import com.jassemsendi.unpacked.data.db.EventRow
import com.jassemsendi.unpacked.data.db.LibraryDatabase
import com.jassemsendi.unpacked.data.db.OwnedByRecord
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

    override suspend fun byId(id: String): Entity? = hydrate(listOf(id)).singleOrNull()

    override suspend fun all(): List<Entity> = hydrate(dao.all().map { it.id })

    /** Entities in the order of [ids]; ids that no longer exist are left out. */
    suspend fun byIds(ids: List<String>): List<Entity> = hydrate(ids)

    /**
     * Turns Event or Claim rows into model objects: their attributes in record order, then their
     * Entities in link order, fetched in bulk so a page of records costs a handful of queries.
     */
    suspend fun <Row, Attribute : OwnedByRecord, Link : EntityLink, Model> hydrate(
        rows: List<Row>,
        idOf: (Row) -> Long,
        attributesOf: suspend (List<Long>) -> List<Attribute>,
        linksOf: suspend (List<Long>) -> List<Link>,
        build: (Row, List<Attribute>, List<Entity>) -> Model,
    ): List<Model> {
        val ids = rows.map(idOf)
        val attributes = ids.inChunks(attributesOf).groupBy { it.recordId }
        val links = ids.inChunks(linksOf).groupBy { it.recordId }
        val entityById =
            byIds(
                links.values
                    .flatten()
                    .map { it.entityId }
                    .distinct(),
            ).associateBy { it.id }
        return rows.map { row ->
            val id = idOf(row)
            build(row, attributes[id].orEmpty(), links[id].orEmpty().mapNotNull { entityById[it.entityId] })
        }
    }

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
        dao.countByKind(importId?.value).toMap(EventKind::valueOf)

    override suspend fun byProvenance(provenance: Provenance): Event? =
        dao.byId(provenance.hash)?.let { hydrate(listOf(it)).single() }

    override suspend fun forImport(importId: ImportId): List<Event> = hydrate(dao.forImport(importId.value))

    override suspend fun newestFirst(
        kind: EventKind?,
        limit: Int,
        offset: Int,
    ): List<Event> = hydrate(dao.newestFirst(kind?.name, limit, offset))

    private suspend fun hydrate(rows: List<EventRow>): List<Event> =
        entities.hydrate(rows, { it.id }, dao::attributes, dao::links) { row, attributes, linked ->
            row.toModel(attributes, linked)
        }
}

internal class RoomClaimRepository(
    private val db: LibraryDatabase,
) : ClaimRepository {
    private val dao get() = db.claimDao()
    private val entities = RoomEntityRepository(db)

    override suspend fun countByKind(importId: ImportId?): Map<ClaimKind, Int> =
        dao.countByKind(importId?.value).toMap(ClaimKind::valueOf)

    override suspend fun byProvenance(provenance: Provenance): Claim? =
        dao.byId(provenance.hash)?.let { hydrate(listOf(it)).single() }

    override suspend fun forImport(importId: ImportId): List<Claim> = hydrate(dao.forImport(importId.value))

    private suspend fun hydrate(rows: List<ClaimRow>): List<Claim> =
        entities.hydrate(rows, { it.id }, dao::attributes, dao::links) { row, attributes, linked ->
            row.toModel(attributes, linked)
        }
}
