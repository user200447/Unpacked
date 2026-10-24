package com.jassemsendi.unpacked

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.jassemsendi.unpacked.ui.UnpackedApp

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Unpacked",
        ) {
            UnpackedApp()
        }
    }
