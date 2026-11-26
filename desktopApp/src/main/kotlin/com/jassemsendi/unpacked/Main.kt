package com.jassemsendi.unpacked

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.jassemsendi.unpacked.agent.LocalModelProvider
import com.jassemsendi.unpacked.agent.PreferenceStore
import com.jassemsendi.unpacked.agent.SecureKeyStore
import com.jassemsendi.unpacked.data.Library
import com.jassemsendi.unpacked.data.libraryModule
import com.jassemsendi.unpacked.data.open
import com.jassemsendi.unpacked.ui.UnpackedApp
import com.jassemsendi.unpacked.ui.accessrequest.AccessRequestSaver
import com.jassemsendi.unpacked.ui.di.uiModule
import com.jassemsendi.unpacked.ui.imports.ExportPicker
import okio.FileSystem
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.io.File

fun main() {
    val dataDirectory = dataDirectory()
    val library = Library.open(dataDirectory.resolve("library.db").absolutePath)
    startKoin {
        modules(
            libraryModule(library, FileSystem.SYSTEM),
            module {
                single<ExportPicker> { DesktopExportPicker() }
                single<AccessRequestSaver> { DesktopAccessRequestSaver() }
                // The local model is Ollama on this computer; Settings may choose Gemini instead, with a key kept in
                // the Keychain (or an owner-only file) and the choice in the JDK's preferences (ADR 0008).
                single<LocalModelProvider> { OllamaModelProvider() }
                single<PreferenceStore> { DesktopPreferenceStore() }
                single<SecureKeyStore> { desktopKeyStore(dataDirectory) }
            },
            uiModule,
        )
    }
    application {
        Window(
            onCloseRequest = {
                library.close()
                exitApplication()
            },
            title = "Unpacked",
        ) {
            UnpackedApp()
        }
    }
}

/** The app's own directory under the per-user application data directory of the host OS: the library and the secrets live there. */
private fun dataDirectory(): File {
    val home = File(System.getProperty("user.home"))
    val dataDir =
        when {
            HostOs.isMac -> home.resolve("Library/Application Support")
            HostOs.isWindows -> System.getenv("APPDATA")?.let(::File) ?: home.resolve("AppData/Roaming")
            else -> System.getenv("XDG_DATA_HOME")?.let(::File) ?: home.resolve(".local/share")
        }
    return dataDir.resolve("Unpacked").apply { mkdirs() }
}
