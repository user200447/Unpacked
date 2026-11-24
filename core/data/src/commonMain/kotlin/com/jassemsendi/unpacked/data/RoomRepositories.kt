package com.jassemsendi.unpacked.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.jassemsendi.unpacked.data.db.ClaimRow
import com.jassemsendi.unpacked.data.db.EntityCountRow
import com.jassemsendi.unpacked.data.db.EntityLink
import com.jassemsendi.unpacked.data.db.EntityTallyRow
import com.jassemsendi.unpacked.data.db.EventDao
import com.jassemsendi.unpacked.data.db.EventRow
import com.jassemsendi.unpacked.data.db.HitType
import com.jassemsendi.unpacked.data.db.LibraryDatabase
import com.jassemsendi.unpacked.data.db.OwnedByRecord
import com.jassemsendi.unpacked.data.db.SearchHitRow
import com.jassemsendi.unpacked.data.db.inChunks
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EmptyResult
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.asEmptyResult
import kotlin.time.Instant

/** A row that is not there is [DataError.Local.NOT_FOUND]; every repository answers absence the same way. */
private fun <T : Any> Result<T?, DataError.Local>.orNotFound(): Result<T, DataError.Local> =
    when (this) {
        is Result.Error -> this
        is Result.Success -> data?.let { Result.Success(it) } ?: Result.Error(DataError.Local.NOT_FOUND)
    }

internal class RoomImportRepository(
    private val db: LibraryDatabase,
) : ImportRepository {
    private val dao get() = db.importDao()

    override suspend fun all(): Result<List<Import>, DataError.Local> =
        localCall {
            val rows = dao.all()
            val files = rows.map { it.id }.inChunks { dao.files(it) }.groupBy { it.importId }
            rows.map { row -> row.toModel(files[row.id].orEmpty().map { it.toModel() }) }
        }

    override suspend fun get(id: ImportId): Result<Import, DataError.Local> =
        localCall {
            dao.byId(id.value)?.toModel(dao.files(listOf(id.value)).map { it.toModel() })
        }.orNotFound()

    override suspend fun delete(id: ImportId): EmptyResult<DataError.Local> =
        localCall {
            db.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    dao.delete(id.value)
                    db.entityDao().deleteOrphans()
                }
            }
        }

    override suspend fun relocate(
        id: ImportId,
        location: ExportLocation,
    ): EmptyResult<DataError.Local> =
        localCall { dao.relocate(id.value, location.kindColumn, location.path).takeIf { it > 0 } }
            .orNotFound()
            .asEmptyResult()
}

