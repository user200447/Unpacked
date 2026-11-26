package com.jassemsendi.unpacked.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** One sentence on why a screen could not do what it tried, in the error colours, where the result would have been. */
@Composable
internal fun ErrorNotice(
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.readable(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Spacing.medium))
    }
}
