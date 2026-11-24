package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EntityCounts
import com.jassemsendi.unpacked.data.EntityOrder
import com.jassemsendi.unpacked.data.EntityRepository
import com.jassemsendi.unpacked.data.EntityTallies
import com.jassemsendi.unpacked.data.EntityTally
import com.jassemsendi.unpacked.data.EventFilter
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.data.ImportRepository
import com.jassemsendi.unpacked.data.SearchHit
import com.jassemsendi.unpacked.data.SearchRepository
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
import com.jassemsendi.unpacked.model.Source
import kotlin.time.Instant

// In-memory repositories that answer as the Room ones do, so a Tool reads the same against either (ADR 0005).
// An Event or Claim belongs to the Import whose Export id its Provenance names, which is how a Source filter works.

class FakeImportRepository(
    vararg initial: Import,
) : ImportRepository {
    val imports = initial.toMutableList()
    var failure: DataError.Local? = null

    fun sourceOf(provenance: Provenance): Source? =
        imports.find { it.summary.exportId == provenance.exportId }?.summary?.source

    override suspend fun all(): Result<List<Import>, DataError.Local> = answer { imports.sortedBy { it.id.value } }

    override suspend fun get(id: ImportId): Result<Import, DataError.Local> =
        answer { imports.find { it.id == id } }.orNotFound()

    override suspend fun delete(id: ImportId): EmptyResult<DataError.Local> = error("Tools never write")

    override suspend fun relocate(
        id: ImportId,
        location: ExportLocation,
    ): EmptyResult<DataError.Local> = error("Tools never write")

    private fun <T> answer(read: () -> T): Result<T, DataError.Local> =
        failure?.let { Result.Error(it) } ?: Result.Success(read())
}

class FakeEventRepository(
    private val imports: FakeImportRepository,
    vararg initial: Event,
) : EventRepository {
    val events = initial.toMutableList()
    var failure: DataError.Local? = null

    private val newestFirst = compareByDescending<Event> { it.time }.thenByDescending { it.id }

    override suspend fun countByKind(importId: ImportId?): Result<Map<EventKind, Int>, DataError.Local> =
        answer { ofImport(importId).groupingBy { it.kind }.eachCount() }

    override suspend fun byProvenance(provenance: Provenance): Result<Event, DataError.Local> =
        answer { events.find { it.provenance == provenance } }.orNotFound()

    override suspend fun forImport(importId: ImportId): Result<List<Event>, DataError.Local> =
        answer { ofImport(importId).sortedWith(compareBy({ it.time }, { it.id })) }

    override suspend fun newestFirst(
        kind: EventKind?,
        limit: Int,
        offset: Int,
        importId: ImportId?,
    ): Result<List<Event>, DataError.Local> =
        answer {
            ofImport(importId)
                .filter { kind == null || it.kind == kind }
                .sortedWith(newestFirst)
                .drop(offset)
                .take(limit)
        }

    override suspend fun forEntity(
        entityId: String,
        limit: Int,
        offset: Int,
    ): Result<List<Event>, DataError.Local> =
        answer {
            events
                .filter { it.names(entityId) }
                .sortedWith(newestFirst)
                .drop(offset)
                .take(limit)
        }

    override suspend fun count(filter: EventFilter): Result<Int, DataError.Local> = answer { kept(filter).size }

    override suspend fun times(filter: EventFilter): Result<List<Instant>, DataError.Local> =
        answer { kept(filter).map { it.time } }

    override suspend fun newest(
        filter: EventFilter,
        limit: Int,
    ): Result<List<Event>, DataError.Local> = answer { kept(filter).sortedWith(newestFirst).take(limit) }

    override suspend fun topEntities(
        filter: EventFilter,
        entityKind: EntityKind,
        limit: Int,
    ): Result<EntityTallies, DataError.Local> =
        answer { kept(filter).flatMap { it.entities }.tallied(entityKind, limit) }

    private fun kept(filter: EventFilter): List<Event> {
        val entityId = filter.entityId
        return events.filter { event ->
            (filter.kind == null || event.kind == filter.kind) &&
                (filter.range?.let { event.time in it } ?: true) &&
                (filter.source == null || imports.sourceOf(event.provenance) == filter.source) &&
                (entityId == null || event.names(entityId))
        }
    }

    private fun ofImport(importId: ImportId?): List<Event> =
        if (importId == null) {
            events
        } else {
            val export =
                imports.imports
                    .find { it.id == importId }
                    ?.summary
                    ?.exportId
            events.filter { it.provenance.exportId == export }
        }

    private fun <T> answer(read: () -> T): Result<T, DataError.Local> =
        failure?.let { Result.Error(it) } ?: Result.Success(read())
}

/** How the Room repository orders Claims for a Kind or an Entity: dated ones newest first, then undated ones in file order. */
val claimsNewestFirst: Comparator<Claim> =
    compareBy<Claim> { it.asOf == null }
        .thenByDescending { it.asOf }
        .thenBy { it.provenance.path }
        .thenBy { it.provenance.recordIndex }
        .thenBy { it.provenance.itemIndex }

