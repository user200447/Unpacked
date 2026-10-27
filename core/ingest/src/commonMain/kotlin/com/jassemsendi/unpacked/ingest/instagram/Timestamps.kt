package com.jassemsendi.unpacked.ingest.instagram

import kotlin.time.Instant

private const val MILLISECONDS_THRESHOLD = 100_000_000_000L

/**
 * Instagram writes epoch seconds in most files and epoch milliseconds in a few; both become the
 * same instant. A zero or missing timestamp means the Source gave no time.
 */
internal fun epochToInstant(value: Long?): Instant? =
    value?.takeIf { it > 0 }?.let {
        if (it > MILLISECONDS_THRESHOLD) Instant.fromEpochMilliseconds(it) else Instant.fromEpochSeconds(it)
    }
