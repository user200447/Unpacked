package com.jassemsendi.unpacked.ui.entities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jassemsendi.unpacked.data.EntityOrder
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.ui.home.KindCountUi
import com.jassemsendi.unpacked.ui.home.KindFilter
import com.jassemsendi.unpacked.ui.home.RecordDetailLayout
import com.jassemsendi.unpacked.ui.home.counted
import com.jassemsendi.unpacked.ui.theme.ErrorNotice
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EntitiesRoot(
    onBack: () -> Unit,
    viewModel: EntitiesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Imports may have come or gone while another screen was up, so every appearance re-reads the library.
    LaunchedEffect(viewModel) { viewModel.onAction(EntitiesAction.Refresh) }
    EntitiesScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

/** The Entity list, or the opened Entity's own timeline and Claims, with the opened Record beside or over it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EntitiesScreen(
    state: EntitiesState,
    onAction: (EntitiesAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Back closes the Record first (the layout handles that), then the Entity, then leaves the screen.
    BackHandler(enabled = state.opened != null && state.detail == null) { onAction(EntitiesAction.CloseEntity) }
    RecordDetailLayout(detail = state.detail, onClose = { onAction(EntitiesAction.CloseDetail) }, modifier = modifier) {
        val opened = state.opened
        if (opened == null) {
            EntityList(state, onAction, onBack, modifier = it)
        } else {
            EntityPane(opened, state.detail?.ref, state.error, onAction, modifier = it)
        }
    }
}

@Composable
private fun EntityList(
    state: EntitiesState,
    onAction: (EntitiesAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { ListHeader(state.totalCount, onBack) }
        state.error?.let { error -> item { ErrorNotice(error) } }
        if (state.kinds.isNotEmpty()) {
            item { KindFilter(state.kinds, state.totalCount, state.filter) { onAction(EntitiesAction.Filter(it)) } }
            item { OrderChips(state.order) { onAction(EntitiesAction.Sort(it)) } }
        }
        when {
            state.isLoading && state.entities.isEmpty() -> item { CircularProgressIndicator() }
            state.totalCount == 0 -> item { EmptyNotice() }
        }
        items(state.entities, key = { it.id }) { entity ->
            EntityCard(entity) { onAction(EntitiesAction.OpenEntity(entity.id)) }
        }
        if (state.hasMore) {
            item {
                // Reaching the end of what was read asks for the next page; the spinner stays until it lands.
                LaunchedEffect(state.entities.size) { onAction(EntitiesAction.NextPage) }
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun ListHeader(
    totalCount: Int,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.readable(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(weight = 1f)) {
            Text("Entities", style = MaterialTheme.typography.headlineMedium)
            Text(
                "${counted(totalCount, "named thing")} in your library, each kept to its Source",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onBack) { Text("Timeline") }
    }
}

@Composable
private fun OrderChips(
    order: EntityOrder,
    onOrder: (EntityOrder) -> Unit,
) {
    Row(modifier = Modifier.readable(), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        FilterChip(
            selected = order == EntityOrder.MostReferenced,
            onClick = { onOrder(EntityOrder.MostReferenced) },
            label = { Text("Most named first") },
        )
        FilterChip(
            selected = order == EntityOrder.Name,
            onClick = { onOrder(EntityOrder.Name) },
            label = { Text("By name") },
        )
    }
}

@Composable
private fun EmptyNotice() {
    Column(modifier = Modifier.readable(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text("Nothing named yet.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Import an Export and the advertisers, accounts, devices, topics and places it names land here.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** One Entity in the list: its name, its Kind on its Source, and how often it is named. */
@Composable
private fun EntityCard(
    entity: EntityRowUi,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.readable().clickable(onClick = onOpen)) {
        Column(
            modifier = Modifier.padding(Spacing.medium).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.small / 2),
        ) {
            Text(entity.name, style = MaterialTheme.typography.titleMedium)
            Text("${entity.kindLabel} on ${entity.sourceName}", style = MaterialTheme.typography.bodySmall)
            Text(entity.references(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview
@Composable
private fun EntitiesScreenPreview() {
    MaterialTheme {
        EntitiesScreen(
            state =
                EntitiesState(
                    isLoading = false,
                    totalCount = 2,
                    kinds = listOf(KindCountUi(EntityKind.Advertiser, "Advertiser", 2)),
                    entities =
                        listOf(
                            EntityRowUi(
                                id = "instagram:advertiser:examplecourier",
                                name = "Example Courier",
                                kindLabel = "Advertiser",
                                sourceName = "Instagram",
                                eventCount = 3,
                                claimCount = 1,
                            ),
                            EntityRowUi(
                                id = "instagram:advertiser:examplebakery",
                                name = "Example Bakery",
                                kindLabel = "Advertiser",
                                sourceName = "Instagram",
                                eventCount = 1,
                                claimCount = 0,
                            ),
                        ),
                ),
            onAction = {},
            onBack = {},
        )
    }
}
