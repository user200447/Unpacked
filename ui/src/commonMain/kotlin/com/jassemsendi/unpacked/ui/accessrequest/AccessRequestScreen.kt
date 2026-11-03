package com.jassemsendi.unpacked.ui.accessrequest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.ui.theme.ErrorNotice
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AccessRequestRoot(
    importId: ImportId,
    onBack: () -> Unit,
    viewModel: AccessRequestViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The report is read again on every appearance: the Import may have been replaced since the letter was last drafted.
    LaunchedEffect(viewModel, importId) { viewModel.onAction(AccessRequestAction.Load(importId)) }
    AccessRequestScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

/** The letter drafted from one Import's Gap report, with the ways to take it out of the app. */
@Composable
fun AccessRequestScreen(
    state: AccessRequestState,
    onAction: (AccessRequestAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Header(onBack) }
        state.error?.let { error -> item { ErrorNotice(error) } }
        val request = state.request
        when {
            request != null -> {
                item { Summary(request) }
                item { Actions(request, state.canSave, state.savedTo) { onAction(AccessRequestAction.Save) } }
                item { RequestText(request.text) }
            }

            state.isLoading -> {
                item { CircularProgressIndicator() }
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
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text("Access request", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A letter to the Source asking for what the Gap report found Missing or Partial, " +
                    "drafted from the report and nothing else. Add your name before sending it.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onBack) { Text("Gap report") }
    }
}

@Composable
private fun Summary(request: AccessRequestUi) {
    Column(modifier = Modifier.readable(), verticalArrangement = Arrangement.spacedBy(Spacing.small / 2)) {
        Text("To ${request.sourceName}, about ${request.exportName}", style = MaterialTheme.typography.titleMedium)
        Text(request.summary, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Copy goes through the platform clipboard Compose provides; saving through the saver the app bound. */
@Composable
private fun Actions(
    request: AccessRequestUi,
    canSave: Boolean,
    savedTo: String?,
    onSave: () -> Unit,
) {
    // The replacement, Clipboard, has no common way to build a text ClipEntry in Compose Multiplatform 1.12.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember(request.text) { mutableStateOf(false) }
    Column(modifier = Modifier.readable(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(request.text))
                    copied = true
                },
            ) { Text("Copy") }
            if (canSave) {
                OutlinedButton(onClick = onSave) { Text("Save as text") }
            }
        }
        when {
            savedTo != null -> {
                Text("Saved to $savedTo", style = MaterialTheme.typography.bodySmall)
            }

            copied -> {
                Text("Copied to the clipboard.", style = MaterialTheme.typography.bodySmall)
            }

            !canSave -> {
                Text(
                    "Saving as a file is not available on this platform yet; copy the letter instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RequestText(text: String) {
    Card(modifier = Modifier.readable()) {
        SelectionContainer {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Spacing.medium))
        }
    }
}

@Preview
@Composable
private fun AccessRequestScreenPreview() {
    MaterialTheme {
        AccessRequestScreen(
            state =
                AccessRequestState(
                    isLoading = false,
                    canSave = true,
                    request =
                        AccessRequestUi(
                            exportName = "instagram-me",
                            sourceName = "Instagram",
                            summary = "Asks for 2 of the nine items: 15(1)(c), 15(1)(d).",
                            text =
                                "Subject: Access request under Article 15 GDPR\n\n" +
                                    "To the data protection officer of Instagram,\n\n" +
                                    "- Article 15(1)(c): the recipients (provided only in part).\n" +
                                    "- Article 15(1)(d): how long my data will be stored (not provided).",
                            fileName = "access-request-instagram-me.txt",
                        ),
                ),
            onAction = {},
            onBack = {},
        )
    }
}
