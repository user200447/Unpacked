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

    /**
     * A record of [items]; an empty item is left out, so an optional leaf can be passed as "".
     * A null [timestamp] makes a record with no time of its own, as the files that state standing facts are.
     */
    fun labelValues(
        timestamp: Long?,
        vararg items: String,
    ): String {
        val present = items.filter { it.isNotEmpty() }.joinToString(", ")
        val timestampField = timestamp?.let { """"timestamp": $it, """ } ?: ""
        return """{$timestampField"media": [], "label_values": [$present], "fbid": "synthetic"}"""
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

    /** A leaf that is a label and nothing else, as a consent or an unset setting is written. */
    fun labelOnly(label: String): String = """{"label": "$label"}"""

    /** A labelled list of plain values, as the files that list advertisers or locations are made of. */
    fun vec(
        label: String,
        values: List<String>,
    ): String =
        values.joinToString(", ", prefix = """{"label": "$label", "vec": [""", postfix = "]}") {
            """{"value": "$it"}"""
        }

    /** A labelled group of leaves, as `Location` nests its country, region and city. */
    fun dict(
        label: String,
        vararg inner: String,
    ): String = """{"label": "$label", "dict": [${inner.joinToString(", ")}]}"""

    /** A titled group whose one anonymous child holds [inner], as Instagram nests `Owner` and `Media`. */
    fun group(
        title: String,
        vararg inner: String,
    ): String = """{"title": "$title", "dict": [{"title": "", "dict": [${inner.joinToString(", ")}]}]}"""

    fun hashtags(names: List<String>): String =
        names.joinToString(", ", prefix = """{"title": "Hashtags", "dict": [""", postfix = "]}") {
            """{"title": "", "dict": [${leaf("Name", it)}]}"""
        }
}
