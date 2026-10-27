package com.jassemsendi.unpacked.ingest.instagram

import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind
import kotlin.time.Instant

/** One record after decoding and flattening, with everything a mapper needs to build canonical records. */
internal data class FlatRecord(
    val fields: Map<String, String>,
    val time: Instant?,
    val provenance: Provenance,
    val rawKind: RawKind,
)

internal data class Mapped(
    val events: List<Event> = emptyList(),
    val claims: List<Claim> = emptyList(),
) {
    companion object {
        val NONE = Mapped()
    }
}

/** Turns one flattened record of a known file into Events and Claims. One mapper per registered file. */
internal fun interface InstagramMapper {
    fun map(record: FlatRecord): Mapped
}

/** The registry of recognised Instagram files, keyed by file name regardless of case. */
internal object InstagramFiles {
    private val mappers: Map<String, InstagramMapper> =
        mapOf(
            "ads_viewed.json" to AdsViewedMapper,
        )

    fun mapperFor(fileName: String): InstagramMapper? = mappers[fileName.lowercase()]
}
