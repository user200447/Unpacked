package com.jassemsendi.unpacked.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jassemsendi.unpacked.ui.theme.Spacing

// The part of the Import screen that follows one run: progress file by file, then the results.

@Composable
internal fun RunPanel(run: ImportRunUi) {
    when (run) {
        ImportRunUi.Idle -> {
            Unit
        }

        is ImportRunUi.Running -> {
            RunningPanel(run)
        }

        is ImportRunUi.Finished -> {
            FinishedPanel(run.import)
        }

        is ImportRunUi.Failed -> {
            NoticeCard(
                title = "Could not import ${run.exportName}",
                body = run.reason,
                isError = true,
            )
        }
    }
}

@Composable
private fun RunningPanel(run: ImportRunUi.Running) {
    Card(modifier = Modifier.readable()) {
        Column(modifier = Modifier.padding(Spacing.medium), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text("Importing ${run.exportName}", style = MaterialTheme.typography.titleMedium)
            if (run.filesTotal == 0) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Listing the files in the Export", style = MaterialTheme.typography.bodyMedium)
            } else {
                LinearProgressIndicator(
                    progress = { run.filesDone.toFloat() / run.filesTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("File ${run.filesDone + 1} of ${run.filesTotal}", style = MaterialTheme.typography.bodyMedium)
            }
            run.currentFile?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            run.files.forEach { FileLine(it) }
        }
    }
}

@Composable
private fun FinishedPanel(import: ImportUi) {
    Card(modifier = Modifier.readable()) {
        Column(modifier = Modifier.padding(Spacing.medium), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text("Imported ${import.exportName}", style = MaterialTheme.typography.titleMedium)
            Text(
                "${import.eventCount} Events and ${import.claimCount} Claims",
                style = MaterialTheme.typography.bodyMedium,
            )
            FileSection("Parsed", import.parsed)
            FileSection("Skipped", import.skipped)
            FileSection("Failed", import.failed)
        }
    }
}

@Composable
private fun FileSection(
    title: String,
    files: List<FileResultUi>,
) {
    Text("$title (${files.size})", style = MaterialTheme.typography.titleSmall)
    if (files.isEmpty()) {
        Text("None", style = MaterialTheme.typography.bodySmall)
    }
    files.forEach { FileLine(it) }
}

@Composable
private fun FileLine(file: FileResultUi) {
    Column {
        Text(file.path, style = MaterialTheme.typography.bodySmall)
        Text(
            file.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun NoticeCard(
    title: String,
    body: String,
    isError: Boolean = false,
) {
    val colors =
        if (isError) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        }
    Card(modifier = Modifier.readable(), colors = colors) {
        Column(modifier = Modifier.padding(Spacing.medium), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
