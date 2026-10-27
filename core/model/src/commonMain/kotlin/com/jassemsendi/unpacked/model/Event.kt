package com.jassemsendi.unpacked.model

import kotlin.time.Instant

/** Something that happened at a known moment, recorded by a Source. */
public data class Event(
    val kind: EventKind,
    val rawKind: RawKind,
    val time: Instant,
    val provenance: Provenance,
    val entities: List<Entity> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
)

/** A standing assertion a Source makes about the user, with no moment of occurrence. */
public data class Claim(
    val kind: ClaimKind,
    val rawKind: RawKind,
    val provenance: Provenance,
    val asOf: Instant? = null,
    val entities: List<Entity> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
)
