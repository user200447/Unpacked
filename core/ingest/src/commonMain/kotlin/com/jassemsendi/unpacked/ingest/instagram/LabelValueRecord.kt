package com.jassemsendi.unpacked.ingest.instagram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlin.time.Instant

/** One record of the `label_values` dialect: a list of labelled values, usually with a timestamp. */
@Serializable
internal data class LabelValueRecord(
    val timestamp: Long? = null,
    @SerialName("label_values") val labelValues: List<LabelValue> = emptyList(),
) : DialectRecord {
    override fun time(): Instant? = epochToInstant(timestamp)

    override fun fields(): Map<String, String> = flatten()

    override fun lists(): Map<String, List<String>> = topLevelLists()
}

/**
 * One labelled value. A leaf has `label` and `value` (or `href`, or a `timestamp_value`); a group
 * has `title` and `dict`; a list has `label` and `vec`. `vec` and `dict` are null when absent, so
 * a leaf that is only a label can be told from an empty group; a media leaf is read only far enough
 * to tell it from one. Anything Instagram adds later is ignored.
 */
@Serializable
internal data class LabelValue(
    val label: String? = null,
    val title: String? = null,
    val value: String? = null,
    val href: String? = null,
    @SerialName("timestamp_value") val timestampValue: Long? = null,
    val vec: List<LabelValue>? = null,
    val dict: List<LabelValue>? = null,
    val media: JsonArray? = null,
)
