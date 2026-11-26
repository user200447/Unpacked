package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.ingest.instagram.fieldPath
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The reading of last resort for the Explorer: any JSON as Records, knowing nothing of a Source's dialects. A
 * top-level array is one Record per element; anything else is one Record. Nested objects flatten to
 * `"parent / child"` keys, a list of plain values joins with commas, a list of objects numbers its items, and
 * nulls are left out. What a Source's own reading cannot shape still shows this way, so nothing is hidden.
 */
internal object JsonRecords {
    /** The Records of [text], or the parse failure when it is not JSON. */
    fun read(text: String): kotlin.Result<List<Record>> = runCatching { records(Json.parseToJsonElement(text)) }

    /** What the Explorer shows when the Source could not read [text] for [reason]: the plain reading, or no Records. */
    fun fallback(
        text: String,
        reason: String?,
    ): FileRecords = FileRecords(read(text).getOrDefault(emptyList()), reason ?: "could not read the file")

    /** One JSON value's label and value pairs, in document order. */
    fun flatten(element: JsonElement): Map<String, String> =
        LinkedHashMap<String, String>().also { flattenInto(it, prefix = "", element) }

    private fun records(element: JsonElement): List<Record> =
        when (element) {
            is JsonArray -> element.mapIndexed { index, item -> Record(index, flatten(item)) }
            else -> listOf(Record(0, flatten(element)))
        }

    private fun flattenInto(
        out: MutableMap<String, String>,
        prefix: String,
        element: JsonElement,
    ) {
        when (element) {
            is JsonObject -> {
                element.forEach { (name, value) -> flattenInto(out, prefix.then(name), value) }
            }

            is JsonArray -> {
                val plain = element.filterIsInstance<JsonPrimitive>().filterNot { it is JsonNull }
                if (plain.size == element.size) {
                    if (plain.isNotEmpty()) out[prefix.orValue()] = plain.joinToString(", ") { it.content }
                } else {
                    element.forEachIndexed { index, item -> flattenInto(out, prefix.then(index.toString()), item) }
                }
            }

            is JsonPrimitive -> {
                if (element !is JsonNull) out[prefix.orValue()] = element.content
            }
        }
    }

    private fun String.then(name: String): String = if (isEmpty()) name else fieldPath(this, name)

    /** A bare value at the top of a file has no label; it is shown under this one. */
    private fun String.orValue(): String = ifEmpty { "Value" }
}
