package com.jassemsendi.unpacked.ui.di

import com.jassemsendi.unpacked.agent.AskAgent
import com.jassemsendi.unpacked.agent.KoogAskAgent
import com.jassemsendi.unpacked.agent.LibraryTools
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
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The screens' ViewModels. Expects the library module, with the [com.jassemsendi.unpacked.ingest.ExportFileReader] it
 * binds, an [com.jassemsendi.unpacked.ui.imports.ExportPicker], an
 * [com.jassemsendi.unpacked.ui.accessrequest.AccessRequestSaver] and a [com.jassemsendi.unpacked.agent.ModelProvider]
 * from the app.
 */
val uiModule: Module =
    module {
        single { TimeZone.currentSystemDefault() }
        single { InsightCards(get(), get(), get()) }
        // Reads the Evidence rules each Source's adapter declares.
        single { GapReportReader(get(), get(), get()) }
        // The eight Tools over the repository interfaces, and the agent that calls them with whatever model the app binds.
        single { LibraryTools(get(), get(), get(), get(), get(), get()) }
        single<AskAgent> { KoogAskAgent(get(), get(), get()) }
        viewModelOf(::ImportViewModel)
        viewModelOf(::GapReportViewModel)
        viewModelOf(::AccessRequestViewModel)
        viewModelOf(::ExplorerViewModel)
        // Spelled out because the page sizes have defaults Koin cannot resolve.
        viewModel { HomeViewModel(get(), get(), get(), get()) }
        viewModel { SearchViewModel(get(), get(), get(), get()) }
        viewModelOf(::AskViewModel)
        viewModel { ClaimsViewModel(get(), get(), get()) }
        viewModel { EntitiesViewModel(get(), get(), get(), get()) }
    }
