package com.jassemsendi.unpacked.ui.ask

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jassemsendi.unpacked.agent.ChartKind
import com.jassemsendi.unpacked.agent.ChartPoint
import com.jassemsendi.unpacked.agent.ChartSpec
import com.jassemsendi.unpacked.ui.theme.SectionCard
import com.jassemsendi.unpacked.ui.theme.readable
import org.jetbrains.compose.ui.tooling.preview.Preview

// The Chart under an answer (spec story 36): a line for a count over time, bars for a ranking, both axes named
// and every number drawn the one the Tool trace shows. Nothing here computes a number; it only draws them.

/** A time series as a line with the periods along the bottom, a ranking as bars named after the Entities. */
@Composable
internal fun ChartCard(chart: ChartUi) {
    SectionCard(title = chart.title, modifier = Modifier.readable().testTag(CHART_TAG)) {
        when (chart.kind) {
            ChartKind.TimeSeries -> LineChart(chart)
            ChartKind.Bars -> BarChart(chart)
        }
    }
}

/** How far up the chart a value reaches, 0 to 1; nothing reaches anywhere when every value is 0. */
internal fun ChartUi.heightOf(value: Int): Float = if (top == 0) 0f else value.toFloat() / top

/** The chart read aloud: its kind and every point with its number, for a screen reader and for the tests. */
internal fun ChartUi.spoken(kind: String): String = "$kind: " + rows.joinToString { "${it.label}: ${it.value}" }

@Preview
@Composable
private fun LineChartPreview() {
    val points = listOf(ChartPoint("2026-08", 0), ChartPoint("2026-09", 1), ChartPoint("2026-10", 2))
    MaterialTheme {
        ChartCard(ChartSpec(ChartKind.TimeSeries, "AdShown per month", "AdShown", "Month", points, emptyList()).toUi())
    }
}

@Preview
@Composable
private fun BarChartPreview() {
    val points = listOf(ChartPoint("Example Courier", 2), ChartPoint("Example Hotels", 1))
    MaterialTheme {
        ChartCard(
            ChartSpec(
                ChartKind.Bars,
                "Advertiser named most by AdShown Events",
                "AdShown Events",
                "Advertiser",
                points,
                emptyList(),
            ).toUi(),
        )
    }
}
