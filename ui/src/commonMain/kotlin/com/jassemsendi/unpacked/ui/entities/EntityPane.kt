package com.jassemsendi.unpacked.ui.entities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.ui.home.AttributeUi
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.RecordRow
import com.jassemsendi.unpacked.ui.home.RecordRowUi
import com.jassemsendi.unpacked.ui.home.RecordTimeUi
import com.jassemsendi.unpacked.ui.home.listKey
import com.jassemsendi.unpacked.ui.theme.ErrorNotice
import com.jassemsendi.unpacked.ui.theme.SectionCard
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The opened Entity: what the Source recorded about it, every Claim naming it, and its Events newest first in pages. */
@Composable
internal fun EntityPane(
    opened: EntityDetailUi,
    selected: RecordRef?,
    error: String?,
    onAction: (EntitiesAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { PaneHeader(opened.entity) { onAction(EntitiesAction.CloseEntity) } }
        error?.let { item { ErrorNotice(it) } }
        if (opened.attributes.isNotEmpty()) {
            item { Attributes(opened.attributes) }
        }
        section("Claims", opened.claims, selected, onAction)
        section("Events, newest first", opened.events, selected, onAction)
        if (opened.hasMoreEvents) {
            item {
                LaunchedEffect(opened.events.size) { onAction(EntitiesAction.MoreEvents) }
                CircularProgressIndicator()
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    rows: List<RecordRowUi>,
    selected: RecordRef?,
    onAction: (EntitiesAction) -> Unit,
) {
    item(key = "title:$title") {
        Text(
            if (rows.isEmpty()) "$title: none" else title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.readable(),
        )
    }
    items(rows, key = { it.ref.listKey() }) { row ->
        RecordRow(row, selected = selected == row.ref) { onAction(EntitiesAction.Open(row.ref)) }
    }
}

@Composable
private fun PaneHeader(
    entity: EntityRowUi,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.readable(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(weight = 1f)) {
            Text(entity.name, style = MaterialTheme.typography.headlineMedium)
            Text("${entity.kindLabel} on ${entity.sourceName}", style = MaterialTheme.typography.bodyMedium)
            Text(entity.references(), style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onClose) { Text("Entities") }
    }
}

@Composable
private fun Attributes(attributes: List<AttributeUi>) {
    SectionCard("What the Source recorded", modifier = Modifier.readable()) {
        attributes.forEach { attribute ->
            Text(attribute.label, style = MaterialTheme.typography.labelMedium)
            Text(attribute.value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview
@Composable
private fun EntityPanePreview() {
    val export = ExportId("instagram-me")
    val courier =
        EntityRowUi(
            id = "instagram:advertiser:examplecourier",
            name = "Example Courier",
            kindLabel = "Advertiser",
            sourceName = "Instagram",
            eventCount = 1,
            claimCount = 1,
        )
    MaterialTheme {
        EntitiesScreen(
            state =
                EntitiesState(
                    isLoading = false,
                    totalCount = 1,
                    opened =
                        EntityDetailUi(
                            entity = courier,
                            attributes = listOf(AttributeUi("username", "examplecourier")),
                            events =
                                listOf(
                                    RecordRowUi(
                                        ref = RecordRef.Event(Provenance(export, "ads_information/ads_viewed.json", 1)),
                                        kindLabel = "Ad shown",
                                        time = RecordTimeUi.Happened("2026-10-27 10:12"),
                                        summary = "Example Courier",
                                    ),
                                ),
                            hasMoreEvents = false,
                            claims =
                                listOf(
                                    RecordRowUi(
                                        ref =
                                            RecordRef.Claim(
                                                Provenance(export, "ads_information/advertisers.json", 0, 2),
                                            ),
                                        kindLabel = "Advertiser holds contact",
                                        time = RecordTimeUi.Undated,
                                        summary = "Example Courier",
                                    ),
                                ),
                        ),
                ),
            onAction = {},
            onBack = {},
        )
    }
}
