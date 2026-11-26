package com.jassemsendi.unpacked.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jassemsendi.unpacked.ui.theme.Spacing
import com.jassemsendi.unpacked.ui.theme.readable

/** One chip per Kind with its count, and an "All" chip first; the same row on every screen that narrows by Kind. */
@Composable
internal fun <K> KindFilter(
    kinds: List<KindCountUi<K>>,
    totalCount: Int,
    filter: K?,
    onFilter: (K?) -> Unit,
) {
    Row(
        modifier = Modifier.readable().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        FilterChip(selected = filter == null, onClick = { onFilter(null) }, label = { Text("All ($totalCount)") })
        kinds.forEach { kind ->
            FilterChip(
                selected = filter == kind.kind,
                onClick = { onFilter(kind.kind) },
                label = { Text("${kind.label} (${kind.count})") },
            )
        }
    }
}
