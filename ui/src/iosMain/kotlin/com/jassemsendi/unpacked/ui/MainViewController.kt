package com.jassemsendi.unpacked.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point the SwiftUI shell embeds. */
@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController { UnpackedApp() }
