package com.jassemsendi.unpacked.ui.di

import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.data.ImportRepository
import com.jassemsendi.unpacked.data.ImportUseCase
import com.jassemsendi.unpacked.ui.home.FakeClaimRepository
import com.jassemsendi.unpacked.ui.home.FakeEventRepository
import com.jassemsendi.unpacked.ui.home.HomeViewModel
import com.jassemsendi.unpacked.ui.imports.ExportPicker
import com.jassemsendi.unpacked.ui.imports.FakeExportPicker
import com.jassemsendi.unpacked.ui.imports.FakeImportRepository
import com.jassemsendi.unpacked.ui.imports.FakeImportUseCase
import com.jassemsendi.unpacked.ui.imports.ImportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/** The wiring every app repeats: the UI module resolves once the app binds the library interfaces and a picker. */
class UiModuleTest {
    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun everyViewModelResolvesFromTheLibraryInterfacesAndAPicker() {
        val repository = FakeImportRepository()
        val appModule =
            module {
                single<ImportRepository> { repository }
                single<ImportUseCase> { FakeImportUseCase(repository) }
                single<EventRepository> { FakeEventRepository() }
                single<ClaimRepository> { FakeClaimRepository() }
                single<ExportPicker> { FakeExportPicker() }
            }

        val koin = koinApplication { modules(appModule, uiModule) }.koin

        assertNotNull(koin.get<ImportViewModel>())
        assertNotNull(koin.get<HomeViewModel>())
        koin.close()
    }
}
