package com.jassemsendi.unpacked.ingest.instagram

import com.jassemsendi.unpacked.ingest.Output
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind
import kotlin.time.Instant

/** One record after decoding and flattening, with everything a mapper needs to build Events and Claims. */
internal data class FlatRecord(
    val fields: Map<String, String>,
    val time: Instant?,
    val provenance: Provenance,
    val rawKind: RawKind,
)

/** Turns one flattened record of a known file into Events and Claims. One mapper per registered file. */
internal fun interface InstagramMapper {
    fun map(record: FlatRecord): Output
}

/** The registry of mappers for recognised Instagram files, keyed by file name regardless of case. */
internal object InstagramMappers {
    private val byFileName: Map<String, InstagramMapper> =
        mapOf(
            "ads_viewed.json" to AdsViewedMapper,
        )

    fun forFile(fileName: String): InstagramMapper? = byFileName[fileName.lowercase()]
}
