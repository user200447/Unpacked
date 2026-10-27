package com.jassemsendi.unpacked.ingest.instagram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** One record of the `label_values` dialect: a list of labelled values, usually with a timestamp. */
@Serializable
internal data class LabelValueRecord(
    val timestamp: Long? = null,
    @SerialName("label_values") val labelValues: List<LabelValue> = emptyList(),
)

/**
 * One labelled value. A leaf has `label` and `value` (or `href`, or a `timestamp_value`); a group
 * has `title` and `dict`; a list has `label` and `vec`. Media leaves and anything Instagram adds
 * later are ignored.
 */
@Serializable
internal data class LabelValue(
    val label: String? = null,
    val title: String? = null,
    val value: String? = null,
    val href: String? = null,
    @SerialName("timestamp_value") val timestampValue: Long? = null,
    val vec: List<LabelValue> = emptyList(),
    val dict: List<LabelValue> = emptyList(),
)

/** The record's time, or null when the Source gave none. */
internal fun LabelValueRecord.time(): Instant? = epochToInstant(timestamp)
