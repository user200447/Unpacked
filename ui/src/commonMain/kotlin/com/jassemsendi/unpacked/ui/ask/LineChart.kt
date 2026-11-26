package com.jassemsendi.unpacked.ui.ask

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jassemsendi.unpacked.ui.theme.Spacing
import kotlin.math.roundToInt

// A count over time as a line (glossary: Chart): the periods along the bottom under their own points, the value
// axis up the left, both named; the numbers are the Tool's.

/** The value axis up the left with its top and bottom, the line across, the periods under their points, each axis named. */
@Composable
internal fun LineChart(chart: ChartUi) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small / 2)) {
        Text(chart.valueLabel, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().height(LINE_CHART_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Column(
                modifier = Modifier.fillMaxHeight().width(AXIS_LABEL_WIDTH),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(if (chart.top == 0) "" else chart.top.toString(), style = MaterialTheme.typography.labelSmall)
                Text("0", style = MaterialTheme.typography.labelSmall)
            }
            Line(chart, modifier = Modifier.weight(weight = 1f).fillMaxHeight())
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Spacer(modifier = Modifier.width(AXIS_LABEL_WIDTH))
            PeriodLabels(chart.rows, modifier = Modifier.weight(weight = 1f))
        }
        Text(
            chart.pointLabel,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The two axis lines, the points at their heights joined in order, a dot on each; one point sits in the middle. */
@Composable
private fun Line(
    chart: ChartUi,
    modifier: Modifier = Modifier,
) {
    val line = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val description = chart.spoken("Line chart")
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val inset = DOT_RADIUS.toPx()
        val height = size.height - 2 * inset
        val points =
            chart.rows.mapIndexed { index, row ->
                val x = pointX(index, chart.rows.size, size.width - 2 * inset)
                Offset(inset + x, inset + height * (1 - chart.heightOf(row.value)))
            }
        drawAxes(axis, inset)
        if (points.size > 1) {
            drawPoints(points, PointMode.Polygon, line, strokeWidth = LINE_WIDTH.toPx(), cap = StrokeCap.Round)
        }
        points.forEach { drawCircle(line, radius = inset, center = it) }
    }
}

/** Where the point at [index] of [count] sits across a span of [width]: spread evenly, a lone one in the middle. */
private fun pointX(
    index: Int,
    count: Int,
    width: Float,
): Float = if (count == 1) width / 2 else width * index / (count - 1)

private fun DrawScope.drawAxes(
    color: Color,
    inset: Float,
) {
    val stroke = AXIS_WIDTH.toPx()
    drawLine(color, Offset(inset, inset), Offset(inset, size.height - inset), stroke)
    drawLine(color, Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset), stroke)
}

/**
 * The first period, the last and a few evenly spaced between, each centred under its own point and kept inside
 * the span, so a long series stays legible and every label sits where its dot is.
 */
@Composable
private fun PeriodLabels(
    rows: List<ChartRowUi>,
    modifier: Modifier = Modifier,
) {
    val picked = rows.pickedIndexes()
    val labelStyle = MaterialTheme.typography.labelSmall
    Layout(
        content = { picked.forEach { index -> Text(rows[index].label, style = labelStyle, maxLines = 1) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val inset = DOT_RADIUS.roundToPx()
        val span = (constraints.maxWidth - 2 * inset).toFloat()
        layout(constraints.maxWidth, placeables.maxOfOrNull { it.height } ?: 0) {
            placeables.forEachIndexed { slot, label ->
                val centre = inset + pointX(picked[slot], rows.size, span).roundToInt()
                val rightmost = (constraints.maxWidth - label.width).coerceAtLeast(0)
                label.placeRelative((centre - label.width / 2).coerceIn(0, rightmost), 0)
            }
        }
    }
}

/** Which points get a label: all of a short series; of a long one, [MAX_PERIOD_LABELS] spread from first to last. */
private fun List<ChartRowUi>.pickedIndexes(): List<Int> {
    if (size <= MAX_PERIOD_LABELS) return indices.toList()
    val last = size - 1
    return (0 until MAX_PERIOD_LABELS).map { slot -> (slot * last.toFloat() / (MAX_PERIOD_LABELS - 1)).roundToInt() }
}

private val LINE_CHART_HEIGHT: Dp = 160.dp
private val AXIS_LABEL_WIDTH: Dp = 32.dp
private val DOT_RADIUS: Dp = 4.dp
private val LINE_WIDTH: Dp = 2.dp
private val AXIS_WIDTH: Dp = 1.dp
private const val MAX_PERIOD_LABELS = 6
