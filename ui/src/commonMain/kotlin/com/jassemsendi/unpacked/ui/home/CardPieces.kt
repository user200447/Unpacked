package com.jassemsendi.unpacked.ui.home

import com.jassemsendi.unpacked.data.EntityTally
import com.jassemsendi.unpacked.insights.Figure
import com.jassemsendi.unpacked.insights.MonthCounts
import com.jassemsendi.unpacked.insights.Opens
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth
import kotlin.math.roundToInt

// The pieces every card's UI is made of; the cards themselves are in InsightCardMappers.

/** Goes where a number on a card points: the timeline is this screen, narrowed; the rest are other screens. */
internal fun Opens.follow(
    onAction: (HomeAction) -> Unit,
    destinations: HomeDestinations,
) {
    when (this) {
        is Opens.Timeline -> onAction(HomeAction.Filter(kind))
        is Opens.Claims -> destinations.onClaims(kind)
        is Opens.Entity -> destinations.onEntity(id)
    }
}

internal fun Figure.toUi(caption: String) = FigureUi(value.toString(), caption, opens)

internal fun EntityTally.toRow(noun: String) =
    CardRowUi(entity.displayName, counted(count, noun), Opens.Entity(entity.id))

/** The latest [MONTHS_SHOWN] months of a series as rows, each number captioned in the card's order. */
internal fun List<MonthCounts>.toRows(vararg captions: String): List<MonthRowUi> =
    takeLast(MONTHS_SHOWN).map { month ->
        MonthRowUi(month.month.toText(), month.counts.zip(captions) { figure, caption -> figure.toUi(caption) })
    }

internal fun String.onlyIf(condition: Boolean): String? = takeIf { condition }

/** The noun alone, in the number's grammatical number: the figure in front of it is shown large. */
internal fun noun(
    count: Int,
    singular: String,
): String = if (count == 1) singular else "${singular}s"

/** "1.5", or "3" when there is nothing after the point. */
internal fun Double.toOneDecimal(): String {
    val tenths = (this * TENTHS).roundToInt()
    return if (tenths % TENTHS == 0) "${tenths / TENTHS}" else "${tenths / TENTHS}.${tenths % TENTHS}"
}

/** The hour as the start of its range, "23:00" for 23. */
internal fun Int.toHourText(): String = toString().padStart(2, '0') + ":00"

internal fun DayOfWeek.toText(): String = name.lowercase().replaceFirstChar { it.titlecase() }

/** "Oct 2026": the month's short name and its year. */
private fun YearMonth.toText(): String {
    val shortName = month.name.take(MONTH_ABBREVIATION)
    return shortName.lowercase().replaceFirstChar { it.titlecase() } + " $year"
}

private const val TENTHS = 10
private const val MONTH_ABBREVIATION = 3

/** How many of a series' latest months a card lists. */
private const val MONTHS_SHOWN = 6
