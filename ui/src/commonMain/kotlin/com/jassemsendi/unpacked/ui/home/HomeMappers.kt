package com.jassemsendi.unpacked.ui.home

import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
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

fun EventKind.toUi(count: Int): KindCountUi = KindCountUi(this, label(), count)

fun Event.toEntry(timeZone: TimeZone): TimelineEntryUi =
    TimelineEntryUi(
        ref = RecordRef.Event(provenance),
        kindLabel = kind.label(),
        time = time.toDeviceMinute(timeZone),
        summary =
            entities.joinToString { it.displayName }.ifEmpty {
                attributes.values.firstOrNull { it.isNotBlank() }
                    ?: ""
            },
        rawKind = rawKind.value,
    )

fun Event.toDetail(timeZone: TimeZone): RecordDetailUi =
    RecordDetailUi(
        ref = RecordRef.Event(provenance),
        kindLabel = kind.label(),
        time = RecordTimeUi.Happened(time.toDeviceSecond(timeZone)),
        rawKind = rawKind.value,
        attributes = attributes.toUi(),
        entities = entities.names(),
    )

fun Claim.toDetail(timeZone: TimeZone): RecordDetailUi =
    RecordDetailUi(
        ref = RecordRef.Claim(provenance),
        kindLabel = kind.label(),
        time = asOf?.let { RecordTimeUi.LastUpdated(it.toDeviceSecond(timeZone)) } ?: RecordTimeUi.Undated,
        rawKind = rawKind.value,
        attributes = attributes.toUi(),
        entities = entities.names(),
    )

/** Label and value pairs in the order the Record listed them. */
private fun Map<String, String>.toUi(): List<AttributeUi> = map { (label, value) -> AttributeUi(label, value) }

private fun List<Entity>.names(): List<String> = map { it.displayName }
