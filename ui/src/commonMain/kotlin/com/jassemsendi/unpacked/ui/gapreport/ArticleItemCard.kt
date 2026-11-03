package com.jassemsendi.unpacked.ui.gapreport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.imports.FileResultUi
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable

/** One Article item: its letter and title, its Verdict, what it asks for, and the evidence found. */
@Composable
internal fun ArticleItemCard(
    item: ArticleItemUi,
    selected: RecordRef?,
    onOpen: (RecordRef) -> Unit,
) {
    Card(modifier = Modifier.readable()) {
        Column(modifier = Modifier.padding(Spacing.medium), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(weight = 1f)) {
                    Text(
                        item.article,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                }
                CoverageBadge(item.coverage, item.coverageLabel)
            }
            Text(item.description, style = MaterialTheme.typography.bodyMedium)
            item.emptyText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            item.evidence.forEach { EvidenceSection(it, selected, onOpen) }
        }
    }
}

@Composable
private fun CoverageBadge(
    coverage: Coverage,
    label: String,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) =
        when (coverage) {
            Coverage.Covered -> scheme.primaryContainer to scheme.onPrimaryContainer
            Coverage.Partial -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            Coverage.Missing -> scheme.errorContainer to scheme.onErrorContainer
        }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Spacing.small, vertical = Spacing.small / 2),
        )
    }
}

/** What one rule found: the files as the inventory lists them, or the cited records, each opening its Record. */
@Composable
private fun EvidenceSection(
    evidence: EvidenceUi,
    selected: RecordRef?,
    onOpen: (RecordRef) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small / 2)) {
        Text(evidence.caption, style = MaterialTheme.typography.labelMedium)
        when (evidence) {
            is EvidenceUi.Files -> {
                evidence.files.forEach { FileLine(it) }
                if (evidence.moreFiles > 0) {
                    Text("and ${evidence.moreFiles} more files", style = MaterialTheme.typography.bodySmall)
                }
            }

            is EvidenceUi.Records -> {
                evidence.cited.forEach { CitedLine(it, selected = it.ref == selected) { onOpen(it.ref) } }
            }
        }
    }
}

@Composable
private fun FileLine(file: FileResultUi) {
    Column {
        Text(file.path, style = MaterialTheme.typography.bodySmall)
        Text(
            file.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CitedLine(
    cited: CitedRecordUi,
    selected: Boolean,
    onOpen: () -> Unit,
) {
    Text(
        cited.text,
        style = MaterialTheme.typography.bodySmall,
        color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    )
}
