package com.jassemsendi.unpacked.model

import kotlin.time.Instant

/** The identity of one stored Import: the row id the library assigned when the run started. */
public data class ImportId(
    val value: Long,
)

/**
 * One run of reading one Export into the library, as stored. [completedAt] is null while the run
 * is in progress and stays null if it never finished; such an Import is still visible and deletable.
 */
public data class Import(
    val id: ImportId,
    val startedAt: Instant,
    val completedAt: Instant?,
    val summary: ImportSummary,
)