class FakeClaimRepository(
    private val imports: FakeImportRepository,
    vararg initial: Claim,
) : ClaimRepository {
    val claims = initial.toMutableList()
    var failure: DataError.Local? = null

    override suspend fun countByKind(importId: ImportId?): Result<Map<ClaimKind, Int>, DataError.Local> =
        answer { ofImport(importId).groupingBy { it.kind }.eachCount() }

    override suspend fun byProvenance(provenance: Provenance): Result<Claim, DataError.Local> =
        answer { claims.find { it.provenance == provenance } }.orNotFound()

    override suspend fun forImport(importId: ImportId): Result<List<Claim>, DataError.Local> =
        answer { ofImport(importId) }

    override suspend fun byKind(
        kind: ClaimKind,
        limit: Int,
        offset: Int,
        importId: ImportId?,
    ): Result<List<Claim>, DataError.Local> =
        answer {
            ofImport(importId)
                .filter { it.kind == kind }
                .sortedWith(claimsNewestFirst)
                .drop(offset)
                .take(limit)
        }

    override suspend fun forEntity(entityId: String): Result<List<Claim>, DataError.Local> =
        answer { claims.filter { it.names(entityId) }.sortedWith(claimsNewestFirst) }

    override suspend fun topEntities(
        kind: ClaimKind,
        entityKind: EntityKind,
        limit: Int,
    ): Result<EntityTallies, DataError.Local> =
        answer { claims.filter { it.kind == kind }.flatMap { it.entities }.tallied(entityKind, limit) }

    private fun ofImport(importId: ImportId?): List<Claim> =
        if (importId == null) {
            claims
        } else {
            val export =
                imports.imports
                    .find { it.id == importId }
                    ?.summary
                    ?.exportId
            claims.filter { it.provenance.exportId == export }
        }

    private fun <T> answer(read: () -> T): Result<T, DataError.Local> =
        failure?.let { Result.Error(it) } ?: Result.Success(read())
}

/** The Entities the Events and Claims name, derived on every read as the Room one's rows are kept by the Import. */
class FakeEntityRepository(
    private val events: FakeEventRepository,
    private val claims: FakeClaimRepository,
) : EntityRepository {
    var failure: DataError.Local? = null

    private fun everyCounts(): List<EntityCounts> {
        val named = (events.events.flatMap { it.entities } + claims.claims.flatMap { it.entities }).distinctBy { it.id }
        return named
            .map { entity ->
                EntityCounts(
                    entity,
                    eventCount = events.events.count { it.names(entity.id) },
                    claimCount = claims.claims.count { it.names(entity.id) },
                )
            }.sortedBy { it.entity.id }
    }

    override suspend fun byId(id: String): Result<Entity, DataError.Local> =
        answer { everyCounts().find { it.entity.id == id }?.entity }.orNotFound()

    override suspend fun all(): Result<List<Entity>, DataError.Local> = answer { everyCounts().map { it.entity } }

    override suspend fun countByKind(): Result<Map<EntityKind, Int>, DataError.Local> =
        answer { everyCounts().groupingBy { it.entity.kind }.eachCount() }

    override suspend fun list(
        kind: EntityKind?,
        order: EntityOrder,
        limit: Int,
        offset: Int,
    ): Result<List<EntityCounts>, DataError.Local> =
        answer {
            everyCounts()
                .filter { kind == null || it.entity.kind == kind }
                .sortedWith(
                    when (order) {
                        EntityOrder.MostNamed -> compareByDescending<EntityCounts> { it.total }
                        EntityOrder.Name -> compareBy { 0 }
                    }.thenBy { it.entity.displayName.lowercase() }.thenBy { it.entity.id },
                ).drop(offset)
                .take(limit)
        }

    override suspend fun counts(id: String): Result<EntityCounts, DataError.Local> =
        answer { everyCounts().find { it.entity.id == id } }.orNotFound()

    private fun <T> answer(read: () -> T): Result<T, DataError.Local> =
        failure?.let { Result.Error(it) } ?: Result.Success(read())
}

/** Every word matched from its start against a Record's values and Entity names, as the FTS table does (ADR 0010). */
class FakeSearchRepository(
    private val events: FakeEventRepository,
    private val claims: FakeClaimRepository,
) : SearchRepository {
    var failure: DataError.Local? = null

    override suspend fun search(
        text: String,
        limit: Int,
        offset: Int,
    ): Result<List<SearchHit>, DataError.Local> =
        answer {
            val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return@answer emptyList()
            val hits =
                events.events
                    .filter {
                        it.attributes.values.matches(
                            words,
                            it.entities,
                        )
                    }.map { SearchHit.OfEvent(it) } +
                    claims.claims
                        .filter {
                            it.attributes.values.matches(
                                words,
                                it.entities,
                            )
                        }.map { SearchHit.OfClaim(it) }
            hits
                .sortedWith(compareBy<SearchHit> { it.time == null }.thenByDescending { it.time })
                .drop(offset)
                .take(limit)
        }

    private fun Collection<String>.matches(
        words: List<String>,
        entities: List<Entity>,
    ): Boolean {
        val tokens = (this + entities.map { it.displayName }).flatMap { it.lowercase().split(Regex("\\W+")) }
        return words.all { word -> tokens.any { it.startsWith(word) } }
    }

    private fun <T> answer(read: () -> T): Result<T, DataError.Local> =
        failure?.let { Result.Error(it) } ?: Result.Success(read())
}

private fun Event.names(entityId: String) = entities.any { it.id == entityId }

private fun Claim.names(entityId: String) = entities.any { it.id == entityId }

/** How the Room repositories tally the Entities a set of Events or Claims name: most named first, ties by name then id. */
fun List<Entity>.tallied(
    entityKind: EntityKind,
    limit: Int,
): EntityTallies {
    val all =
        filter { it.kind == entityKind }
            .groupBy { it.id }
            .map { (_, same) -> EntityTally(same.first(), same.size) }
            .sortedWith(
                compareByDescending<EntityTally> { it.count }
                    .thenBy { it.entity.displayName.lowercase() }
                    .thenBy { it.entity.id },
            )
    return EntityTallies(all.size, all.take(limit))
}

private fun <T : Any> Result<T?, DataError.Local>.orNotFound(): Result<T, DataError.Local> =
    when (this) {
        is Result.Error -> this
        is Result.Success -> data?.let { Result.Success(it) } ?: Result.Error(DataError.Local.NOT_FOUND)
    }
