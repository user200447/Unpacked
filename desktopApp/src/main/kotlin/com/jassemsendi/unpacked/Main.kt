package com.jassemsendi.unpacked

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.jassemsendi.unpacked.data.Library
import com.jassemsendi.unpacked.data.libraryModule
import com.jassemsendi.unpacked.data.open
import com.jassemsendi.unpacked.ui.UnpackedApp
import com.jassemsendi.unpacked.ui.di.uiModule
import com.jassemsendi.unpacked.ui.imports.ExportPicker
import okio.FileSystem
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.io.File

fun main() {
    val library = Library.open(libraryFile().absolutePath)
    startKoin {
        modules(
            libraryModule(library, FileSystem.SYSTEM),
            module { single<ExportPicker> { DesktopExportPicker() } },
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

/** The library lives in the per-user application data directory of the host OS. */
private fun libraryFile(): File {
    val home = File(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()
    val dataDir =
        when {
            os.contains("mac") -> home.resolve("Library/Application Support")
            os.contains("windows") -> System.getenv("APPDATA")?.let(::File) ?: home.resolve("AppData/Roaming")
            else -> System.getenv("XDG_DATA_HOME")?.let(::File) ?: home.resolve(".local/share")
        }
    return dataDir.resolve("Unpacked").apply { mkdirs() }.resolve("library.db")
}
