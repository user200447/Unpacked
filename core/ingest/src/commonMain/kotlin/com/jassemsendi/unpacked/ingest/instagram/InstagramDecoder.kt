package com.jassemsendi.unpacked.ingest.instagram

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Instant

internal class UnsupportedFileShapeException(
    message: String,
) : RuntimeException(message)

/** What every dialect's record can tell: its label/value pairs and when it happened, if the Source said. */
internal interface DialectRecord {
    fun fields(): Map<String, String>

    fun time(): Instant?

    /** The lists of plain values the record carries, by label, for a mapper that needs one Claim per item. */
    fun lists(): Map<String, List<String>> = emptyMap()
}

/**
 * One record of any Instagram dialect after decoding and flattening: its label/value pairs, its
 * lists kept apart, and its time. Every value has had its mojibake repaired.
 */
internal data class InstagramRecord(
    val fields: Map<String, String>,
    val time: Instant?,
    val lists: Map<String, List<String>> = emptyMap(),
)

/**
 * Decodes one file's text into records. A file is a list of records, one record object, or an
 * object with a single key holding the list; each record is in the `label_values` dialect, the
 * `string_list_data` / `string_map_data` dialect or the media-list dialect, recognised record by record.
 */
internal object InstagramDecoder {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): Result<List<InstagramRecord>> =
        runCatching { records(json.parseToJsonElement(text)).map(::decodeRecord) }

    private fun records(element: JsonElement): List<JsonObject> =
        when (element) {
            is JsonArray -> {
                element.map { it as? JsonObject ?: unsupported("expected a list of record objects") }
            }

            is JsonObject -> {
                when {
                    element.isRecord() -> listOf(element)
                    element.size == 1 -> records(element.values.single())
                    else -> unsupported("object is neither a record nor a list of records")
                }
            }

            else -> {
                unsupported("expected a list of records or one record object")
            }
        }

    private fun JsonObject.isRecord(): Boolean = DIALECT_KEYS.any { it in this }

    private fun decodeRecord(element: JsonObject): InstagramRecord {
        val record: DialectRecord =
            when {
                LABEL_VALUES in element -> {
                    json.decodeFromJsonElement<LabelValueRecord>(element)
                }

                STRING_MAP_DATA in element || STRING_LIST_DATA in element -> {
                    json.decodeFromJsonElement<StringDataRecord>(element)
                }

                URI in element -> {
                    json.decodeFromJsonElement<MediaRecord>(element)
                }

                else -> {
                    unsupported("record has none of ${DIALECT_KEYS.joinToString()}")
                }
            }
        return InstagramRecord(
            fields = record.fields().mapValues { (_, value) -> repairMojibake(value) },
            time = record.time(),
            lists = record.lists().mapValues { (_, values) -> values.map(::repairMojibake) },
        )
    }

    private fun unsupported(message: String): Nothing = throw UnsupportedFileShapeException(message)

    private const val LABEL_VALUES = "label_values"
    private const val STRING_LIST_DATA = "string_list_data"
    private const val STRING_MAP_DATA = "string_map_data"
    private const val URI = "uri"
    private val DIALECT_KEYS = listOf(LABEL_VALUES, STRING_LIST_DATA, STRING_MAP_DATA, URI)
}
