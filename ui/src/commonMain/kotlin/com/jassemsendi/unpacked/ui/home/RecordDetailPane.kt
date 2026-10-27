package com.jassemsendi.unpacked.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jassemsendi.unpacked.ui.theme.SectionCard
import com.jassemsendi.unpacked.ui.theme.Spacing

/** One Record as stored: its label and value pairs, its Raw kind, and the Provenance it came from. */
@Composable
internal fun RecordDetailPane(
    detail: RecordDetailUi,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(
                detail.kindLabel,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(weight = 1f),
            )
            TextButton(onClick = onClose) { Text("Close") }
        }
        Text(
            when (val time = detail.time) {
                is RecordTimeUi.Happened -> "Happened ${time.at}"
                is RecordTimeUi.LastUpdated -> "Last updated ${time.at}"
                RecordTimeUi.Undated -> "No time recorded"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Raw kind: ${detail.rawKind}", style = MaterialTheme.typography.bodyMedium)
        SectionCard(title = "Record", modifier = Modifier.fillMaxWidth()) {
            if (detail.attributes.isEmpty()) {
                Text("Nothing but the time and the Kind.", style = MaterialTheme.typography.bodySmall)
            }
            detail.attributes.forEach { LabelValue(it.label, it.value.ifEmpty { "(empty)" }) }
        }
        if (detail.entities.isNotEmpty()) {
            SectionCard(title = "Names", modifier = Modifier.fillMaxWidth()) {
                detail.entities.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        val provenance = detail.ref.provenance
        SectionCard(title = "Provenance", modifier = Modifier.fillMaxWidth()) {
            LabelValue("Export", provenance.exportId.value)
            LabelValue("File", provenance.path)
            LabelValue("Record index", provenance.recordIndex.toString())
            if (provenance.itemIndex > 0) LabelValue("Item index", provenance.itemIndex.toString())
        }
    }
}

@Composable
private fun LabelValue(
    label: String,
    value: String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
