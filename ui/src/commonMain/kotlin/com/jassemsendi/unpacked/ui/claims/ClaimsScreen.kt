package com.jassemsendi.unpacked.ui.claims

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.ui.home.KindCountUi
import com.jassemsendi.unpacked.ui.home.RecordDetailLayout
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.RecordRow
import com.jassemsendi.unpacked.ui.home.RecordRowUi
import com.jassemsendi.unpacked.ui.home.RecordTimeUi
import com.jassemsendi.unpacked.ui.home.counted
import com.jassemsendi.unpacked.ui.home.listKey
import com.jassemsendi.unpacked.ui.theme.ErrorNotice
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ClaimsRoot(
    onBack: () -> Unit,
    viewModel: ClaimsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Imports may have come or gone while another screen was up, so every appearance re-reads the library.
    LaunchedEffect(viewModel) { viewModel.onAction(ClaimsAction.Refresh) }
    ClaimsScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

/** The Claim groups, one per Kind, with the opened Record beside or over them. */
@Composable
fun ClaimsScreen(
    state: ClaimsState,
    onAction: (ClaimsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RecordDetailLayout(detail = state.detail, onClose = { onAction(ClaimsAction.CloseDetail) }, modifier = modifier) {
        Groups(state, onAction, onBack, modifier = it)
    }
}

@Composable
private fun Groups(
    state: ClaimsState,
    onAction: (ClaimsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Header(state.totalCount, onBack) }
        state.error?.let { error -> item { ErrorNotice(error) } }
        when {
            state.isLoading && state.kinds.isEmpty() -> item { CircularProgressIndicator() }
            state.kinds.isEmpty() -> item { EmptyNotice() }
        }
        state.kinds.forEach { group ->
            val expanded = state.expanded == group.kind
            item(key = "group:${group.kind}") {
                GroupCard(group, expanded) { onAction(ClaimsAction.Expand(if (expanded) null else group.kind)) }
            }
            if (expanded) {
                items(state.claims, key = { it.ref.listKey() }) { claim ->
                    RecordRow(
                        claim,
                        selected = state.detail?.ref == claim.ref,
                    ) { onAction(ClaimsAction.Open(claim.ref)) }
                }
                if (state.hasMore) {
                    item(key = "more:${group.kind}") {
                        // Reaching the end of what was read asks for the next page, and again whenever a read ends with
                        // the end still in view, even one that listed nothing new; the spinner stays until it lands.
                        LaunchedEffect(state.claims.size, state.isLoading, state.isReadingNextPage) {
                            onAction(ClaimsAction.NextPage)
                        }
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    totalCount: Int,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.readable(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(weight = 1f)) {
            Text("What the Source believes", style = MaterialTheme.typography.headlineMedium)
            Text(
                "${counted(totalCount, "Claim")} about you, grouped by Kind",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onBack) { Text("Timeline") }
    }
}

@Composable
private fun EmptyNotice() {
    Column(modifier = Modifier.readable(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text("No Claims yet.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Import an Export and what the Source infers, remembers and was told about you lands here, by Kind.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** One Kind of Claim with its count; opens to list its Claims beneath it. */
@Composable
private fun GroupCard(
    group: KindCountUi<ClaimKind>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors =
        if (expanded) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    Card(modifier = Modifier.readable().clickable(onClick = onToggle), colors = colors) {
        Row(
            modifier = Modifier.padding(Spacing.medium).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(group.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(weight = 1f))
            Text(counted(group.count, "Claim"), style = MaterialTheme.typography.bodyMedium)
            Text(if (expanded) "Hide" else "Show", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private const val TOTAL_IN_PREVIEW = 5

@Preview
@Composable
private fun ClaimsScreenPreview() {
    val export = ExportId("instagram-me")
    MaterialTheme {
        ClaimsScreen(
            state =
                ClaimsState(
                    isLoading = false,
                    totalCount = TOTAL_IN_PREVIEW,
                    kinds =
                        listOf(
                            KindCountUi(ClaimKind.AdvertiserHoldsContact, "Advertiser holds contact", count = 3),
                            KindCountUi(ClaimKind.InferredInterest, "Inferred interest", count = 2),
                        ),
                    expanded = ClaimKind.InferredInterest,
                    claims =
                        listOf(
                            RecordRowUi(
                                ref = RecordRef.Claim(Provenance(export, "ads_information/ads_interests.json", 0, 1)),
                                kindLabel = "Inferred interest",
                                time = RecordTimeUi.LastUpdated("2026-10-27 10:17"),
                                summary = "Sourdough baking",
                            ),
                            RecordRowUi(
                                ref =
                                    RecordRef.Claim(
                                        Provenance(export, "preferences/your_topics/your_topics.json", 0),
                                    ),
                                kindLabel = "Inferred interest",
                                time = RecordTimeUi.Undated,
                                summary = "Sourdough",
                            ),
                        ),
                ),
            onAction = {},
            onBack = {},
        )
    }
}
