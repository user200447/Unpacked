package com.jassemsendi.unpacked.ui.di

import com.jassemsendi.unpacked.ingest.SourceAdapter
import com.jassemsendi.unpacked.insights.EvidenceRules
import com.jassemsendi.unpacked.insights.GapReportReader
import com.jassemsendi.unpacked.insights.InsightCards
import com.jassemsendi.unpacked.ui.claims.ClaimsViewModel
import com.jassemsendi.unpacked.ui.entities.EntitiesViewModel
import com.jassemsendi.unpacked.ui.gapreport.GapReportViewModel
import com.jassemsendi.unpacked.ui.home.HomeViewModel
import com.jassemsendi.unpacked.ui.imports.ImportViewModel
import com.jassemsendi.unpacked.ui.search.SearchViewModel
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The screens' ViewModels. Expects the library module and an [com.jassemsendi.unpacked.ui.imports.ExportPicker] from the app. */
val uiModule: Module =
    module {
        single { TimeZone.currentSystemDefault() }
        single { InsightCards(get(), get(), get()) }
        // Each Source's adapter declares its own Evidence rules; the Gap report reads them through this lookup alone.
        single { GapReportReader(get(), get(), get(), EvidenceRules { SourceAdapter.of(it).evidenceRules }) }
        viewModelOf(::ImportViewModel)
        viewModelOf(::GapReportViewModel)
        // Spelled out because the page sizes have defaults Koin cannot resolve.
        viewModel { HomeViewModel(get(), get(), get(), get()) }
        viewModel { SearchViewModel(get(), get(), get(), get()) }
        viewModel { ClaimsViewModel(get(), get(), get()) }
        viewModel { EntitiesViewModel(get(), get(), get(), get()) }
    }
