package com.jassemsendi.unpacked.ingest.instagram

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

internal class UnsupportedFileShapeException(
    message: String,
) : RuntimeException(message)

/** Decodes one file's text into `label_values` records. */
internal object LabelValuesDecoder {
    private val json = Json { ignoreUnknownKeys = true }

    /** A file is either a list of records or one record object; anything else is not this dialect. */
    fun decode(text: String): Result<List<LabelValueRecord>> =
        runCatching {
            when (val element = json.parseToJsonElement(text)) {
                is JsonArray -> {
                    element.map { json.decodeFromJsonElement<LabelValueRecord>(it) }
                }

                is JsonObject -> {
                    if ("label_values" !in element) throw UnsupportedFileShapeException("object has no label_values")
                    listOf(json.decodeFromJsonElement<LabelValueRecord>(element))
                }

                else -> {
                    throw UnsupportedFileShapeException("expected a list of records or one record object")
                }
            }
        }
}
