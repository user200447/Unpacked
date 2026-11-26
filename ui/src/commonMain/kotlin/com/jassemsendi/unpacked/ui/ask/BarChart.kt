package com.jassemsendi.unpacked.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jassemsendi.unpacked.ui.theme.Spacing

// A ranking as bars (glossary: Chart): one row per Entity with its count at the end, the axes named above and
// the value axis under the bars; the numbers are the Tool's.

/** Each Entity on its row with a bar as long as its share of the top, its count at the end; the axes named above. */
@Composable
internal fun BarChart(chart: ChartUi) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small / 2)) {
        val description = chart.spoken("Bar chart")
        BarRow(
            label = { Text(chart.pointLabel, style = MaterialTheme.typography.labelMedium) },
            bar = { Text(chart.valueLabel, style = MaterialTheme.typography.labelMedium) },
            value = {},
        )
        Column(
            modifier = Modifier.semantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(Spacing.small / 2),
        ) {
            chart.rows.forEach { row ->
                BarRow(
                    label = {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    bar = {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(chart.heightOf(row.value))
                                    .height(BAR_HEIGHT)
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                    },
                    value = { Text(row.value.toString(), style = MaterialTheme.typography.bodyMedium) },
                )
            }
        }
        BarRow(
            label = {},
            bar = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0", style = MaterialTheme.typography.labelSmall)
                    if (chart.top > 0) Text(chart.top.toString(), style = MaterialTheme.typography.labelSmall)
                }
            },
            value = {},
        )
    }
}

/** The three columns every row of a bar chart shares, so the bars, their axis and its name line up. */
@Composable
private fun BarRow(
    label: @Composable () -> Unit,
    bar: @Composable () -> Unit,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(weight = 1f)) { label() }
        Box(modifier = Modifier.weight(weight = 2f), contentAlignment = Alignment.CenterStart) { bar() }
        Box(modifier = Modifier.width(VALUE_WIDTH), contentAlignment = Alignment.CenterEnd) { value() }
    }
}

private val VALUE_WIDTH: Dp = 40.dp
private val BAR_HEIGHT: Dp = 16.dp
