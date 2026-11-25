package com.jassemsendi.unpacked

import android.app.Application
import com.jassemsendi.unpacked.agent.ModelProvider
import com.jassemsendi.unpacked.agent.NoModelProvider
import com.jassemsendi.unpacked.data.Library
import com.jassemsendi.unpacked.data.libraryModule
import com.jassemsendi.unpacked.data.open
import com.jassemsendi.unpacked.ui.accessrequest.AccessRequestSaver
import com.jassemsendi.unpacked.ui.accessrequest.NoAccessRequestSaver
import com.jassemsendi.unpacked.ui.di.uiModule
import com.jassemsendi.unpacked.ui.imports.ExportPicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module

class UnpackedApplication : Application() {
    /** Outlives every screen: where a hosted Import runs. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ImportService.createChannel(this)
        val library = Library.open(this, getDatabasePath("library.db").absolutePath)
        val picker = AndroidExportPicker(contentResolver)
        startKoin {
            modules(
                // Exports are read from the documents the user picked, through the content resolver.
                libraryModule(library, contentResolver.exportFileSystem()) { importUseCase ->
                    HostedImportUseCase(importUseCase, ForegroundImportHost(this@UnpackedApplication), appScope)
                },
                // Nothing shares the Access request on Android yet, and no model runs on the phone yet (spec story 44).
                module {
                    single { picker } bind ExportPicker::class
                    single<AccessRequestSaver> { NoAccessRequestSaver }
                    single<ModelProvider> { NoModelProvider }
                },
                uiModule,
            )
        }
    }
}
