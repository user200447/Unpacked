package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EntityRepository
import com.jassemsendi.unpacked.data.EntityTally
import com.jassemsendi.unpacked.data.EventFilter
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.data.ImportRepository
import com.jassemsendi.unpacked.data.SearchHit
import com.jassemsendi.unpacked.data.SearchRepository
import com.jassemsendi.unpacked.insights.DeclaredEvidenceRules
import com.jassemsendi.unpacked.insights.EvidenceRules
import com.jassemsendi.unpacked.insights.GapReportReader
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import com.jassemsendi.unpacked.model.andThen
import com.jassemsendi.unpacked.model.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Instant

/**
 * The eight Tools (glossary: Tool): every typed, read-only question the agent may ask the Library, as plain
 * Kotlin over the repository interfaces, so that they run and are tested with no model. Inputs are what a
 * model writes, names and dates as text, and each is validated into a [ToolError] rather than thrown. Every
 * output cites the Provenance of the records it summarises; a Library that cannot be read is
 * [ToolError.Library]. Times are read in [timeZone], the device's.
 */
public class LibraryTools(
    private val imports: ImportRepository,
    private val events: EventRepository,
    private val claims: ClaimRepository,
    private val entities: EntityRepository,
    private val search: SearchRepository,
    private val timeZone: TimeZone,
    /** The Evidence rules the Gap report applies: each Source's own unless a test hands in others. */
    rules: EvidenceRules = DeclaredEvidenceRules,
    private val citedCount: Int = CITED_COUNT,
) {
    private val gapReports = GapReportReader(imports, events, claims, rules, citedCount)

    /** Every Import, oldest first, so the agent knows what the Library holds and which Import a Gap report is for. */
    public suspend fun listImports(): Result<ImportsListed, ToolError> =
        imports.all().orLibraryError().map { all -> ImportsListed(all.map(ImportRef::of)) }

    /**
     * How many Events there are: of [kind] (every Kind when null), from [from] to [to] as `YYYY-MM-DD` days in the
     * device's zone (either end open when null), from [source] (every Source when null); spread per day or month
     * when [groupBy] names a [Grouping]. Cites the newest few Events counted.
     */
    public suspend fun countEvents(
        kind: String? = null,
        from: String? = null,
        to: String? = null,
        source: String? = null,
        groupBy: String? = null,
    ): Result<EventsCounted, ToolError> =
        optional(kind, ::eventKindOf).andThen { eventKind ->
            dateRangeOf(from, to).andThen { range ->
                optional(source, ::sourceOf).andThen { eventSource ->
                    optional(groupBy, ::groupingOf).andThen { grouping ->
                        val filter = eventFilterOf(eventKind, range, eventSource, timeZone)
                        counted(filter, grouping).andThen { counted ->
                            events.citedBy(filter, citedCount).map { cited ->
                                EventsCounted(
                                    eventKind,
                                    range,
                                    eventSource,
                                    counted.total,
                                    grouping,
                                    counted.series,
                                    cited,
                                )
                            }
                        }
                    }
                }
            }
        }

    /**
     * The [count] Entities of [entityKind] that Events of [kind] name most often between [from] and [to], most first,
     * and how many such Entities there are in all. Each is cited by the newest few of those Events naming it.
     */
    public suspend fun topEntities(
        kind: String,
        entityKind: String,
        count: Int = TOP_COUNT,
        from: String? = null,
        to: String? = null,
    ): Result<TopEntities, ToolError> =
        eventKindOf(kind).andThen { eventKind ->
            entityKindOf(entityKind).andThen { namedKind ->
                countOf(count, TOP_RANGE).andThen { limit ->
                    dateRangeOf(from, to).andThen { range ->
                        val filter = eventFilterOf(eventKind, range, source = null, timeZone)
                        events.topEntities(filter, namedKind, limit).orLibraryError().andThen { tallies ->
                            events.ranked(filter, tallies.top, citedCount).map { top ->
                                TopEntities(eventKind, namedKind, range, tallies.count, top)
                            }
                        }
                    }
                }
            }
        }

    /** One Entity by its id: how often it is named, its newest [limit] Events and every Claim naming it. */
    public suspend fun entityTimeline(
        entityId: String,
        limit: Int = PAGE_SIZE,
    ): Result<EntityTimeline, ToolError> =
        countOf(limit, PAGE_RANGE).andThen { pageSize ->
            entities.counts(entityId).orLibraryError { ToolError.UnknownEntity(entityId) }.andThen { counts ->
                events.forEntity(entityId, pageSize, offset = 0).orLibraryError().andThen { named ->
                    claims.forEntity(entityId).orLibraryError().map { held ->
                        EntityTimeline(
                            entity = EntityRef.of(counts.entity),
                            eventCount = counts.eventCount,
                            claimCount = counts.claimCount,
                            events = named.map(RecordRef::of),
                            claims = held.map(RecordRef::of),
                        )
                    }
                }
            }
        }

    /** The newest [limit] Events and Claims whose Records or Entity names contain every word of [text]. */
    public suspend fun searchText(
        text: String,
        limit: Int = PAGE_SIZE,
    ): Result<TextFound, ToolError> {
        val words = text.trim()
        if (words.isEmpty()) return Result.Error(ToolError.BlankText)
        return countOf(limit, PAGE_RANGE).andThen { pageSize ->
            search.search(words, pageSize, offset = 0).orLibraryError().map { hits ->
                TextFound(
                    words,
                    hits.map { hit ->
                        when (hit) {
                            is SearchHit.OfEvent -> RecordRef.of(hit.event)
                            is SearchHit.OfClaim -> RecordRef.of(hit.claim)
                        }
                    },
                )
            }
        }
    }

    /** Every Match among the Library's Entities of [kind], computed now from their names and never stored (ADR 0002). */
    public suspend fun findMatches(kind: String): Result<MatchesFound, ToolError> =
        entityKindOf(kind).andThen { entityKind ->
            entities.all().orLibraryError().map { all ->
                MatchesFound(entityKind, matchesAmong(all.filter { it.kind == entityKind }))
            }
        }

    /** The Gap report for the Import with [importId], as the Gap report screen computes it. */
    public suspend fun gapReport(importId: Long): Result<GapReported, ToolError> =
        gapReports.read(ImportId(importId)).orLibraryError { ToolError.UnknownImport(importId) }.map(::GapReported)

    /** A chart described from a Tool output that has the shape of one; see [chartSpecOf]. */
    public fun chartSpec(output: ToolOutput): Result<ChartSpec, ToolError> = chartSpecOf(output)

    /** The total alone when no grouping is asked for; otherwise every time, spread, with the total being their number. */
    private suspend fun counted(
        filter: EventFilter,
        grouping: Grouping?,
    ): Result<Counted, ToolError> =
        if (grouping == null) {
            events.count(filter).orLibraryError().map { Counted(it, series = null) }
        } else {
            events.times(filter).orLibraryError().map { times -> Counted(times.size, times.spread(grouping, timeZone)) }
        }

    public companion object {
        /** How many records a Tool cites for a number it did not list in full, as the Gap report does. */
        public const val CITED_COUNT: Int = GapReportReader.CITED_COUNT

        /** How many Entities topEntities ranks unless asked otherwise, and the most it will. */
        public const val TOP_COUNT: Int = 5
        public val TOP_RANGE: IntRange = 1..50

        /** How many records a listing Tool returns unless asked otherwise, and the most it will. */
        public const val PAGE_SIZE: Int = 20
        public val PAGE_RANGE: IntRange = 1..100
    }
}

