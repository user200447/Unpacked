package com.jassemsendi.unpacked.ingest.instagram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * One record of the media-list dialect: a file in the Export with the moment it was created.
 * Its metadata is ignored.
 */
@Serializable
internal data class MediaRecord(
    val uri: String,
    @SerialName("creation_timestamp") val creationTimestamp: Long? = null,
    val title: String? = null,
)

internal fun MediaRecord.time(): Instant? = epochToInstant(creationTimestamp)

internal fun MediaRecord.flatten(): Map<String, String> =
    buildMap {
        put("URI", uri)
        title?.takeIf { it.isNotBlank() }?.let { put("Title", it) }
    }
