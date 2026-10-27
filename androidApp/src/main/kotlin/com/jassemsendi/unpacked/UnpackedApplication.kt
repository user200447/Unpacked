package com.jassemsendi.unpacked

import android.app.Application
import com.jassemsendi.unpacked.data.Library
import com.jassemsendi.unpacked.data.libraryModule
import com.jassemsendi.unpacked.data.open
import com.jassemsendi.unpacked.ui.di.uiModule
import com.jassemsendi.unpacked.ui.imports.ExportPicker
import com.jassemsendi.unpacked.ui.imports.NoExportPicker
import okio.FileSystem
import org.koin.core.context.startKoin
import org.koin.dsl.module

class UnpackedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val library = Library.open(this, getDatabasePath("library.db").absolutePath)
        startKoin {
            modules(
                libraryModule(library, FileSystem.SYSTEM),
                // The Storage Access Framework picker arrives with ticket #17.
                module { single<ExportPicker> { NoExportPicker } },
                uiModule,
            )
        }
    }
}
