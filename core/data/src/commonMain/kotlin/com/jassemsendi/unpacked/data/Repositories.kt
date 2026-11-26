package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EmptyResult
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.Result
import kotlin.time.Instant

// The boundary of the library: everything outside core:data reads through these and never sees Room.
// Every call answers with a Result (ADR 0009): a library that cannot be read is a DataError.Local, never a throw.

public interface ImportRepository {
    /** Every Import, oldest first, including ones that never completed. */
    public suspend fun all(): Result<List<Import>, DataError.Local>

    /** [DataError.Local.NOT_FOUND] when no Import has that id. */
    public suspend fun get(id: ImportId): Result<Import, DataError.Local>

    /**
     * Removes the Import, every Event and Claim it produced, and every Entity left without a reference.
     * An Import that is already gone is a success: it is gone either way.
     */
    public suspend fun delete(id: ImportId): EmptyResult<DataError.Local>
}

public interface EventRepository {
    /** Events per Kind across the whole library, or within one Import. Kinds with no Events are absent. */
    public suspend fun countByKind(importId: ImportId? = null): Result<Map<EventKind, Int>, DataError.Local>

    /** [DataError.Local.NOT_FOUND] when no Event has that Provenance. */
    public suspend fun byProvenance(provenance: Provenance): Result<Event, DataError.Local>

    /** The Events of one Import in time order. */
    public suspend fun forImport(importId: ImportId): Result<List<Event>, DataError.Local>

    /**
     * One page of the whole library's Events, newest first, narrowed to one Kind when [kind] is given.
     * Events at the same instant keep a fixed order across pages, so paging never repeats or skips one.
     */
    public suspend fun newestFirst(
        kind: EventKind? = null,
        limit: Int,
        offset: Int,
    ): Result<List<Event>, DataError.Local>
}

public interface ClaimRepository {
    /** Claims per Kind across the whole library, or within one Import. Kinds with no Claims are absent. */
    public suspend fun countByKind(importId: ImportId? = null): Result<Map<ClaimKind, Int>, DataError.Local>

    /** [DataError.Local.NOT_FOUND] when no Claim has that Provenance. */
    public suspend fun byProvenance(provenance: Provenance): Result<Claim, DataError.Local>

    /** The Claims of one Import: undated ones first in file order, then dated ones in "as of" order. */
    public suspend fun forImport(importId: ImportId): Result<List<Claim>, DataError.Local>
}

public interface EntityRepository {
    /** By `source:kind:key` (ADR 0002); [DataError.Local.NOT_FOUND] when no Entity has that id. */
    public suspend fun byId(id: String): Result<Entity, DataError.Local>

    /** Every Entity any Event or Claim in the library refers to, in id order. */
    public suspend fun all(): Result<List<Entity>, DataError.Local>
}

public interface SearchRepository {
    /**
     * One page of the Events and Claims whose attribute values or Entity names contain every word of
     * [text], each word matched from its start without regard to case or accents. Newest first: Events by
     * their time, Claims by their "as of" time, Claims with none last; ties keep a fixed order across
     * pages. Blank text finds nothing. Hits are in time order, not relevance order (ADR 0004).
     */
    public suspend fun search(
        text: String,
        limit: Int,
        offset: Int,
    ): Result<List<SearchHit>, DataError.Local>
}

/** One Event or Claim a search found, told apart because each has its own repository and reads differently. */
public sealed interface SearchHit {
    public val provenance: Provenance

    /** What the hit sorts by: an Event's moment, a Claim's "as of" time, or nothing. */
    public val time: Instant?

    public data class OfEvent(
        val event: Event,
    ) : SearchHit {
        override val provenance: Provenance get() = event.provenance
        override val time: Instant get() = event.time
    }

    public data class OfClaim(
        val claim: Claim,
    ) : SearchHit {
        override val provenance: Provenance get() = claim.provenance
        override val time: Instant? get() = claim.asOf
    }
}
