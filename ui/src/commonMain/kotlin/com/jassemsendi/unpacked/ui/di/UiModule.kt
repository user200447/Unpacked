package com.jassemsendi.unpacked.ui.di

import com.jassemsendi.unpacked.agent.AgentSettings
import com.jassemsendi.unpacked.agent.AskAgent
import com.jassemsendi.unpacked.agent.GeminiModelProvider
import com.jassemsendi.unpacked.agent.KoogAskAgent
import com.jassemsendi.unpacked.agent.LibraryTools
import com.jassemsendi.unpacked.agent.LocalModelProvider
import com.jassemsendi.unpacked.agent.ModelProvider
import com.jassemsendi.unpacked.agent.SwitchingModelProvider
import com.jassemsendi.unpacked.insights.GapReportReader
import com.jassemsendi.unpacked.insights.InsightCards
import com.jassemsendi.unpacked.ui.accessrequest.AccessRequestViewModel
import com.jassemsendi.unpacked.ui.ask.AskViewModel
import com.jassemsendi.unpacked.ui.claims.ClaimsViewModel
import com.jassemsendi.unpacked.ui.entities.EntitiesViewModel
import com.jassemsendi.unpacked.ui.explorer.ExplorerViewModel
import com.jassemsendi.unpacked.ui.gapreport.GapReportViewModel
import com.jassemsendi.unpacked.ui.home.HomeViewModel
import com.jassemsendi.unpacked.ui.imports.ImportViewModel
import com.jassemsendi.unpacked.ui.search.SearchViewModel
import com.jassemsendi.unpacked.ui.settings.SettingsViewModel
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The screens' ViewModels. Expects the library module, with the [com.jassemsendi.unpacked.ingest.ExportFileReader] it
 * binds, an [com.jassemsendi.unpacked.ui.imports.ExportPicker], an
 * [com.jassemsendi.unpacked.ui.accessrequest.AccessRequestSaver], a [LocalModelProvider], a
 * [com.jassemsendi.unpacked.agent.PreferenceStore] and a [com.jassemsendi.unpacked.agent.SecureKeyStore] from the app.
 */
val uiModule: Module =
    module {
        single { TimeZone.currentSystemDefault() }
        single { InsightCards(get(), get(), get()) }
        // Reads the Evidence rules each Source's adapter declares.
        single { GapReportReader(get(), get(), get()) }
        // The eight Tools over the repository interfaces, and the agent that calls them with the model Settings chooses:
        // the platform's own, Gemini with the saved key, or none (ADR 0006).
        single { LibraryTools(get(), get(), get(), get(), get(), get()) }
        single { AgentSettings(get(), get(), localOffered = get<LocalModelProvider>().isOffered) }
        single<ModelProvider> { SwitchingModelProvider(get(), get<LocalModelProvider>(), GeminiModelProvider(get())) }
        single<AskAgent> { KoogAskAgent(get(), get(), get()) }
        viewModelOf(::ImportViewModel)
        viewModelOf(::GapReportViewModel)
        viewModelOf(::AccessRequestViewModel)
        viewModelOf(::ExplorerViewModel)
        // Spelled out because the page sizes have defaults Koin cannot resolve.
        viewModel { HomeViewModel(get(), get(), get(), get()) }
        viewModel { SearchViewModel(get(), get(), get(), get()) }
        viewModelOf(::AskViewModel)
        // Spelled out because the tested Exports default to the adapters' declarations.
        viewModel { SettingsViewModel(get()) }
        viewModel { ClaimsViewModel(get(), get(), get()) }
        viewModel { EntitiesViewModel(get(), get(), get(), get()) }
    }
