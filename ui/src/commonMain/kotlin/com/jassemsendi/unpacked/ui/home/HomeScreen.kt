package com.jassemsendi.unpacked.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoot(
    onImport: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Imports may have come or gone while another screen was up, so every appearance re-reads the library.
    LaunchedEffect(viewModel) { viewModel.onAction(HomeAction.Refresh) }
    HomeScreen(state = state, onAction = viewModel::onAction, onImport = onImport)
}

/**
 * The timeline, with the opened Record beside it on a wide window and over it on a narrow one. The
 * timeline stays composed underneath, so closing the Record returns to the same place in the list.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    val close = { onAction(HomeAction.CloseDetail) }
    BackHandler(enabled = detail != null, onBack = close)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= TWO_PANE_MIN_WIDTH
        Row(modifier = Modifier.fillMaxSize()) {
            Timeline(state, onAction, onImport, modifier = Modifier.weight(weight = 1f))
            if (detail != null && sideBySide) {
                RecordDetailPane(detail, onClose = close, modifier = Modifier.width(DETAIL_PANE_WIDTH).fillMaxSize())
            }
        }
        if (detail != null && !sideBySide) {
            Surface(modifier = Modifier.fillMaxSize()) {
                RecordDetailPane(detail, onClose = close, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun Timeline(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Header(state.totalCount, onImport) }
        if (state.kinds.isNotEmpty()) {
            item { KindFilter(state.kinds, state.totalCount, state.filter) { onAction(HomeAction.Filter(it)) } }
        }
        when {
            state.isLoading && state.entries.isEmpty() -> {
                item { CircularProgressIndicator() }
            }

            state.totalCount == 0 -> {
                item { EmptyNotice() }
            }

            state.entries.isEmpty() -> {
                item { Text("No Events of this Kind.", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        items(state.entries, key = { it.ref.provenance.hash }) { entry ->
            EntryCard(entry, selected = state.detail?.ref == entry.ref) { onAction(HomeAction.Open(entry.ref)) }
        }
        if (state.hasMore) {
            item {
                // Reaching the end of what was read asks for the next page; the spinner stays until it lands.
                LaunchedEffect(state.entries.size) { onAction(HomeAction.NextPage) }
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun Header(
    totalCount: Int,
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier.readable(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(weight = 1f)) {
            Text("Your timeline", style = MaterialTheme.typography.headlineMedium)
            Text("$totalCount Events, newest first", style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onImport) { Text("Imports") }
    }
}

@Composable
private fun KindFilter(
    kinds: List<KindCountUi>,
    totalCount: Int,
    filter: EventKind?,
    onFilter: (EventKind?) -> Unit,
) {
    Row(
        modifier = Modifier.readable().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        FilterChip(selected = filter == null, onClick = { onFilter(null) }, label = { Text("All ($totalCount)") })
        kinds.forEach { kind ->
            FilterChip(
                selected = filter == kind.kind,
                onClick = { onFilter(kind.kind) },
                label = { Text("${kind.label} (${kind.count})") },
            )
        }
    }
}

@Composable
private fun EmptyNotice() {
    Column(modifier = Modifier.readable(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text("Nothing to show yet.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Import an Export and every Event in it lands here, newest first.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EntryCard(
    entry: TimelineEntryUi,
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
            Text(entry.time, style = MaterialTheme.typography.bodySmall)
            Text(entry.kindLabel, style = MaterialTheme.typography.titleMedium)
            if (entry.summary.isNotEmpty()) {
                Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Raw kind: ${entry.rawKind}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val TWO_PANE_MIN_WIDTH = 840.dp
private val DETAIL_PANE_WIDTH = 360.dp

@Preview
@Composable
private fun HomeScreenPreview() {
    val entry =
        TimelineEntryUi(
            ref = RecordRef.Event(Provenance(ExportId("instagram-me"), "ads_information/ads_viewed.json", 0)),
            kindLabel = "Ad shown",
            time = "2026-10-27 10:12",
            summary = "Example Courier",
            rawKind = "ads_viewed",
        )
    MaterialTheme {
        HomeScreen(
            state =
                HomeState(
                    isLoading = false,
                    totalCount = 3,
                    kinds = listOf(KindCountUi(EventKind.AdShown, "Ad shown", count = 3)),
                    entries = listOf(entry),
                ),
            onAction = {},
            onImport = {},
        )
    }
}
