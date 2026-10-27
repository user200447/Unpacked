package com.jassemsendi.unpacked.ingest.instagram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Instant

/** One record of the `label_values` dialect: a list of labelled values, usually with a timestamp. */
@Serializable
internal data class LabelValueRecord(
    val timestamp: Long? = null,
    @SerialName("label_values") val labelValues: List<LabelValue> = emptyList(),
    val fbid: String? = null,
    val title: String? = null,
)

/**
 * One labelled value. A leaf has `label` and `value` (or `href`); a group has `title` and `dict`;
 * a list has `label` and `vec`. Anything Instagram adds later is ignored.
 */
@Serializable
internal data class LabelValue(
    val label: String? = null,
    val title: String? = null,
    val value: String? = null,
    val href: String? = null,
    val vec: List<LabelValue> = emptyList(),
    val dict: List<LabelValue> = emptyList(),
)

internal class UnsupportedFileShapeException(
    message: String,
) : RuntimeException(message)

internal object LabelValues {
    private val json = Json { ignoreUnknownKeys = true }

    /** A file is either a list of records or one record object; anything else is not this dialect. */
    fun decode(text: String): Result<List<LabelValueRecord>> =
        runCatching {
            when (val element = json.parseToJsonElement(text)) {
                is JsonArray -> {
                    element.map { json.decodeFromJsonElement<LabelValueRecord>(it) }
                }

                is JsonObject -> {
                    if ("label_values" !in element) throw UnsupportedFileShapeException("object has no label_values")
                    listOf(json.decodeFromJsonElement<LabelValueRecord>(element))
                }

                else -> {
                    throw UnsupportedFileShapeException("expected a list of records or one record object")
                }
            }
        }
}

private const val MILLISECONDS_THRESHOLD = 100_000_000_000L

/** The record's time, or null when the Source gave none (a zero timestamp means unknown). */
internal fun LabelValueRecord.time(): Instant? =
    timestamp?.takeIf { it > 0 }?.let { value ->
        if (value > MILLISECONDS_THRESHOLD) Instant.fromEpochMilliseconds(value) else Instant.fromEpochSeconds(value)
    }

/**
 * Flattens the nested label/value tree into `"Group / Label" -> value` pairs. Anonymous children
 * append to their parent's key, so a `vec` of values becomes one comma-joined string. Empty values
 * are dropped.
 */
internal fun LabelValueRecord.flatten(): Map<String, String> =
    LinkedHashMap<String, String>().also { flattenInto(it, prefix = "", items = labelValues) }

private fun flattenInto(
    out: MutableMap<String, String>,
    prefix: String,
    items: List<LabelValue>,
) {
    for (item in items) {
        val name = item.label.orBlank() ?: item.title.orBlank() ?: ""
        val key =
            when {
                prefix.isEmpty() -> name
                name.isEmpty() -> prefix
                else -> "$prefix / $name"
            }
        val scalar = item.value.orBlank() ?: item.href.orBlank()
        if (scalar != null && key.isNotEmpty()) out.append(key, scalar)
        flattenInto(out, key, item.vec)
        flattenInto(out, key, item.dict)
    }
}

private fun String?.orBlank(): String? = this?.takeIf { it.isNotBlank() }

private fun MutableMap<String, String>.append(
    key: String,
    value: String,
) {
    this[key] = this[key]?.let { existing -> "$existing, $value" } ?: value
}
