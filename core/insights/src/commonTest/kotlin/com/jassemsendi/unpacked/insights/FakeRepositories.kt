package com.jassemsendi.unpacked.insights

import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EntityTallies
import com.jassemsendi.unpacked.data.EntityTally
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.Result
import kotlin.time.Instant

// In-memory repositories that answer as the Room ones do, so a card reads the same against either (ADR 0005).

class FakeEventRepository(
    vararg initial: Event,
) : EventRepository {
    val events = initial.toMutableList()
    var failure: DataError.Local? = null

    override suspend fun countByKind(importId: ImportId?): Result<Map<EventKind, Int>, DataError.Local> =
        answer { events.groupingBy { it.kind }.eachCount() }

    override suspend fun byProvenance(provenance: Provenance): Result<Event, DataError.Local> =
        answer { events.find { it.provenance == provenance } }.orNotFound()

    override suspend fun forImport(importId: ImportId): Result<List<Event>, DataError.Local> = answer { events }

    override suspend fun newestFirst(
        kind: EventKind?,
        limit: Int,
        offset: Int,
    ): Result<List<Event>, DataError.Local> =
        answer {
            events
                .filter { kind == null || it.kind == kind }
                .sortedByDescending { it.time }
                .drop(
                    offset,
                ).take(limit)
        }

    override suspend fun forEntity(
        entityId: String,
        limit: Int,
        offset: Int,
    ): Result<List<Event>, DataError.Local> =
        answer {
            events
                .filter { event -> event.entities.any { it.id == entityId } }
                .sortedByDescending { it.time }
                .drop(offset)
                .take(limit)
        }

    override suspend fun timesOf(kind: EventKind): Result<List<Instant>, DataError.Local> =
        answer { events.filter { it.kind == kind }.map { it.time } }

    override suspend fun topEntities(
        kind: EventKind,
        entityKind: EntityKind,
        limit: Int,
    ): Result<EntityTallies, DataError.Local> =
        answer { events.filter { it.kind == kind }.flatMap { it.entities }.tallied(entityKind, limit) }

    private fun <T> answer(read: () -> T): Result<T, DataError.Local> =
        failure?.let { Result.Error(it) } ?: Result.Success(read())
}

class FakeClaimRepository(
    vararg initial: Claim,
) : ClaimRepository {
    val claims = initial.toMutableList()
    var failure: DataError.Local? = null

    override suspend fun countByKind(importId: ImportId?): Result<Map<ClaimKind, Int>, DataError.Local> =
        answer { claims.groupingBy { it.kind }.eachCount() }

    override suspend fun byProvenance(provenance: Provenance): Result<Claim, DataError.Local> =
        answer { claims.find { it.provenance == provenance } }.orNotFound()

    override suspend fun forImport(importId: ImportId): Result<List<Claim>, DataError.Local> = answer { claims }

    override suspend fun byKind(
        kind: ClaimKind,
        limit: Int,
        offset: Int,
    ): Result<List<Claim>, DataError.Local> = answer { claims.filter { it.kind == kind }.drop(offset).take(limit) }

    override suspend fun forEntity(entityId: String): Result<List<Claim>, DataError.Local> =
        answer { claims.filter { claim -> claim.entities.any { it.id == entityId } } }

    override suspend fun topEntities(
        kind: ClaimKind,
        entityKind: EntityKind,
        limit: Int,
    ): Result<EntityTallies, DataError.Local> =
        answer { claims.filter { it.kind == kind }.flatMap { it.entities }.tallied(entityKind, limit) }

    private fun <T> answer(read: () -> T): Result<T, DataError.Local> =
        failure?.let { Result.Error(it) } ?: Result.Success(read())
}

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