internal class RoomEntityRepository(
    private val db: LibraryDatabase,
) : EntityRepository {
    private val dao get() = db.entityDao()

    override suspend fun byId(id: String): Result<Entity, DataError.Local> =
        localCall { hydrate(listOf(id)).singleOrNull() }.orNotFound()

    override suspend fun all(): Result<List<Entity>, DataError.Local> = localCall { hydrate(dao.all().map { it.id }) }

    override suspend fun countByKind(): Result<Map<EntityKind, Int>, DataError.Local> =
        localCall { db.entityCountDao().countByKind().toMap(EntityKind::valueOf) }

    override suspend fun list(
        kind: EntityKind?,
        order: EntityOrder,
        limit: Int,
        offset: Int,
    ): Result<List<EntityCounts>, DataError.Local> =
        localCall {
            val byCount = order == EntityOrder.MostNamed
            withCounts(db.entityCountDao().list(kind?.name, byCount, limit, offset))
        }

    override suspend fun counts(id: String): Result<EntityCounts, DataError.Local> =
        localCall { db.entityCountDao().counts(id)?.let { withCounts(listOf(it)).singleOrNull() } }.orNotFound()

    /** The Entities behind counted rows, in row order, with their attributes read in bulk. */
    private suspend fun withCounts(rows: List<EntityCountRow>): List<EntityCounts> {
        val entityById = hydrate(rows.map { it.entity.id }).associateBy { it.id }
        return rows.mapNotNull { row ->
            entityById[row.entity.id]?.let { EntityCounts(it, row.eventCount, row.claimCount) }
        }
    }

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

    /** The Entities behind tallied rows, in row order, with their attributes read in bulk. */
    suspend fun tallies(
        count: Int,
        rows: List<EntityTallyRow>,
    ): EntityTallies {
        val entityById = hydrate(rows.map { it.entity.id }).associateBy { it.id }
        return EntityTallies(
            count,
            rows.mapNotNull { row ->
                entityById[row.entity.id]?.let { EntityTally(it, row.count) }
            },
        )
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
    private val filtered get() = db.eventFilterDao()
    private val entities = RoomEntityRepository(db)

    override suspend fun countByKind(importId: ImportId?): Result<Map<EventKind, Int>, DataError.Local> =
        localCall { dao.countByKind(importId?.value).toMap(EventKind::valueOf) }

    override suspend fun byProvenance(provenance: Provenance): Result<Event, DataError.Local> =
        localCall { byIds(listOf(provenance.hash)).singleOrNull() }.orNotFound()

    override suspend fun forImport(importId: ImportId): Result<List<Event>, DataError.Local> =
        localCall { entities.hydrateEvents(dao, dao.forImport(importId.value)) }

    override suspend fun newestFirst(
        kind: EventKind?,
        limit: Int,
        offset: Int,
        importId: ImportId?,
    ): Result<List<Event>, DataError.Local> =
        localCall { entities.hydrateEvents(dao, dao.newestFirst(kind?.name, importId?.value, limit, offset)) }

    override suspend fun forEntity(
        entityId: String,
        limit: Int,
        offset: Int,
    ): Result<List<Event>, DataError.Local> =
        localCall {
            entities.hydrateEvents(dao, db.entityCountDao().events(entityId, limit, offset))
        }

    override suspend fun count(filter: EventFilter): Result<Int, DataError.Local> =
        localCall { with(filter) { filtered.count(kind?.name, from, to, source?.id, entityId) } }

    override suspend fun times(filter: EventFilter): Result<List<Instant>, DataError.Local> =
        localCall {
            with(
                filter,
            ) { filtered.times(kind?.name, from, to, source?.id, entityId) }.map(Instant::fromEpochMilliseconds)
        }

    override suspend fun newest(
        filter: EventFilter,
        limit: Int,
    ): Result<List<Event>, DataError.Local> =
        localCall {
            entities.hydrateEvents(
                dao,
                with(filter) { filtered.newest(kind?.name, from, to, source?.id, entityId, limit) },
            )
        }

    override suspend fun topEntities(
        filter: EventFilter,
        entityKind: EntityKind,
        limit: Int,
    ): Result<EntityTallies, DataError.Local> =
        localCall {
            with(filter) {
                entities.tallies(
                    filtered.entityCount(kind?.name, from, to, source?.id, entityId, entityKind.name),
                    filtered.entityTallies(kind?.name, from, to, source?.id, entityId, entityKind.name, limit),
                )
            }
        }

    private val EventFilter.from: Long? get() = range?.start?.toEpochMilliseconds()
    private val EventFilter.to: Long? get() = range?.endInclusive?.toEpochMilliseconds()

    /** The Events with these ids, in no particular order; ids that no longer exist are left out. */
    suspend fun byIds(ids: List<Long>): List<Event> = entities.hydrateEvents(dao, ids.inChunks { dao.byIds(it) })
}

/** Event rows with their attributes and the Entities they link to, as [RoomEntityRepository.hydrate] reads them. */
private suspend fun RoomEntityRepository.hydrateEvents(
    dao: EventDao,
    rows: List<EventRow>,
): List<Event> =
    hydrate(rows, { it.id }, dao::attributes, dao::links) { row, attributes, linked ->
        row.toModel(attributes, linked)
    }

internal class RoomClaimRepository(
    private val db: LibraryDatabase,
) : ClaimRepository {
    private val dao get() = db.claimDao()
    private val entities = RoomEntityRepository(db)

    override suspend fun countByKind(importId: ImportId?): Result<Map<ClaimKind, Int>, DataError.Local> =
        localCall { dao.countByKind(importId?.value).toMap(ClaimKind::valueOf) }

    override suspend fun byProvenance(provenance: Provenance): Result<Claim, DataError.Local> =
        localCall { byIds(listOf(provenance.hash)).singleOrNull() }.orNotFound()

    override suspend fun forImport(importId: ImportId): Result<List<Claim>, DataError.Local> =
        localCall { hydrate(dao.forImport(importId.value)) }

    override suspend fun byKind(
        kind: ClaimKind,
        limit: Int,
        offset: Int,
        importId: ImportId?,
    ): Result<List<Claim>, DataError.Local> =
        localCall { hydrate(dao.byKind(kind.name, importId?.value, limit, offset)) }

    override suspend fun forEntity(entityId: String): Result<List<Claim>, DataError.Local> =
        localCall { hydrate(db.entityCountDao().claims(entityId)) }

    override suspend fun topEntities(
        kind: ClaimKind,
        entityKind: EntityKind,
        limit: Int,
    ): Result<EntityTallies, DataError.Local> =
        localCall {
            val dao = db.insightCardDao()
            entities.tallies(
                dao.claimEntityCount(kind.name, entityKind.name),
                dao.claimEntityTallies(kind.name, entityKind.name, limit),
            )
        }

    /** The Claims with these ids, in no particular order; ids that no longer exist are left out. */
    suspend fun byIds(ids: List<Long>): List<Claim> = hydrate(ids.inChunks { dao.byIds(it) })

    private suspend fun hydrate(rows: List<ClaimRow>): List<Claim> =
        entities.hydrate(rows, { it.id }, dao::attributes, dao::links) { row, attributes, linked ->
            row.toModel(attributes, linked)
        }
}

/** Reads hits back through the Event and Claim repositories, so a hit is the same object the timeline reads. */
internal class RoomSearchRepository(
    private val db: LibraryDatabase,
    private val events: RoomEventRepository,
    private val claims: RoomClaimRepository,
) : SearchRepository {
    override suspend fun search(
        text: String,
        limit: Int,
        offset: Int,
    ): Result<List<SearchHit>, DataError.Local> {
        val match = matchExpression(text) ?: return Result.Success(emptyList())
        return localCall {
            val rows = db.searchDao().hits(match, limit, offset)
            val eventById = events.byIds(rows.idsOf(HitType.EVENT)).associateBy { it.id }
            val claimById = claims.byIds(rows.idsOf(HitType.CLAIM)).associateBy { it.id }
            rows.mapNotNull { row ->
                when (row.hitType) {
                    HitType.EVENT -> eventById[row.hitId]?.let { SearchHit.OfEvent(it) }
                    HitType.CLAIM -> claimById[row.hitId]?.let { SearchHit.OfClaim(it) }
                }
            }
        }
    }

    private fun List<SearchHitRow>.idsOf(type: HitType): List<Long> = filter { it.hitType == type }.map { it.hitId }
}
