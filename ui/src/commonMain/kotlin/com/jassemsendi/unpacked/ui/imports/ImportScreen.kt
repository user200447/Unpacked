package com.jassemsendi.unpacked.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jassemsendi.unpacked.ingest.ExportPart
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.ui.ObserveAsEvents
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ImportRoot(
    onShowTimeline: () -> Unit,
    viewModel: ImportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ImportEvent.ImportDeleted -> {
                scope.launch {
                    snackbars.showSnackbar(
                        "Deleted the Import of ${event.exportName} and everything it produced",
                    )
                }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbars) }) { padding ->
        ImportScreen(
            state = state,
            onAction = viewModel::onAction,
            onShowTimeline = onShowTimeline,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun ImportScreen(
    state: ImportState,
    onAction: (ImportAction) -> Unit,
    onShowTimeline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Picker(state, onAction, onShowTimeline) }
        state.part?.let { part -> item { PartNotice(part) } }
        item { RunPanel(state.run, onShowTimeline) }
        item { Text("Your Imports", style = MaterialTheme.typography.titleLarge, modifier = Modifier.readable()) }
        if (state.imports.isEmpty()) {
            item {
                Text(
                    "Nothing imported yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.readable(),
                )
            }
        }
        items(state.imports, key = { it.id.value }) { import ->
            ImportRow(import, onDelete = { onAction(ImportAction.DeleteImport(import.id)) })
        }
    }
}

@Composable
private fun Picker(
    state: ImportState,
    onAction: (ImportAction) -> Unit,
    onShowTimeline: () -> Unit,
) {
    val running = state.run is ImportRunUi.Running
    Column(modifier = Modifier.readable(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text("Import an Export", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pick the zip you downloaded, or the folder you unzipped it into. It is read on this device only.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!state.canPickZip && !state.canPickFolder) {
            Text(
                "Choosing an Export is not available on this platform yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            if (state.canPickZip) {
                Button(onClick = { onAction(ImportAction.PickZip) }, enabled = !running) { Text("Choose a zip") }
            }
            if (state.canPickFolder) {
                OutlinedButton(
                    onClick = { onAction(ImportAction.PickFolder) },
                    enabled = !running,
                ) { Text("Choose a folder") }
            }
            TextButton(onClick = onShowTimeline) { Text("See your timeline") }
        }
    }
}

@Composable
private fun PartNotice(part: ExportPart) {
    val whichPart = part.count?.let { "part ${part.number} of $it" } ?: "part ${part.number}"
    NoticeCard(
        title = "This Export is $whichPart",
        body =
            "The Source split your Export into several files. Only this part is imported now; " +
                "import the other parts one at a time to see everything.",
    )
}

@Composable
private fun ImportRow(
    import: ImportUi,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.readable()) {
        Row(
            modifier = Modifier.padding(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(weight = 1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.small / 2),
            ) {
                Text(import.exportName, style = MaterialTheme.typography.titleMedium)
                Text("${import.sourceName}, started ${import.startedAt}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "${import.eventCount} Events, ${import.claimCount} Claims. ${import.parsed.size} files parsed, " +
                        "${import.skipped.size} skipped, ${import.failed.size} failed.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!import.isComplete) {
                    Text(
                        "Incomplete: this run did not finish. Delete it or import the Export again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Preview
@Composable
private fun ImportScreenPreview() {
    val finished =
        ImportUi(
            id = ImportId(1),
            exportName = "instagram-me-2026-10-20",
            sourceName = "Instagram",
            startedAt = "2026-10-27 10:12",
            isComplete = true,
            eventCount = 4,
            claimCount = 0,
            parsed = listOf(FileResultUi("ads_information/ads_viewed.json", "4 records: 4 Events, 0 Claims")),
            skipped = listOf(FileResultUi("personal_information/notes.txt", "not a JSON file")),
            failed = listOf(FileResultUi("activity/liked_posts.json", "could not decode JSON")),
        )
    MaterialTheme {
        ImportScreen(
            state =
                ImportState(
                    canPickZip = true,
                    canPickFolder = true,
                    imports = listOf(finished),
                    run = ImportRunUi.Finished(finished),
                    part = ExportPart(number = 2, count = 3),
                ),
            onAction = {},
            onShowTimeline = {},
        )
    }
}
