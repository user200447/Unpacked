package com.jassemsendi.unpacked.fixtures

/** Builders for JSON text in the `string_list_data` / `string_map_data` dialect of a real Instagram Export. */
internal object InstagramStringDataJson {
    fun stringList(
        title: String,
        value: String?,
        href: String,
        timestamp: Long,
    ): String {
        val valueField = value?.let { """"value": "$it", """ } ?: ""
        return """{"title": "$title", "media_list_data": [], """ +
            """"string_list_data": [{"href": "$href", $valueField"timestamp": $timestamp}]}"""
    }

    fun stringMap(
        title: String?,
        vararg entries: String,
    ): String {
        val titleField = title?.let { """"title": "$it", "media_map_data": {}, """ } ?: ""
        return """{$titleField"string_map_data": {${entries.joinToString(", ")}}}"""
    }

    fun entry(
        label: String,
        value: String = "",
        href: String = "",
        timestamp: Long = 0,
    ): String = """"$label": {"href": "$href", "value": "$value", "timestamp": $timestamp}"""
}
