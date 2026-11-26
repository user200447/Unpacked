package com.jassemsendi.unpacked.ui.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.ui.home.AttributeUi
import com.jassemsendi.unpacked.ui.home.DetailLayout
import com.jassemsendi.unpacked.ui.home.LabelValue
import com.jassemsendi.unpacked.ui.imports.ImportChoiceUi
import com.jassemsendi.unpacked.ui.imports.ImportChooser
import com.jassemsendi.unpacked.ui.theme.ErrorNotice
import com.jassemsendi.unpacked.ui.theme.SectionCard
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ExplorerRoot(
    onBack: () -> Unit,
    importId: ImportId? = null,
    viewModel: ExplorerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Imports may have come or gone while another screen was up, so every appearance re-reads the library,
    // exploring the Import it was sent to, if any, over whichever was chosen before.
    LaunchedEffect(viewModel, importId) {
        viewModel.onAction(importId?.let { ExplorerAction.Select(it) } ?: ExplorerAction.Refresh)
    }
    ExplorerScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

/** Every file of the chosen Import's Export, with the opened one's Records beside or over the list. */
@Composable
fun ExplorerScreen(
    state: ExplorerState,
    onAction: (ExplorerAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val close = { onAction(ExplorerAction.Close) }
    DetailLayout(
        detail = state.opened,
        onClose = close,
        modifier = modifier,
        pane = { opened, paneModifier -> OpenedFilePane(opened, onClose = close, modifier = paneModifier) },
    ) {
        Files(state, onAction, onBack, modifier = it)
    }
}

@Composable
private fun Files(
    state: ExplorerState,
    onAction: (ExplorerAction) -> Unit,
    onBack: () -> Unit,
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
            item { ImportChooser(state.imports, state.selected) { onAction(ExplorerAction.Select(it)) } }
        }
        state.error?.let { error -> item { ErrorNotice(error) } }
        state.exportGone?.let { gone -> item { ExportGoneNotice(gone) { onAction(ExplorerAction.PickExportAgain) } } }
        val inventory = state.inventory
        when {
            inventory != null -> item { Summary(inventory, state.isOpening) }
            state.isLoading -> item { CircularProgressIndicator() }
            else -> item { EmptyNotice() }
        }
        items(inventory?.files.orEmpty(), key = { it.path }) { file ->
            FileCard(
                file,
                selected = file.path == state.opened?.file?.path,
            ) { onAction(ExplorerAction.Open(file.path)) }
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
            Text("Explorer", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Every file in the Export, including the ones the Import made nothing of, and the records each holds.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onBack) { Text("Timeline") }
    }
}

/** Whose Export this is, where the Import remembers it, and how much of it is media that is only counted. */
@Composable
private fun Summary(
    inventory: InventoryUi,
    isOpening: Boolean,
) {
    Column(modifier = Modifier.readable(), verticalArrangement = Arrangement.spacedBy(Spacing.small / 2)) {
        Text("${inventory.exportName} from ${inventory.sourceName}", style = MaterialTheme.typography.titleMedium)
        Text("Read from ${inventory.location}.", style = MaterialTheme.typography.bodyMedium)
        Text(
            "${inventory.files.size} files the Import visited, " +
                "and ${inventory.mediaFiles} media files counted and never opened.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (isOpening) {
            Text(
                "Reading from the Export…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The Export is not where the Import left it; where the platform has a chooser, a button lets the user point again. */
@Composable
private fun ExportGoneNotice(
    gone: ExportGoneUi,
    onPickAgain: () -> Unit,
) {
    SectionCard(
        title = "The Export has moved",
        modifier = Modifier.readable(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(gone.message, style = MaterialTheme.typography.bodyMedium)
        if (gone.canPickAgain) {
            Button(onClick = onPickAgain) { Text("Pick the Export again") }
        } else {
            Text(
                "Choosing an Export is not available on this platform yet.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EmptyNotice() {
    Column(modifier = Modifier.readable(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text("No Import to explore yet.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Import an Export and every file in it is listed here, whether or not the Import understood it.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** One file as the Import reported it; a JSON file opens to its Records, anything else only says what it is. */
@Composable
private fun FileCard(
    file: ExplorerFileUi,
    selected: Boolean,
    onOpen: () -> Unit,
) {
    val colors =
        if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    Card(modifier = Modifier.readable().clickable(enabled = file.canOpen, onClick = onOpen), colors = colors) {
        Column(
            modifier = Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small / 2),
        ) {
            Text(file.path, style = MaterialTheme.typography.titleSmall)
            Text(
                "${file.outcome}. ${file.detail}",
                style = MaterialTheme.typography.bodySmall,
                color = if (file.outcome == FAILED) MaterialTheme.colorScheme.error else LocalContentColor.current,
            )
            val rawKind = "Raw kind: ${file.rawKind}"
            Text(
                if (file.canOpen) rawKind else "$rawKind. Not JSON, so it stays closed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One file's Records as read now from the Export: each one its label and value pairs, in the file's order. */
@Composable
private fun OpenedFilePane(
    opened: OpenedFileUi,
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
                opened.file.path,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(weight = 1f),
            )
            TextButton(onClick = onClose) { Text("Close") }
        }
        Text("${opened.file.outcome}. ${opened.file.detail}", style = MaterialTheme.typography.bodyMedium)
        Text("Raw kind: ${opened.file.rawKind}", style = MaterialTheme.typography.bodyMedium)
        opened.note?.let { note ->
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (opened.records.isEmpty() && opened.note == null) {
            Text("This file holds no records.", style = MaterialTheme.typography.bodyMedium)
        }
        opened.records.forEach { record ->
            SectionCard(title = "Record ${record.index}", modifier = Modifier.fillMaxWidth()) {
                if (record.fields.isEmpty()) {
                    Text("An empty record.", style = MaterialTheme.typography.bodySmall)
                }
                record.fields.forEach { LabelValue(it.label, it.value.ifEmpty { "(empty)" }) }
            }
        }
    }
}

private const val FAILED = "Failed"

@Preview
@Composable
private fun ExplorerScreenPreview() {
    val viewed =
        ExplorerFileUi(
            "ads_information/ads_viewed.json",
            "ads_viewed",
            "Parsed",
            "5 records: 5 Events, 0 Claims",
            canOpen = true,
        )
    val unmapped =
        ExplorerFileUi(
            "ads_information/subscription_for_no_ads.json",
            "subscription_for_no_ads",
            "Skipped",
            "no mapper registered for subscription_for_no_ads.json",
            canOpen = true,
        )
    val notes =
        ExplorerFileUi("personal_information/notes_0.txt", "notes_0", "Skipped", "not a JSON file", canOpen = false)
    MaterialTheme {
        ExplorerScreen(
            state =
                ExplorerState(
                    isLoading = false,
                    imports =
                        listOf(
                            ImportChoiceUi(ImportId(1), "instagram-me", "2026-10-27 10:12", isComplete = true),
                        ),
                    selected = ImportId(1),
                    inventory =
                        InventoryUi(
                            exportName = "instagram-me",
                            sourceName = "Instagram",
                            location = "the zip at /Users/me/Downloads/instagram-me.zip",
                            files = listOf(viewed, unmapped, notes),
                            mediaFiles = 3,
                        ),
                    opened =
                        OpenedFileUi(
                            file = unmapped,
                            records =
                                listOf(
                                    RecordUi(
                                        0,
                                        listOf(
                                            AttributeUi("Timestamp", "2026-10-08T07:48:39Z"),
                                            AttributeUi("Subscription status", "Not subscribed"),
                                        ),
                                    ),
                                ),
                            note = null,
                        ),
                ),
            onAction = {},
            onBack = {},
        )
    }
}
