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

private const val MILLISECONDS_THRESHOLD = 100_000_000_000L

/** The record's time, or null when the Source gave none (a zero timestamp means unknown). */
internal fun LabelValueRecord.time(): Instant? =
    timestamp?.takeIf { it > 0 }?.let { value ->
        if (value > MILLISECONDS_THRESHOLD) Instant.fromEpochMilliseconds(value) else Instant.fromEpochSeconds(value)
    }
