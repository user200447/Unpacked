package com.jassemsendi.unpacked.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.jassemsendi.unpacked.agent.LocalModelProvider
import com.jassemsendi.unpacked.agent.NoModelProvider
import com.jassemsendi.unpacked.agent.PreferenceStore
import com.jassemsendi.unpacked.agent.SecureKeyStore
import com.jassemsendi.unpacked.data.Library
import com.jassemsendi.unpacked.data.libraryModule
import com.jassemsendi.unpacked.data.open
import com.jassemsendi.unpacked.ui.accessrequest.AccessRequestSaver
import com.jassemsendi.unpacked.ui.accessrequest.NoAccessRequestSaver
import com.jassemsendi.unpacked.ui.di.uiModule
import com.jassemsendi.unpacked.ui.imports.ExportPicker
import com.jassemsendi.unpacked.ui.imports.IosExportPicker
import com.jassemsendi.unpacked.ui.imports.UiKitDocumentPickerPresenter
import com.jassemsendi.unpacked.ui.settings.IosPreferenceStore
import com.jassemsendi.unpacked.ui.settings.IosSecureKeyStore
import com.jassemsendi.unpacked.ui.settings.SecurityKeychain
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

/**
 * Entry point the SwiftUI shell embeds. Starts Koin once, with the library in Application Support and the
 * platform bindings iOS has (ADR 0008), then hands back the Compose view controller the shell shows.
 */
@Suppress("FunctionName")
fun MainViewController(): UIViewController {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        val library = Library.open(libraryPath())
        startKoin {
            modules(
                libraryModule(library, FileSystem.SYSTEM),
                // Exports are picked with the system document picker and read in place; nothing shares the Access
                // request on iOS yet. No model runs on the phone itself; Settings may choose Gemini, with the key in
                // the Keychain and the choice in the user defaults (ADR 0008).
                module {
                    single<ExportPicker> { IosExportPicker(UiKitDocumentPickerPresenter()) }
                    single<AccessRequestSaver> { NoAccessRequestSaver }
                    single<LocalModelProvider> { NoModelProvider }
                    single<PreferenceStore> { IosPreferenceStore() }
                    single<SecureKeyStore> { IosSecureKeyStore(SecurityKeychain()) }
                },
                uiModule,
            )
        }
    }
    return ComposeUIViewController { SafeAreaApp() }
}

/**
 * The shared screens inside the safe area. The Swift shell lets Compose fill the window, so the edges under the
 * status bar and the home indicator are painted here with the theme's background, one level outside the
 * [UnpackedApp] whose own surface stops at the inset; nothing in the shared code knows about iOS edges.
 */
@Composable
private fun SafeAreaApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                UnpackedApp()
            }
        }
    }
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
