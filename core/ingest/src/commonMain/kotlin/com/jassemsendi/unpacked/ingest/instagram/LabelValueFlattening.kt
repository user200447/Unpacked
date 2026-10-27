package com.jassemsendi.unpacked.ingest.instagram

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
