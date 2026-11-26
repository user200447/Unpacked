package com.jassemsendi.unpacked.eval

import com.jassemsendi.unpacked.agent.DateRange
import com.jassemsendi.unpacked.agent.LibraryTools
import com.jassemsendi.unpacked.agent.toInstants
import com.jassemsendi.unpacked.data.EventFilter
import com.jassemsendi.unpacked.fixtures.SyntheticInstagramExport
import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.insights.DeclaredEvidenceRules
import com.jassemsendi.unpacked.insights.GapReportReader
import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.EventKind
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** How many questions the eval asks. */
const val QUESTION_COUNT: Int = 20

/** The share of questions that must pass for the eval to be a go: the Week 5 signal. */
const val TARGET_PERCENT: Int = 70

/** What a question is about; the twenty cover every topic. */
enum class EvalTopic {
    Counts,
    TopEntities,
    TimeRanges,
    Search,
    GapReport,
}

/** What an answer must state to pass, as the fixture says it. */
sealed interface Expected {
    /** The answer's numbers, read as the Citation validator reads them, include this one. */
    data class Count(
        val value: Int,
    ) : Expected

    /** The answer, lowercased and reduced to letters and digits, contains one of these spellings. */
    data class Words(
        val alternatives: List<String>,
    ) : Expected {
        constructor(vararg alternatives: String) : this(alternatives.toList())
    }

    /** Every part is found. */
    data class AllOf(
        val parts: List<Expected>,
    ) : Expected

    /** A calendar day, in any of its usual spellings. */
    data class Day(
        val date: LocalDate,
    ) : Expected
}

/** One question of the eval, with the value the fixture says the answer must state; numbered by its position. */
data class EvalQuestion(
    val topic: EvalTopic,
    val question: String,
    val expected: Expected,
)

/**
 * The twenty questions, declared here and nowhere else, with every expected value derived from the fixture: the
 * generator's own tallies and the Library it was imported into, read in [zone] as the agent reads it.
 */
suspend fun evalQuestions(
    fixture: EvalFixture,
    zone: TimeZone,
): List<EvalQuestion> =
    counts(fixture) + topEntities(fixture) + timeRanges(fixture, zone) + search(fixture) + gapReport(fixture)

private suspend fun counts(fixture: EvalFixture): List<EvalQuestion> {
    val perKind =
        fixture.library.events
            .countByKind()
            .orFail()
    val threads = SyntheticInstagramExport.MESSAGE_THREADS
    return listOf(
        EvalQuestion(EvalTopic.Counts, "How many ads was I shown?", Expected.Count(fixture.export.ads.size)),
        EvalQuestion(EvalTopic.Counts, "How many times did I log in to Instagram?", perKind.counted(EventKind.Login)),
        EvalQuestion(EvalTopic.Counts, "How many follows are recorded in my data?", perKind.counted(EventKind.Follow)),
        EvalQuestion(EvalTopic.Counts, "How many messages did I send?", Expected.Count(threads.sumOf { it.sent })),
        EvalQuestion(
            EvalTopic.Counts,
            "How many messages did I receive?",
            Expected.Count(threads.sumOf { it.received }),
        ),
        // ContentViewed counts posts, videos, stories, suggested profiles and Threads posts alike.
        EvalQuestion(
            EvalTopic.Counts,
            "How many pieces of content did I view?",
            perKind.counted(EventKind.ContentViewed),
        ),
        EvalQuestion(
            EvalTopic.Counts,
            "How many events are in my library in total?",
            Expected.Count(perKind.values.sum()),
        ),
    )
}

private suspend fun topEntities(fixture: EvalFixture): List<EvalQuestion> {
    val tally =
        fixture.export.ads
            .groupingBy { it.name }
            .eachCount()
    val most = tally.values.max()
    val leaders = tally.filterValues { it == most }.keys.sorted()
    val devices =
        fixture.library.entities
            .countByKind()
            .orFail()
            .getValue(EntityKind.Device)
    val mostWrittenTo = SyntheticInstagramExport.MESSAGE_THREADS.maxBy { it.sent }
    return listOf(
        EvalQuestion(EvalTopic.TopEntities, "Which advertiser showed me the most ads?", Expected.Words(leaders)),
        EvalQuestion(EvalTopic.TopEntities, "How many ads did ${leaders.first()} show me?", Expected.Count(most)),
        EvalQuestion(
            EvalTopic.TopEntities,
            "Which advertisers showed me ads? Name them all.",
            Expected.AllOf(tally.keys.sorted().map { Expected.Words(it) }),
        ),
        EvalQuestion(EvalTopic.TopEntities, "How many different devices did I log in from?", Expected.Count(devices)),
        // Messages name their Thread, not an Account, so the question asks for the thread.
        EvalQuestion(
            EvalTopic.TopEntities,
            "In which message thread did I send the most messages?",
            Expected.Words(mostWrittenTo.title),
        ),
    )
}

