package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.model.Provenance
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Instant

/**
 * The parts every rendered Tool output shares: a Provenance becomes its citation marker through the question's
 * [citations], never a file path; a time is written in the Library's zone, the zone the Ask screen shows it in.
 */
internal class JsonMarks(
    private val citations: CitationIndex,
    private val timeZone: TimeZone,
) {
    fun records(records: List<RecordRef>): JsonArray =
        buildJsonArray {
            records.forEach { record ->
                add(
                    buildJsonObject {
                        put("cite", marker(record.provenance))
                        when (record) {
                            is RecordRef.OfEvent -> {
                                put("kind", record.kind.name)
                                put("time", local(record.time))
                            }

                            is RecordRef.OfClaim -> {
                                put("kind", record.kind.name)
                                put("asOf", record.asOf?.let(::local))
                            }
                        }
                        putJsonArray("entities") { record.entities.forEach { add(JsonPrimitive(it.displayName)) } }
                    },
                )
            }
        }

    fun entity(entity: EntityRef): JsonObject =
        buildJsonObject {
            put("id", entity.id)
            put("name", entity.displayName)
            put("source", entity.source.name)
            put("kind", entity.kind.name)
        }

    fun cited(provenances: List<Provenance>): JsonElement =
        buildJsonArray { provenances.distinct().forEach { add(JsonPrimitive(marker(it))) } }

    fun marker(provenance: Provenance): String = "[${citations.markerOf(provenance)}]"

    fun local(instant: Instant): String = instant.toLocalDateTime(timeZone).toString()
}
