package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Provenance

// The boundary of the library: everything outside core:data reads through these and never sees Room.

public interface ImportRepository {
    /** Every Import, oldest first, including ones that never completed. */
    public suspend fun all(): List<Import>

    public suspend fun get(id: ImportId): Import?

    /** Removes the Import, every Event and Claim it produced, and every Entity left without a reference. */
    public suspend fun delete(id: ImportId)
}

public interface EventRepository {
    /** Events per Kind across the whole library, or within one Import. Kinds with no Events are absent. */
    public suspend fun countByKind(importId: ImportId? = null): Map<EventKind, Int>

    public suspend fun byProvenance(provenance: Provenance): Event?

    /** The Events of one Import in time order. */
    public suspend fun forImport(importId: ImportId): List<Event>
}

public interface ClaimRepository {
    /** Claims per Kind across the whole library, or within one Import. Kinds with no Claims are absent. */
    public suspend fun countByKind(importId: ImportId? = null): Map<ClaimKind, Int>

    public suspend fun byProvenance(provenance: Provenance): Claim?

    /** The Claims of one Import: undated ones first in file order, then dated ones in "as of" order. */
    public suspend fun forImport(importId: ImportId): List<Claim>
}

public interface EntityRepository {
    /** By `source:kind:key` (ADR 0002). */
    public suspend fun byId(id: String): Entity?

    /** Every Entity any Event or Claim in the library refers to, in id order. */
    public suspend fun all(): List<Entity>
}