private suspend fun timeRanges(
    fixture: EvalFixture,
    zone: TimeZone,
): List<EvalQuestion> {
    val events = fixture.library.events
    val adsDay = SyntheticInstagramExport.FIRST_TIME.toLocalDate(zone)
    val timedDay = SyntheticInstagramExport.TIMED_FILES_FIRST_TIME.toLocalDate(zone)
    val messagesDay = SyntheticInstagramExport.MESSAGE_FILES_FIRST_TIME.toLocalDate(zone)
    val busiest =
        events
            .times(EventFilter())
            .orFail()
            .groupingBy { it.toLocalDateTime(zone).date }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<LocalDate, Int>> { it.value }.thenBy { it.key })
            .first()
            .key
    val monthStart = LocalDate(messagesDay.year, messagesDay.month, 1)
    val monthEnd = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    val monthName = messagesDay.month.spelled().replaceFirstChar { it.titlecase() }
    return listOf(
        EvalQuestion(
            EvalTopic.TimeRanges,
            "How many ads was I shown on $adsDay?",
            Expected.Count(events.count(EventFilter(kind = EventKind.AdShown, range = adsDay.dayIn(zone))).orFail()),
        ),
        EvalQuestion(
            EvalTopic.TimeRanges,
            "How many events happened on $timedDay?",
            Expected.Count(events.count(EventFilter(range = timedDay.dayIn(zone))).orFail()),
        ),
        EvalQuestion(EvalTopic.TimeRanges, "On which day was I most active?", Expected.Day(busiest)),
        EvalQuestion(
            EvalTopic.TimeRanges,
            "How many messages did I send in $monthName ${messagesDay.year}?",
            Expected.Count(
                events
                    .count(
                        EventFilter(
                            kind = EventKind.MessageSent,
                            range = DateRange(monthStart, monthEnd).toInstants(zone),
                        ),
                    ).orFail(),
            ),
        ),
    )
}

private suspend fun search(fixture: EvalFixture): List<EvalQuestion> {
    val hits =
        fixture.library.search
            .search("sourdough", limit = LibraryTools.PAGE_RANGE.last, offset = 0)
            .orFail()
    return listOf(
        EvalQuestion(
            EvalTopic.Search,
            "Find the ad whose caption says \"Synthetic ad 3\". Which advertiser was it from?",
            Expected.Words(fixture.export.ads[3].name),
        ),
        EvalQuestion(EvalTopic.Search, "How many times does sourdough appear in my data?", Expected.Count(hits.size)),
    )
}

private suspend fun gapReport(fixture: EvalFixture): List<EvalQuestion> {
    val library = fixture.library
    val report =
        GapReportReader(
            library.imports,
            library.events,
            library.claims,
            DeclaredEvidenceRules,
            LibraryTools.CITED_COUNT,
        ).read(fixture.import.id).orFail()
    val purposes = report.verdicts.single { it.item == ArticleItem.Purposes }.coverage
    return listOf(
        EvalQuestion(
            EvalTopic.GapReport,
            "How many of the nine Article 15 items are missing from my Instagram export?",
            Expected.Count(report.count(Coverage.Missing)),
        ),
        EvalQuestion(
            EvalTopic.GapReport,
            "In the Gap report, is the purposes item covered, partial or missing?",
            Expected.Words(purposes.name),
        ),
    )
}

/** The month's English name in lowercase, the one spelling the questions and the judge both build on. */
internal fun Month.spelled(): String = name.lowercase()

private fun Map<EventKind, Int>.counted(kind: EventKind): Expected.Count = Expected.Count(getValue(kind))

private fun Long.toLocalDate(zone: TimeZone): LocalDate = Instant.fromEpochSeconds(this).toLocalDateTime(zone).date

/** The day as instants in [zone], exactly as a Tool reads a `YYYY-MM-DD` day. */
private fun LocalDate.dayIn(zone: TimeZone): ClosedRange<Instant> = checkNotNull(DateRange(this, this).toInstants(zone))
