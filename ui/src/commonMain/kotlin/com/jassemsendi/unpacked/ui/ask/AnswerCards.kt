package com.jassemsendi.unpacked.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import com.jassemsendi.unpacked.agent.CITATION_MARKER
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.ui.theme.SectionCard
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable

// The cards under a question: the answer with its clickable citations and its flag, the chart the model asked
// for, and the Tool trace that backs every number (spec stories 35 and 44).

/** The answer with its markers made clickable, how it is marked, and the flag when a number has no backing. */
@Composable
internal fun AnswerCard(
    answer: AnswerUi,
    onOpen: (Provenance) -> Unit,
) {
    SectionCard(title = "Answer", modifier = Modifier.readable()) {
        Text(citedText(answer, onOpen), style = MaterialTheme.typography.bodyLarge)
        if (!answer.isVerified) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "Unverified: ${answer.unverifiedNumbers.joinToString()} " +
                        (if (answer.unverifiedNumbers.size == 1) "appears" else "appear") +
                        " in no Tool output. Trust the trace below, not these numbers.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Spacing.small),
                )
            }
        }
        Text("Answered by ${answer.modelMarker}", style = MaterialTheme.typography.labelMedium)
    }
}

/** The answer text with every marker that names a Record turned into a link that opens it. */
@Composable
private fun citedText(
    answer: AnswerUi,
    onOpen: (Provenance) -> Unit,
) = buildAnnotatedString {
    val byMarker = answer.citations.associateBy { it.marker }
    val linkStyle =
        TextLinkStyles(
            SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
        )
    var from = 0
    CITATION_MARKER.findAll(answer.text).forEach { match ->
        append(answer.text.substring(from, match.range.first))
        val citation = byMarker[match.groupValues[1].toInt()]
        if (citation == null) {
            append(match.value)
        } else {
            withLink(LinkAnnotation.Clickable("cite:${citation.marker}", linkStyle) { onOpen(citation.provenance) }) {
                append(match.value)
            }
        }
        from = match.range.last + 1
    }
    append(answer.text.substring(from))
}

@Composable
internal fun ChartCard(chart: ChartUi) {
    SectionCard(title = chart.title, modifier = Modifier.readable()) {
        chart.rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(weight = 1f))
                Box(modifier = Modifier.weight(weight = 2f)) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(row.share)
                                .height(Spacing.medium)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(row.value.toString(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(chart.valueLabel, style = MaterialTheme.typography.labelSmall)
    }
}

/** Every Tool call in order, with what it was called with and what came back; a long output unfolds on tap. */
@Composable
internal fun TraceCard(trace: List<TraceStepUi>) {
    SectionCard(title = "Tool trace", modifier = Modifier.readable()) {
        if (trace.isEmpty()) Text("The model called no Tool.", style = MaterialTheme.typography.bodyMedium)
        trace.forEach { step -> TraceStepRow(step) }
    }
}

@Composable
private fun TraceStepRow(step: TraceStepUi) {
    var unfolded by remember(step.number) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small / 2)) {
        Text("${step.number}. ${step.tool}", style = MaterialTheme.typography.titleSmall)
        Text(step.input, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        Text(
            step.output,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (step.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (unfolded) Int.MAX_VALUE else FOLDED_OUTPUT_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { unfolded = !unfolded },
        )
    }
}

private const val FOLDED_OUTPUT_LINES = 6
