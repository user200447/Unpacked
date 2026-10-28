package com.jassemsendi.unpacked.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.ui.home.RecordDetailLayout
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.RecordTimeUi
import com.jassemsendi.unpacked.ui.home.text
import com.jassemsendi.unpacked.ui.theme.ErrorNotice
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchRoot(
    onBack: () -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

/** The search box and its hits, with the opened Record beside or over them. */
@Composable
fun SearchScreen(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RecordDetailLayout(detail = state.detail, onClose = { onAction(SearchAction.CloseDetail) }, modifier = modifier) {
        Hits(state, onAction, onBack, modifier = it)
    }
}

@Composable
private fun Hits(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
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
        item { SearchBox(state.text) { onAction(SearchAction.TextChanged(it)) } }
        state.error?.let { error -> item { ErrorNotice(error) } }
        when {
            state.text.isBlank() -> {
                item { Hint() }
            }

            state.isSearching && state.hits.isEmpty() -> {
                item { CircularProgressIndicator() }
            }

            state.hits.isEmpty() && state.error == null -> {
                item {
                    Text(
                        "Nothing in your library mentions “${state.text.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.readable(),
                    )
                }
            }
        }
        items(state.hits, key = { it.ref.listKey() }) { hit ->
            HitCard(hit, selected = state.detail?.ref == hit.ref) { onAction(SearchAction.Open(hit.ref)) }
        }
        if (state.hasMore) {
            item {
                // Reaching the end of what was read asks for the next page; the spinner stays until it lands.
                LaunchedEffect(state.hits.size) { onAction(SearchAction.NextPage) }
                CircularProgressIndicator()
            }
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
        Column(modifier = Modifier.weight(weight = 1f)) {
            Text("Search your library", style = MaterialTheme.typography.headlineMedium)
            Text("Events and Claims that mention your words, newest first", style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onBack) { Text("Timeline") }
    }
}

@Composable
private fun SearchBox(
    text: String,
    onTextChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChanged,
        modifier = Modifier.readable(),
        singleLine = true,
        label = { Text("Words to find") },
        placeholder = { Text("A name, a place, a word from a caption") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun Hint() {
    Column(modifier = Modifier.readable(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text("Type a word to begin.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Every word you type must appear in the record, and each matches from its start: " +
                "“cour” finds “courier”. Hits are listed newest first, not by relevance.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun HitCard(
    hit: SearchHitUi,
    selected: Boolean,
    onOpen: () -> Unit,
) {
    val colors =
        if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    val provenance = hit.ref.provenance
    Card(modifier = Modifier.readable().clickable(onClick = onOpen), colors = colors) {
        Column(
            modifier = Modifier.padding(Spacing.medium).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.small / 2),
        ) {
            Text(hit.time.text(), style = MaterialTheme.typography.bodySmall)
            Text(hit.kindLabel, style = MaterialTheme.typography.titleMedium)
            if (hit.summary.isNotEmpty()) {
                Text(hit.summary, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "${provenance.path}, record ${provenance.recordIndex}" +
                    if (provenance.itemIndex > 0) ", item ${provenance.itemIndex}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A list key the platform can save: an Event and a Claim read from the same record share a Provenance hash. */
private fun RecordRef.listKey(): String =
    when (this) {
        is RecordRef.Event -> "event:${provenance.hash}"
        is RecordRef.Claim -> "claim:${provenance.hash}"
    }

@Preview
@Composable
private fun SearchScreenPreview() {
    val export = ExportId("instagram-me")
    MaterialTheme {
        SearchScreen(
            state =
                SearchState(
                    text = "courier",
                    hits =
                        listOf(
                            SearchHitUi(
                                ref = RecordRef.Claim(Provenance(export, "ads_information/advertisers.json", 0, 2)),
                                kindLabel = "Advertiser holds contact",
                                time = RecordTimeUi.Undated,
                                summary = "Example Courier",
                            ),
                            SearchHitUi(
                                ref = RecordRef.Event(Provenance(export, "ads_information/ads_viewed.json", 1)),
                                kindLabel = "Ad shown",
                                time = RecordTimeUi.Happened("2026-10-27 10:12"),
                                summary = "Example Courier",
                            ),
                        ),
                ),
            onAction = {},
            onBack = {},
        )
    }
}
