package com.jassemsendi.unpacked.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier

/** Full width up to the readable width: what every text block and card on the first screens is sized to. */
internal fun Modifier.readable(): Modifier = widthIn(max = Spacing.readableWidth).fillMaxWidth()
