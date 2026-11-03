package com.jassemsendi.unpacked.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jassemsendi.unpacked.insights.Opens
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The Insight cards side by side, scrolled across; every number on them opens what it counted. */
@Composable
internal fun InsightCardRow(
    cards: List<InsightCardUi>,
    onOpen: (Opens) -> Unit,
) {
    LazyRow(modifier = Modifier.readable(), horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        items(cards, key = { it.id }) { card -> InsightCardView(card, onOpen) }
    }
}

@Composable
private fun InsightCardView(
    card: InsightCardUi,
    onOpen: (Opens) -> Unit,
) {
    Card(modifier = Modifier.width(CARD_WIDTH)) {
        Column(modifier = Modifier.padding(Spacing.medium), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium)
            val emptyText = card.emptyText
            if (emptyText != null) {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium)
            } else {
                FigureLine(card.headline, MaterialTheme.typography.displaySmall, onOpen)
                card.details.forEach { FigureLine(it, MaterialTheme.typography.titleLarge, onOpen) }
                card.rows.forEach { NameLine(it, onOpen) }
            }
        }
    }
}

/** A number in the primary colour with its caption beside it; the whole line opens what the number counted. */
@Composable
private fun FigureLine(
    figure: FigureUi,
    style: TextStyle,
    onOpen: (Opens) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(figure.opens) },
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(figure.value, style = style, color = MaterialTheme.colorScheme.primary)
        Text(figure.caption, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NameLine(
    row: CardRowUi,
    onOpen: (Opens) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(row.opens) },
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Text(
            row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(weight = 1f),
        )
        Text(row.detail, style = MaterialTheme.typography.bodySmall)
    }
}

private val CARD_WIDTH = 280.dp

@Preview
@Composable
private fun InsightCardRowPreview() {
    MaterialTheme {
        InsightCardRow(
            cards =
                listOf(
                    InsightCardUi(
                        id = "ads",
                        title = "Ads shown to you",
                        headline = FigureUi("12", "ads shown", Opens.Timeline(EventKind.AdShown)),
                        details = listOf(FigureUi("4", "from advertisers", Opens.Timeline(EventKind.AdShown))),
                        rows =
                            listOf(
                                CardRowUi(
                                    "Example Courier",
                                    "5 ads",
                                    Opens.Entity("instagram:advertiser:examplecourier"),
                                ),
                            ),
                        emptyText = null,
                    ),
                    InsightCardUi(
                        id = "logins",
                        title = "Your logins",
                        headline = FigureUi("0", "logins", Opens.Timeline(EventKind.Login)),
                        details = emptyList(),
                        rows = emptyList(),
                        emptyText = "No logins in your library yet.",
                    ),
                ),
            onOpen = {},
        )
    }
}
