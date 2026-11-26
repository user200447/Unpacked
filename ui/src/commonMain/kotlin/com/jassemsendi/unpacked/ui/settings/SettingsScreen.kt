package com.jassemsendi.unpacked.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jassemsendi.unpacked.agent.ProviderChoice
import com.jassemsendi.unpacked.ui.theme.ErrorNotice
import com.jassemsendi.unpacked.ui.theme.SectionCard
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Suppress("ParamsComparedByRef")
@Composable
fun SettingsRoot(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

/** The provider choice, the Gemini key, and the tested Exports, each in its own card. */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
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
        state.error?.let { error -> item { ErrorNotice(error) } }
        state.notice?.let { notice ->
            item { Text(notice, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.readable()) }
        }
        if (state.isLoading) {
            item { CircularProgressIndicator() }
        } else {
            item { ProviderCard(state, onAction) }
            item { KeyCard(state, onAction) }
        }
        item { TestedExportsCard(state.testedExports) }
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
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text("How the agent runs, and what leaves this device", style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onBack) { Text("Timeline") }
    }
}

@Composable
private fun ProviderCard(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
) {
    SectionCard(title = "Where the agent runs", modifier = Modifier.readable()) {
        state.choices.forEach { choice ->
            val selected = choice.choice == state.provider
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected) { onAction(SettingsAction.ChooseProvider(choice.choice)) },
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalAlignment = Alignment.Top,
            ) {
                RadioButton(selected = selected, onClick = null)
                Column {
                    Text(choice.label, style = MaterialTheme.typography.titleSmall)
                    Text(choice.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.provider == ProviderChoice.Cloud && !state.hasKey) {
            Text("The cloud model needs a key; paste one below.", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun KeyCard(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
) {
    SectionCard(title = "Gemini key", modifier = Modifier.readable()) {
        Text(
            (if (state.hasKey) "A key is saved" else "No key is saved") +
                ", in ${state.keyStorage}. The key is never shown again here.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = state.keyDraft,
            onValueChange = { onAction(SettingsAction.KeyChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSavingKey,
            label = { Text(if (state.hasKey) "Replace the key" else "Paste a key from Google AI Studio") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Button(
                onClick = { onAction(SettingsAction.SaveKey) },
                enabled = !state.isSavingKey && state.keyDraft.isNotBlank(),
            ) { Text("Save key") }
            if (state.hasKey) {
                OutlinedButton(onClick = { onAction(SettingsAction.RemoveKey) }) { Text("Remove key") }
            }
        }
    }
}

/** The Exports the app was tested against (spec story 55), from the adapters' one declaration each. */
@Composable
private fun TestedExportsCard(tested: List<TestedExportUi>) {
    SectionCard(title = "Tested Exports", modifier = Modifier.readable()) {
        Text(
            "An Export of a later date may have changed shape; whatever the Import does not understand is still " +
                "shown in the Explorer.",
            style = MaterialTheme.typography.bodySmall,
        )
        tested.forEach { export ->
            Column {
                Text(
                    "${export.sourceName}, ${export.format}, downloaded ${export.downloadedOn}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(export.note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(
            state =
                SettingsState(
                    isLoading = false,
                    choices = ProviderChoice.entries.map { it.toUi() },
                    provider = ProviderChoice.Local,
                    hasKey = true,
                    keyStorage = "the macOS Keychain",
                    testedExports = listOf(TestedExportUi("Instagram", "JSON", "2026-10-20", "All time, JSON format.")),
                ),
            onAction = {},
            onBack = {},
        )
    }
}
