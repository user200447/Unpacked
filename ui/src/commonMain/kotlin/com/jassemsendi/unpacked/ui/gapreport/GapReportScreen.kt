package com.jassemsendi.unpacked.ui.gapreport

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.ui.home.RecordDetailLayout
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.theme.ErrorNotice
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GapReportRoot(
    onBack: () -> Unit,
    onAccessRequest: (ImportId) -> Unit,
    importId: ImportId? = null,
    viewModel: GapReportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Imports may have come or gone while another screen was up, so every appearance re-reads the library,
    // reporting on the Import it was sent to, if any, over whichever was chosen before.
    LaunchedEffect(viewModel, importId) {
        viewModel.onAction(importId?.let { GapReportAction.Select(it) } ?: GapReportAction.Refresh)
    }
    GapReportScreen(state = state, onAction = viewModel::onAction, onBack = onBack, onAccessRequest = onAccessRequest)
}

/** The nine Verdicts for the chosen Import, with the opened Record beside or over them. */
@Composable
fun GapReportScreen(
    state: GapReportState,
    onAction: (GapReportAction) -> Unit,
    onBack: () -> Unit,
    onAccessRequest: (ImportId) -> Unit,
    modifier: Modifier = Modifier,
) {
    RecordDetailLayout(
        detail = state.detail,
        onClose = { onAction(GapReportAction.CloseDetail) },
        modifier = modifier,
    ) {
        Report(state, onAction, onBack, onAccessRequest, modifier = it)
    }
}

@Composable
private fun Report(
    state: GapReportState,
    onAction: (GapReportAction) -> Unit,
    onBack: () -> Unit,
    onAccessRequest: (ImportId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Header(onBack) }
        if (state.imports.isNotEmpty()) {
            item { ImportChooser(state.imports, state.selected) { onAction(GapReportAction.Select(it)) } }
        }
        state.error?.let { error -> item { ErrorNotice(error) } }
        val report = state.report
        val onDraft = state.selected?.let { id -> { onAccessRequest(id) } }
        when {
            report != null -> item { Summary(report, onAccessRequest = onDraft) }
            state.isLoading -> item { CircularProgressIndicator() }
            else -> item { EmptyNotice() }
        }
        items(report?.items.orEmpty(), key = { it.item }) { item ->
            ArticleItemCard(item, selected = state.detail?.ref) { onAction(GapReportAction.Open(it)) }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(
        modifier = Modifier.readable(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text("Gap report", style = MaterialTheme.typography.headlineMedium)
            Text(
                "The nine things GDPR Article 15 says you must be given, and what this Export gave you of each.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                NOT_LEGAL_ADVICE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onBack) { Text("Timeline") }
    }
}

/** One chip per stored Import, newest first; the report is over the selected one. */
@Composable
private fun ImportChooser(
    imports: List<ImportChoiceUi>,
    selected: ImportId?,
    onSelect: (ImportId) -> Unit,
) {
    Row(
        modifier = Modifier.readable().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        imports.forEach { import ->
            val name = if (import.isComplete) import.exportName else "${import.exportName} (incomplete)"
            FilterChip(selected = import.id == selected, onClick = { onSelect(import.id) }, label = { Text(name) })
        }
    }
}

/** The report in one line, and the way to the letter that asks for what it found wanting. */
@Composable
private fun Summary(
    report: GapReportUi,
    onAccessRequest: (() -> Unit)?,
) {
    Column(modifier = Modifier.readable(), verticalArrangement = Arrangement.spacedBy(Spacing.small / 2)) {
        Text("${report.exportName} from ${report.sourceName}", style = MaterialTheme.typography.titleMedium)
        Text(report.summary, style = MaterialTheme.typography.bodyMedium)
        if (onAccessRequest != null) {
            Button(onClick = onAccessRequest, modifier = Modifier.padding(top = Spacing.small)) {
                Text("Draft the Access request")
            }
        }
    }
}

@Composable
private fun EmptyNotice() {
    Column(modifier = Modifier.readable(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text("No Import to report on yet.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Import an Export and its nine Verdicts land here, with the evidence behind each.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview
@Composable
private fun GapReportScreenPreview() {
    MaterialTheme {
        GapReportScreen(
            state =
                GapReportState(
                    isLoading = false,
                    imports =
                        listOf(
                            ImportChoiceUi(ImportId(1), "instagram-me", "2026-10-27 10:12", isComplete = true),
                        ),
                    selected = ImportId(1),
                    report =
                        GapReportUi(
                            exportName = "instagram-me",
                            sourceName = "Instagram",
                            summary = "2 covered, 3 partial, 4 missing",
                            items = previewItems(),
                        ),
                ),
            onAction = {},
            onBack = {},
            onAccessRequest = {},
        )
    }
}

/** One Partial item with a cited Claim and one Missing item. */
private fun previewItems(): List<ArticleItemUi> {
    val recipients =
        ArticleItemUi(
            item = ArticleItem.Recipients,
            article = "15(1)(c)",
            title = "Recipients",
            description = "Who your data has been or will be disclosed to.",
            coverage = Coverage.Partial,
            coverageLabel = "Partial",
            evidence =
                listOf(
                    EvidenceUi.Records(
                        "Counts as Partial: 4 Claims of Advertiser holds contact",
                        listOf(
                            CitedRecordUi(
                                RecordRef.Claim(
                                    Provenance(ExportId("instagram-me"), "ads_information/advertisers.json", 0),
                                ),
                                "ads_information/advertisers.json, record 0",
                            ),
                        ),
                    ),
                ),
            emptyText = null,
        )
    val retention =
        recipients.copy(
            item = ArticleItem.RetentionPeriod,
            article = "15(1)(d)",
            title = "Retention period",
            description = "How long your data is kept, or how that period is decided.",
            coverage = Coverage.Missing,
            coverageLabel = "Missing",
            evidence = emptyList(),
            emptyText = "Nothing in this Export counts toward this item.",
        )
    return listOf(recipients, retention)
}
