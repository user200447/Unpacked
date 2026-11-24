package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.Result
import kotlinx.datetime.yearMonth

/** The shape of a chart: a count over time, or a ranking across Entities. */
public enum class ChartKind {
    TimeSeries,
    Bars,
}

/** One point of a chart: what it stands for, and the number. */
public data class ChartPoint(
    val label: String,
    val value: Int,
)

/**
 * A chart described, not drawn: what the agent hands the Ask screen when a question is about quantities over time
 * or across Entities (spec story 36). Carries the Provenance of the output it was made from, so a chart cites
 * exactly what the numbers behind it cite.
 */
public data class ChartSpec(
    val kind: ChartKind,
    val title: String,
    /** What the numbers count, such as "Events" or "ads shown". */
    val valueLabel: String,
    val points: List<ChartPoint>,
    override val cited: List<Provenance>,
) : ToolOutput

/**
 * The chartSpec Tool: a time series from a count spread over days or months, bars from a ranking of Entities, and
 * a typed refusal for every other shape. Pure: it reads nothing.
 */
public fun chartSpecOf(output: ToolOutput): Result<ChartSpec, ToolError> =
    when (output) {
        is EventsCounted -> {
            output.series?.let { output.timeSeries(it) } ?: refuse(output, Reason.SingleNumber)
        }

        is TopEntities -> {
            output.bars()
        }

        is ChartSpec -> {
            Result.Success(output)
        }

        is ImportsListed, is EntityTimeline, is TextFound, is MatchesFound, is GapReported -> {
            refuse(output, Reason.NotQuantities)
        }
    }

private fun EventsCounted.timeSeries(series: List<SeriesPoint>): Result<ChartSpec, ToolError> {
    if (series.isEmpty()) return refuse(this, Reason.NothingToChart)
    val what = kind?.name ?: "Events"
    val per = if (grouping == Grouping.Day) "day" else "month"
    return Result.Success(
        ChartSpec(
            kind = ChartKind.TimeSeries,
            title = "$what per $per",
            valueLabel = what,
            points =
                series.map { point ->
                    val label =
                        if (grouping ==
                            Grouping.Day
                        ) {
                            point.start.toString()
                        } else {
                            point.start.yearMonth.toString()
                        }
                    ChartPoint(label, point.count)
                },
            cited = cited,
        ),
    )
}

private fun TopEntities.bars(): Result<ChartSpec, ToolError> {
    if (top.isEmpty()) return refuse(this, Reason.NothingToChart)
    return Result.Success(
        ChartSpec(
            kind = ChartKind.Bars,
            title = "${entityKind.name} named most by ${kind.name} Events",
            valueLabel = "${kind.name} Events",
            points = top.map { ChartPoint(it.entity.displayName, it.count) },
            cited = cited,
        ),
    )
}

private fun refuse(
    output: ToolOutput,
    reason: Reason,
): Result<ChartSpec, ToolError> = Result.Error(ToolError.NotChartable(output, reason))

private typealias Reason = ToolError.NotChartable.Reason
