package com.jassemsendi.unpacked.ingest.instagram

/** Flattened field keys nest a group's label and a leaf's label with this separator: `Owner / Name`. */
internal const val FIELD_SEPARATOR = " / "

internal fun fieldPath(
    prefix: String,
    name: String,
): String = "$prefix$FIELD_SEPARATOR$name"

internal fun String?.orBlank(): String? = this?.takeIf { it.isNotBlank() }

/**
 * The text one leaf shows, whichever of its slots Instagram filled: the value, else the link, else
 * a dated leaf's instant in ISO-8601. Null when the leaf is empty.
 */
internal fun fieldText(
    value: String?,
    href: String?,
    timestamp: Long?,
): String? = value.orBlank() ?: href.orBlank() ?: epochToInstant(timestamp)?.toString()
