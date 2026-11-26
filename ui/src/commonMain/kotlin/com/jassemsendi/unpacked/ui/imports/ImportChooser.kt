package com.jassemsendi.unpacked.ui.imports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable

/** One chip per stored Import, newest first; what the screen shows is over the selected one. */
@Composable
internal fun ImportChooser(
    imports: List<ImportChoiceUi>,
    selected: ImportId?,
    onSelect: (ImportId) -> Unit,
) {
    Row(
        modifier = Modifier.readable().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        imports.forEach { import ->
            val name = if (import.isComplete) import.exportName else "${import.exportName} (incomplete)"
            FilterChip(selected = import.id == selected, onClick = { onSelect(import.id) }, label = { Text(name) })
        }
    }
}
