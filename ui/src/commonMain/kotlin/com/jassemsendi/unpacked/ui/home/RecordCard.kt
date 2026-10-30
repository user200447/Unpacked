package com.jassemsendi.unpacked.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable

/** A [RecordRowUi] as a card: its time as words and its Provenance as the footnote. */
@Composable
internal fun RecordRow(
    row: RecordRowUi,
    selected: Boolean,
    onOpen: () -> Unit,
) {
    RecordCard(
        lines = RecordCardLines(row.time.text(), row.kindLabel, row.summary, row.ref.provenance.text()),
        selected = selected,
        onOpen = onOpen,
    )
}

/** A list key the platform can save: an Event and a Claim read from the same record share a Provenance hash. */
internal fun RecordRef.listKey(): String =
    when (this) {
        is RecordRef.Event -> "event:${provenance.hash}"
        is RecordRef.Claim -> "claim:${provenance.hash}"
    }

/**
 * One Event or Claim in a list, the same on the timeline and among search hits. Opens on tap; shows as
 * selected while its Record is open.
 */
@Composable
internal fun RecordCard(
    lines: RecordCardLines,
    selected: Boolean,
    onOpen: () -> Unit,
) {
    val colors =
        if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    Card(modifier = Modifier.readable().clickable(onClick = onOpen), colors = colors) {
        Column(
            modifier = Modifier.padding(Spacing.medium).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.small / 2),
        ) {
            Text(lines.time, style = MaterialTheme.typography.bodySmall)
            Text(lines.kindLabel, style = MaterialTheme.typography.titleMedium)
            if (lines.summary.isNotEmpty()) {
                Text(lines.summary, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                lines.footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
