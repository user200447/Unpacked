package com.jassemsendi.unpacked.ui.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jassemsendi.unpacked.model.Source
import com.jassemsendi.unpacked.ui.theme.Spacing
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * What a first-time user reads before handing over an Export: the privacy guarantee and how to
 * request an Export from each supported Source. Static, so it has no state and no ViewModel.
 */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = Spacing.readableWidth).padding(Spacing.large),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            Text("Welcome to Unpacked", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Unpacked turns the archive a platform hands you under your right of access into a " +
                    "searchable timeline, and shows what that archive leaves out.",
                style = MaterialTheme.typography.bodyLarge,
            )
            PrivacyGuarantee()
            Text("Request your Export", style = MaterialTheme.typography.titleLarge)
            Source.entries.forEach { source -> RequestInstructions(source) }
            Button(onClick = onContinue) { Text("Choose an Export") }
        }
    }
}

@Composable
private fun PrivacyGuarantee() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text("Your data never leaves this device", style = MaterialTheme.typography.titleMedium)
            Text(
                "Unpacked reads your Export here, keeps everything it finds in a library on this device, " +
                    "and sends nothing anywhere. Deleting an Import removes everything it produced.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RequestInstructions(source: Source) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(source.name, style = MaterialTheme.typography.titleMedium)
        exportInstructions(source).forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium)
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview
@Composable
private fun WelcomeScreenPreview() {
    MaterialTheme { WelcomeScreen(onContinue = {}) }
}
