package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** chartSpec describes a time series and a top-N ranking, and refuses every other shape with a typed reason. */
class ChartSpecTest {
    private val library = TestLibrary()
    private val cited = listOf(library.courierAd.provenance, library.hotelsAd.provenance)

    private fun refusal(output: ToolOutput): ToolError.NotChartable.Reason =
        assertIs<ToolError.NotChartable>(assertIs<Result.Error<ToolError>>(chartSpecOf(output)).error).reason

    @Test
    fun aCountSpreadOverMonthsOrDaysBecomesATimeSeriesLabelledByPeriod() {
        val byMonth =
            EventsCounted(
                EventKind.AdShown,
                DateRange(null, null),
                null,
                4,
                Grouping.Month,
                listOf(SeriesPoint(LocalDate(2026, 9, 1), 1), SeriesPoint(LocalDate(2026, 10, 1), 3)),
                cited,
            )

        assertEquals(
            ChartSpec(
                ChartKind.TimeSeries,
                "AdShown per month",
                "AdShown",
                "Month",
                listOf(ChartPoint("2026-09", 1), ChartPoint("2026-10", 3)),
                cited,
            ),
            chartSpecOf(byMonth).orFail(),
        )

        val byDay =
            byMonth.copy(
                kind = null,
                grouping = Grouping.Day,
                series = listOf(SeriesPoint(LocalDate(2026, 10, 27), 3)),
            )
        assertEquals(
            ChartSpec(
                ChartKind.TimeSeries,
                "Events per day",
                "Events",
                "Day",
                listOf(ChartPoint("2026-10-27", 3)),
                cited,
            ),
            chartSpecOf(byDay).orFail(),
        )
    }

    @Test
    fun aRankingBecomesBarsNamedAfterTheEntities() {
        val top =
            TopEntities(
                EventKind.AdShown,
                EntityKind.Advertiser,
                DateRange(null, null),
                3,
                listOf(
                    RankedEntity(EntityRef.of(library.courier), 2, listOf(library.courierAd.provenance)),
                    RankedEntity(EntityRef.of(library.hotels), 1, listOf(library.hotelsAd.provenance)),
                ),
            )

        assertEquals(
            ChartSpec(
                ChartKind.Bars,
                "Advertiser named most by AdShown Events",
                "AdShown Events",
                "Advertiser",
                listOf(ChartPoint("Example Courier", 2), ChartPoint("Example Hotels", 1)),
                cited,
            ),
            chartSpecOf(top).orFail(),
        )
    }

    @Test
    fun everyOtherShapeIsRefusedWithTheReasonItLacks() {
        val once = EventsCounted(null, DateRange(null, null), Source.Instagram, 5, null, null, cited)
        assertEquals(ToolError.NotChartable.Reason.SingleNumber, refusal(once))
        assertEquals(
            ToolError.NotChartable.Reason.NothingToChart,
            refusal(once.copy(total = 0, grouping = Grouping.Day, series = emptyList())),
        )
        assertEquals(
            ToolError.NotChartable.Reason.NothingToChart,
            refusal(TopEntities(EventKind.Login, EntityKind.Device, DateRange(null, null), 0, emptyList())),
        )
        assertEquals(ToolError.NotChartable.Reason.NotQuantities, refusal(ImportsListed(emptyList())))
        assertEquals(ToolError.NotChartable.Reason.NotQuantities, refusal(TextFound("cour", emptyList())))
        assertEquals(
            ToolError.NotChartable.Reason.NotQuantities,
            refusal(MatchesFound(EntityKind.Advertiser, emptyList())),
        )

        val chart = ChartSpec(ChartKind.Bars, "t", "v", "p", listOf(ChartPoint("a", 1)), cited)
        assertEquals(chart, chartSpecOf(chart).orFail(), "a chart is already a chart")
    }
}
