package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.data.EventFilter
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import com.jassemsendi.unpacked.model.andThen
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

// What a Tool accepts as written by a model: names and dates as text, counts as numbers. Each is checked here
// and answered with a ToolError, never an exception, so the model can be told what it got wrong and try again.

/** The names a Tool accepts for an Event Kind, as the model must write them. */
public val EVENT_KIND_NAMES: List<String> = EventKind.entries.map { it.name }

/** The names a Tool accepts for an Entity Kind. */
public val ENTITY_KIND_NAMES: List<String> = EntityKind.entries.map { it.name }

/** The names a Tool accepts for a Source: each one's name and its id. */
public val SOURCE_NAMES: List<String> = Source.entries.flatMap { listOf(it.name, it.id) }

/** The names a Tool accepts for how a count is spread over time. */
public val GROUPING_NAMES: List<String> = Grouping.entries.map { it.name }

internal fun eventKindOf(given: String): Result<EventKind, ToolError> =
    EventKind.entries.named(given)?.let { Result.Success(it) } ?: Result.Error(ToolError.UnknownKind(given))

internal fun entityKindOf(given: String): Result<EntityKind, ToolError> =
    EntityKind.entries.named(given)?.let { Result.Success(it) } ?: Result.Error(ToolError.UnknownEntityKind(given))

internal fun sourceOf(given: String): Result<Source, ToolError> =
    (Source.entries.named(given) ?: Source.entries.find { it.id.equals(given.trim(), ignoreCase = true) })
        ?.let { Result.Success(it) }
        ?: Result.Error(ToolError.UnknownSource(given))

internal fun groupingOf(given: String): Result<Grouping, ToolError> =
    Grouping.entries.named(given)?.let { Result.Success(it) } ?: Result.Error(ToolError.UnknownGrouping(given))

private fun <E : Enum<E>> List<E>.named(given: String): E? = find { it.name.equals(given.trim(), ignoreCase = true) }

/** A calendar day written `YYYY-MM-DD`; null stays null, so that an end of a range can be left open. */
internal fun dateOf(given: String?): Result<LocalDate?, ToolError> {
    if (given == null) return Result.Success(null)
    val parsed = runCatching { LocalDate.parse(given.trim()) }.getOrNull()
    return parsed?.let { Result.Success(it) } ?: Result.Error(ToolError.InvalidDate(given))
}

/** Both days parsed and in order; either may be left open. */
internal fun dateRangeOf(
    from: String?,
    to: String?,
): Result<DateRange, ToolError> =
    dateOf(from).andThen { first ->
        dateOf(to).andThen { last ->
            if (first != null && last != null && first > last) {
                Result.Error(ToolError.InvertedRange(first, last))
            } else {
                Result.Success(DateRange(first, last))
            }
        }
    }

/**
 * The days as instants in [timeZone], from the first day's start to the last millisecond of the last day, the
 * Library's finest grain; an open end reaches as far as time goes. Null when neither end was given. Public so
 * that a test reading the Library for the value a Tool must report reads the same days the Tool does.
 */
public fun DateRange.toInstants(timeZone: TimeZone): ClosedRange<Instant>? {
    if (from == null && to == null) return null
    val start = from?.atStartOfDayIn(timeZone) ?: Instant.DISTANT_PAST
    val end = to?.plus(1, DateTimeUnit.DAY)?.atStartOfDayIn(timeZone)?.minus(1.milliseconds) ?: Instant.DISTANT_FUTURE
    return start..end
}

internal fun countOf(
    given: Int,
    allowed: IntRange,
): Result<Int, ToolError> =
    if (given in allowed) Result.Success(given) else Result.Error(ToolError.CountOutOfRange(given, allowed))

/** The filter a Tool reads Events through, its days turned into instants of the device's zone. */
internal fun eventFilterOf(
    kind: EventKind?,
    range: DateRange,
    source: Source?,
    timeZone: TimeZone,
): EventFilter = EventFilter(kind = kind, range = range.toInstants(timeZone), source = source)
