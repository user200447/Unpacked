package com.jassemsendi.unpacked.fixtures

/** Builders for JSON text in the shapes of a real Instagram Export, one per container and leaf shape. */
internal object InstagramJson {
    /** A file that is a list of records. */
    fun list(vararg records: String): String = records.joinToString(",\n", prefix = "[\n", postfix = "\n]")

    /** A file that is an object with one key holding the list of records. */
    fun container(
        key: String,
        vararg records: String,
    ): String = """{"$key": ${list(*records)}}"""

    // label_values dialect

    /** A record of [items]; an empty item is left out, so an optional leaf can be passed as "". */
    fun labelValues(
        timestamp: Long,
        vararg items: String,
    ): String {
        val present = items.filter { it.isNotEmpty() }.joinToString(", ")
        return """{"timestamp": $timestamp, "media": [], "label_values": [$present], "fbid": "synthetic"}"""
    }

    fun leaf(
        label: String,
        value: String,
        href: String? = null,
    ): String =
        href?.let { """{"label": "$label", "value": "$value", "href": "$it"}""" }
            ?: """{"label": "$label", "value": "$value"}"""

    fun timed(
        label: String,
        timestamp: Long,
    ): String = """{"label": "$label", "timestamp_value": $timestamp}"""

    /** A titled group whose one anonymous child holds [inner], as Instagram nests `Owner` and `Media`. */
    fun group(
        title: String,
        vararg inner: String,
    ): String = """{"title": "$title", "dict": [{"title": "", "dict": [${inner.joinToString(", ")}]}]}"""

    fun hashtags(names: List<String>): String =
        names.joinToString(", ", prefix = """{"title": "Hashtags", "dict": [""", postfix = "]}") {
            """{"title": "", "dict": [${leaf("Name", it)}]}"""
        }

    // string_list_data / string_map_data dialect

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
