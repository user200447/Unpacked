package com.jassemsendi.unpacked.ingest.instagram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * One record of the `string_list_data` / `string_map_data` dialect: an optional title, an anonymous
 * list of entries, and a map of labelled entries. Media references beside them are ignored.
 */
@Serializable
internal data class StringDataRecord(
    val title: String? = null,
    @SerialName("string_list_data") val stringListData: List<StringEntry> = emptyList(),
    @SerialName("string_map_data") val stringMapData: Map<String, StringEntry> = emptyMap(),
)

/** One entry: any of a value, a link and a timestamp, each of which Instagram may leave empty. */
@Serializable
internal data class StringEntry(
    val href: String? = null,
    val value: String? = null,
    val timestamp: Long? = null,
)

/** The record's time: the first dated entry, labelled ones first, or null when none is dated. */
internal fun StringDataRecord.time(): Instant? =
    (stringMapData.values + stringListData).firstNotNullOfOrNull { epochToInstant(it.timestamp) }

/**
 * Flattens to label/value pairs: the title under "Title", each labelled entry under its label, and
 * the anonymous entries joined under "Value" and "Href". An entry with only a timestamp shows it as
 * an ISO-8601 instant. Empty values are dropped.
 */
internal fun StringDataRecord.flatten(): Map<String, String> =
    buildMap {
        title.orBlank()?.let { put("Title", it) }
        for ((label, entry) in stringMapData) {
            entry.text()?.let { put(label, it) }
        }
        stringListData.mapNotNull { it.value.orBlank() }.joinOrNull()?.let { put("Value", it) }
        stringListData.mapNotNull { it.href.orBlank() }.joinOrNull()?.let { put("Href", it) }
    }

private fun StringEntry.text(): String? = value.orBlank() ?: href.orBlank() ?: epochToInstant(timestamp)?.toString()

private fun String?.orBlank(): String? = this?.takeIf { it.isNotBlank() }

private fun List<String>.joinOrNull(): String? = takeIf { it.isNotEmpty() }?.joinToString(", ")
