package com.jassemsendi.unpacked.ui.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.ui.home.RecordDetailLayout
import com.jassemsendi.unpacked.ui.theme.ErrorNotice
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Suppress("ParamsComparedByRef")
@Composable
fun AskRoot(
    onBack: () -> Unit,
    onGapReport: () -> Unit,
    viewModel: AskViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AskScreen(state = state, onAction = viewModel::onAction, onBack = onBack, onGapReport = onGapReport)
}

/** The question, the answer with its citations and Tool trace, and the Record a citation opened beside or over them. */
@Composable
fun AskScreen(
    state: AskState,
    onAction: (AskAction) -> Unit,
    onBack: () -> Unit,
    onGapReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RecordDetailLayout(detail = state.detail, onClose = { onAction(AskAction.CloseDetail) }, modifier = modifier) {
        Conversation(state, onAction, onBack, onGapReport, modifier = it)
    }
}

@Composable
private fun Conversation(
    state: AskState,
    onAction: (AskAction) -> Unit,
    onBack: () -> Unit,
    onGapReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Header(onBack) }
        when (val model = state.model) {
            ModelUi.Checking -> {
                item { CircularProgressIndicator() }
            }

            is ModelUi.Off -> {
                item { OffNotice(model.message, onBack, onGapReport) { onAction(AskAction.CheckModel) } }
            }

            is ModelUi.Ready -> {
                item { QuestionBox(state.question, enabled = !state.isAsking, onAction) }
                item { ModelLine(model.marker) }
            }
        }
        state.error?.let { error -> item { ErrorNotice(error) } }
        if (state.isAsking) item { CircularProgressIndicator() }
        when (val outcome = state.outcome) {
            is OutcomeUi.Answered -> {
                item { AnswerCard(outcome.answer) { onAction(AskAction.OpenCitation(it)) } }
                outcome.answer.chart?.let { chart -> item { ChartCard(chart) } }
                item { TraceCard(outcome.answer.trace) }
            }

            is OutcomeUi.Stopped -> {
                item { ErrorNotice(outcome.message) }
                item { TraceCard(outcome.trace) }
            }

            null -> {
                if (state.model is ModelUi.Ready && !state.isAsking) item { Hint() }
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
            Text("Ask your library", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A question in your own words; every number in the answer is backed by a Tool call you can read",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onBack) { Text("Timeline") }
    }
}

/** Why the agent is off, and the two places that answer without it (spec story 44). */
@Composable
private fun OffNotice(
    message: String,
    onHome: () -> Unit,
    onGapReport: () -> Unit,
    onCheckAgain: () -> Unit,
) {
    Card(
        modifier = Modifier.readable(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(Spacing.medium), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text("The agent is off", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Button(onClick = onHome) { Text("Insight cards") }
                Button(onClick = onGapReport) { Text("Gap report") }
                OutlinedButton(onClick = onCheckAgain) { Text("Check again") }
            }
        }
    }
}

@Composable
private fun QuestionBox(
    question: String,
    enabled: Boolean,
    onAction: (AskAction) -> Unit,
) {
    Row(
        modifier = Modifier.readable(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = question,
            onValueChange = { onAction(AskAction.QuestionChanged(it)) },
            modifier = Modifier.weight(weight = 1f),
            enabled = enabled,
            label = { Text("Your question") },
            placeholder = { Text("How many ads did I see in October, and from whom?") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onAction(AskAction.Ask) }),
        )
        Button(onClick = { onAction(AskAction.Ask) }, enabled = enabled && question.isNotBlank()) { Text("Ask") }
    }
}

@Composable
private fun ModelLine(marker: String) {
    Text(
        "Answers come from $marker; your data stays on this device.",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.readable(),
    )
}

@Composable
private fun Hint() {
    Column(modifier = Modifier.readable(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text("Ask about what your Exports hold.", style = MaterialTheme.typography.titleMedium)
        Text(
            "The model only knows what the Tools return, and the answer cites the records behind each number: " +
                "a marker such as [1] opens the record. A number the Tools never returned is flagged.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview
@Composable
private fun AskScreenPreview() {
    val export = ExportId("instagram-me")
    MaterialTheme {
        AskScreen(
            state =
                AskState(
                    question = "How many ads did I see?",
                    model = ModelUi.Ready("local · qwen3:8b"),
                    outcome =
                        OutcomeUi.Answered(
                            AnswerUi(
                                text = "You were shown 4 ads [1]; Example Courier placed 2 of them [2].",
                                citations =
                                    listOf(
                                        CitationUi(1, Provenance(export, "ads_information/ads_viewed.json", 2)),
                                        CitationUi(2, Provenance(export, "ads_information/ads_viewed.json", 1)),
                                    ),
                                trace =
                                    listOf(
                                        TraceStepUi(
                                            number = 1,
                                            tool = "countEvents",
                                            input = """{"kind":"AdShown"}""",
                                            output = """{"total":4,"cited":["[1]"]}""",
                                            failed = false,
                                        ),
                                    ),
                                modelMarker = "local · qwen3:8b",
                                unverifiedNumbers = emptyList(),
                                chart = null,
                            ),
                        ),
                ),
            onAction = {},
            onBack = {},
            onGapReport = {},
        )
    }
}