/** What a count read: the total, and the series it is spread into when a grouping was asked for. */
private data class Counted(
    val total: Int,
    val series: List<SeriesPoint>?,
)

/** One point per day or month from the first to the last with an Event, quiet ones at zero; none when there is none. */
private fun List<Instant>.spread(
    grouping: Grouping,
    timeZone: TimeZone,
): List<SeriesPoint> {
    if (isEmpty()) return emptyList()
    val days = map { it.toLocalDateTime(timeZone).date }
    return when (grouping) {
        Grouping.Day -> {
            val counts = days.groupingBy { it }.eachCount()
            (days.min()..days.max()).map { day -> SeriesPoint(day, counts[day] ?: 0) }
        }

        Grouping.Month -> {
            val counts = days.groupingBy { it.yearMonth }.eachCount()
            (counts.keys.min()..counts.keys.max()).map { month -> SeriesPoint(month.firstDay, counts[month] ?: 0) }
        }
    }
}

/** Each tally with the newest few Events of the filter naming its Entity, the first failure ending the read. */
private suspend fun EventRepository.ranked(
    filter: EventFilter,
    top: List<EntityTally>,
    citedCount: Int,
): Result<List<RankedEntity>, ToolError> {
    val ranked = ArrayList<RankedEntity>(top.size)
    for (tally in top) {
        when (val cited = citedBy(filter.copy(entityId = tally.entity.id), citedCount)) {
            is Result.Error -> return cited
            is Result.Success -> ranked += RankedEntity(EntityRef.of(tally.entity), tally.count, cited.data)
        }
    }
    return Result.Success(ranked)
}

/** The Provenance of the newest [citedCount] Events the filter keeps: what a count cites. */
private suspend fun EventRepository.citedBy(
    filter: EventFilter,
    citedCount: Int,
): Result<List<Provenance>, ToolError> =
    newest(filter, citedCount).orLibraryError().map { newest -> newest.map { it.provenance } }

/** A name the model may leave out: null passes through, anything else is checked. */
private inline fun <T> optional(
    given: String?,
    parse: (String) -> Result<T, ToolError>,
): Result<T?, ToolError> = if (given == null) Result.Success(null) else parse(given)

/** A Library that could not be read is the Tool's failure; [notFound] names what an absent row means here. */
private inline fun <T> Result<T, DataError.Local>.orLibraryError(
    notFound: () -> ToolError = { ToolError.Library(DataError.Local.NOT_FOUND) },
): Result<T, ToolError> =
    when (this) {
        is Result.Success -> {
            this
        }

        is Result.Error -> {
            Result.Error(
                if (error ==
                    DataError.Local.NOT_FOUND
                ) {
                    notFound()
                } else {
                    ToolError.Library(error)
                },
            )
        }
    }
