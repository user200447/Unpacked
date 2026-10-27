package com.jassemsendi.unpacked.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The few gaps every screen is laid out with. */
internal object Spacing {
    val small: Dp = 8.dp
    val medium: Dp = 16.dp
    val large: Dp = 24.dp

    /** Text columns stop growing here so lines stay readable on a wide desktop window. */
    val readableWidth: Dp = 720.dp
}
