package com.jassemsendi.unpacked.ingest.instagram

/**
 * Flattens the nested label/value tree into `"Group / Label" -> value` pairs. Anonymous children
 * append to their parent's key, so a `vec` of values becomes one comma-joined string. A leaf that
 * carries only a timestamp reads as an ISO-8601 instant. A leaf that is a label and nothing else,
 * as a consent is written, is kept with an empty value, because the label is then the statement.
 * Every other empty value is dropped.
 */
internal fun LabelValueRecord.flatten(): Map<String, String> =
    LinkedHashMap<String, String>().also { flattenInto(it, prefix = "", items = labelValues) }

/**
 * The top-level lists of plain values, by label, kept apart from the joined text in [flatten]
 * because a value may itself contain the separator: one Claim per item needs the items.
 */
internal fun LabelValueRecord.topLevelLists(): Map<String, List<String>> =
    labelValues
        .filter { it.vec != null }
        .associate { item ->
            val values = item.vec.orEmpty().mapNotNull { leaf -> fieldText(leaf.value, leaf.href, leaf.timestampValue) }
            item.name() to values
        }.filterValues { it.isNotEmpty() }

private fun LabelValue.name(): String = label.orBlank() ?: title.orBlank() ?: ""

private fun LabelValue.isLabelOnly(): Boolean =
    label != null && value == null && href == null && timestampValue == null && vec == null && dict == null &&
        media == null

private fun flattenInto(
    out: MutableMap<String, String>,
    prefix: String,
    items: List<LabelValue>,
) {
    for (item in items) {
        val name = item.name()
        val key =
            when {
                prefix.isEmpty() -> name
                name.isEmpty() -> prefix
                else -> fieldPath(prefix, name)
            }
        val scalar = fieldText(item.value, item.href, item.timestampValue) ?: "".takeIf { item.isLabelOnly() }
        if (scalar != null && key.isNotEmpty()) out.append(key, scalar)
        flattenInto(out, key, item.vec.orEmpty())
        flattenInto(out, key, item.dict.orEmpty())
    }
}

/** Joins repeated keys; an empty value never adds a separator and never hides a real one. */
private fun MutableMap<String, String>.append(
    key: String,
    value: String,
) {
    val existing = this[key]
    this[key] =
        when {
            existing == null -> value
            value.isEmpty() -> existing
            existing.isEmpty() -> value
            else -> "$existing, $value"
        }
}
