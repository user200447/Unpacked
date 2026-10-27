package com.jassemsendi.unpacked.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.jassemsendi.unpacked.data.Library
import com.jassemsendi.unpacked.data.libraryModule
import com.jassemsendi.unpacked.data.open
import com.jassemsendi.unpacked.ui.di.uiModule
import com.jassemsendi.unpacked.ui.imports.ExportPicker
import com.jassemsendi.unpacked.ui.imports.NoExportPicker
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

/** Entry point the SwiftUI shell embeds. Starts Koin once, with the library in Application Support. */
@Suppress("FunctionName")
fun MainViewController(): UIViewController {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        val library = Library.open(libraryPath())
        startKoin {
            modules(
                libraryModule(library, FileSystem.SYSTEM),
                // The iOS document picker arrives with ticket #18.
                module { single<ExportPicker> { NoExportPicker } },
                uiModule,
            )
        }
    }
    return ComposeUIViewController { UnpackedApp() }
}

@OptIn(ExperimentalForeignApi::class)
private fun libraryPath(): String {
    val directory =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return checkNotNull(directory?.path) { "no Application Support directory" } + "/library.db"
}
