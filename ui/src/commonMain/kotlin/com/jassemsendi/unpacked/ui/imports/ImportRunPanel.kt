package com.jassemsendi.unpacked.ui.imports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jassemsendi.unpacked.ui.theme.SectionCard

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
    SectionCard(title = "Importing ${run.exportName}", modifier = Modifier.readable()) {
        val total = run.filesTotal
        if (total == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Listing the files in the Export", style = MaterialTheme.typography.bodyMedium)
        } else {
            LinearProgressIndicator(progress = { run.filesDone.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            val current = run.currentFile
            if (current != null) {
                Text("File ${run.filesDone + 1} of $total", style = MaterialTheme.typography.bodyMedium)
                Text(current, style = MaterialTheme.typography.bodySmall)
            } else {
                Text("${run.filesDone} of $total files read", style = MaterialTheme.typography.bodyMedium)
            }
        }
        run.files.forEach { FileLine(it) }
    }
}

@Composable
private fun FinishedPanel(import: ImportUi) {
    SectionCard(title = "Imported ${import.exportName}", modifier = Modifier.readable()) {
        Text("${import.eventCount} Events and ${import.claimCount} Claims", style = MaterialTheme.typography.bodyMedium)
        FileSection("Parsed", import.parsed)
        FileSection("Skipped", import.skipped)
        FileSection("Failed", import.failed)
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
    SectionCard(title = title, modifier = Modifier.readable(), colors = colors) {
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
