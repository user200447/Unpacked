package com.jassemsendi.unpacked.ui.home

import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * An in-memory library of Events that answers exactly as the Room one does, ignoring Import boundaries.
 * A test that wants to see the state while a read is in flight holds [gate] and completes it later;
 * one that wants every read refused sets [failure].
 */
class FakeEventRepository(
    vararg initial: Event,
) : EventRepository {
    val events = initial.toMutableList()
    var gate: CompletableDeferred<Unit>? = null
    var failure: DataError.Local? = null

    override suspend fun countByKind(importId: ImportId?): Result<Map<EventKind, Int>, DataError.Local> {
        gate?.await()
        return answer { events.groupingBy { it.kind }.eachCount() }
    }

    override suspend fun byProvenance(provenance: Provenance): Result<Event, DataError.Local> =
        answer { events.find { it.provenance == provenance } }.orNotFound()

    override suspend fun forImport(importId: ImportId): Result<List<Event>, DataError.Local> =
        answer { events.sortedWith(compareBy({ it.time }, { it.id })) }

    override suspend fun newestFirst(
        kind: EventKind?,
        limit: Int,
        offset: Int,
    ): Result<List<Event>, DataError.Local> {
        gate?.await()
        return answer {
            events
                .filter { kind == null || it.kind == kind }
                .sortedWith(compareByDescending<Event> { it.time }.thenByDescending { it.id })
                .drop(offset)
                .take(limit)
        }
    }

    private fun <T> answer(read: () -> T): Result<T, DataError.Local> =
        failure?.let { Result.Error(it) } ?: Result.Success(read())
}

class FakeClaimRepository(
    vararg initial: Claim,
) : ClaimRepository {
    val claims = initial.toMutableList()

    override suspend fun countByKind(importId: ImportId?): Result<Map<ClaimKind, Int>, DataError.Local> =
        Result.Success(claims.groupingBy { it.kind }.eachCount())

    override suspend fun byProvenance(provenance: Provenance): Result<Claim, DataError.Local> =
        Result.Success(claims.find { it.provenance == provenance }).orNotFound()

    override suspend fun forImport(importId: ImportId): Result<List<Claim>, DataError.Local> = Result.Success(claims)
}

/** Absence answers as the Room repositories answer it. */
private fun <T : Any> Result<T?, DataError.Local>.orNotFound(): Result<T, DataError.Local> =
    when (this) {
        is Result.Error -> this
        is Result.Success -> data?.let { Result.Success(it) } ?: Result.Error(DataError.Local.NOT_FOUND)
    }

const val FIRST_EVENT_MILLIS = 1_793_095_920_000L // 2026-10-27T10:12:00Z

val export = ExportId("instagram-me-2026-10-20")

fun provenanceOf(
    path: String,
    recordIndex: Int,
    itemIndex: Int = 0,
) = Provenance(export, path, recordIndex, itemIndex)

/** An Event [minutesAfterFirst] minutes after the first one, so a list of them has a known order. */
fun event(
    kind: EventKind,
    minutesAfterFirst: Int,
    path: String = "ads_information/ads_viewed.json",
    recordIndex: Int = minutesAfterFirst,
    attributes: Map<String, String> = emptyMap(),
    entities: List<Entity> = emptyList(),
): Event =
    Event(
        kind = kind,
        rawKind = RawKind(path.substringAfterLast('/').removeSuffix(".json")),
        time = Instant.fromEpochMilliseconds(FIRST_EVENT_MILLIS) + minutesAfterFirst.minutes,
        provenance = provenanceOf(path, recordIndex),
        entities = entities,
        attributes = attributes,
    )

fun advertiser(name: String): Entity = Entity(Source.Instagram, EntityKind.Advertiser, Entity.normalizeKey(name), name)
