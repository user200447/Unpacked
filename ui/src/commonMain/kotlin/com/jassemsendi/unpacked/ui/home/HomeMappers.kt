package com.jassemsendi.unpacked.ui.home

import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.ui.time.toDeviceMinute
import com.jassemsendi.unpacked.ui.time.toDeviceSecond
import kotlinx.datetime.TimeZone

private val upperCase = Regex("(?<=[a-z])(?=[A-Z])")

/** "ContentViewed" reads as "Content viewed": the Kind's name split at its capitals. */
fun Enum<*>.label(): String =
    name
        .split(upperCase)
        .joinToString(" ") {
            it.lowercase()
        }.replaceFirstChar { it.uppercase() }

fun <K : Enum<K>> K.toUi(count: Int): KindCountUi<K> = KindCountUi(this, label(), count)

/** "1 Event", "2 Events": a count with its noun, for the lines that count things. */
fun counted(
    count: Int,
    noun: String,
): String = if (count == 1) "$count $noun" else "$count ${noun}s"

fun Event.toEntry(timeZone: TimeZone): TimelineEntryUi =
    TimelineEntryUi(
        ref = RecordRef.Event(provenance),
        kindLabel = kind.label(),
        time = time.toDeviceMinute(timeZone),
        summary = summaryOf(entities, attributes),
        rawKind = rawKind.value,
    )

fun Event.toDetail(timeZone: TimeZone): RecordDetailUi =
    RecordDetailUi(
        ref = RecordRef.Event(provenance),
        kindLabel = kind.label(),
        time = RecordTimeUi.Happened(time.toDeviceSecond(timeZone)),
        rawKind = rawKind.value,
        attributes = attributes.toAttributes(),
        entities = entities.names(),
    )

fun Claim.toDetail(timeZone: TimeZone): RecordDetailUi =
    RecordDetailUi(
        ref = RecordRef.Claim(provenance),
        kindLabel = kind.label(),
        time = asOf?.let { RecordTimeUi.LastUpdated(it.toDeviceSecond(timeZone)) } ?: RecordTimeUi.Undated,
        rawKind = rawKind.value,
        attributes = attributes.toAttributes(),
        entities = entities.names(),
    )

/** What a record concerned, in one line: the Entities it names, or failing that its first recorded value. */
fun summaryOf(
    entities: List<Entity>,
    attributes: Map<String, String>,
): String =
    entities.joinToString { it.displayName }.ifEmpty {
        attributes.values.firstOrNull { it.isNotBlank() } ?: ""
    }

/** Label and value pairs in the order the Record listed them. */
fun Map<String, String>.toAttributes(): List<AttributeUi> = map { (label, value) -> AttributeUi(label, value) }

private fun List<Entity>.names(): List<String> = map { it.displayName }
