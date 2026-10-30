package com.jassemsendi.unpacked.ui.di

import com.jassemsendi.unpacked.ui.claims.ClaimsViewModel
import com.jassemsendi.unpacked.ui.entities.EntitiesViewModel
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
        viewModelOf(::ImportViewModel)
        // Spelled out because the page sizes have defaults Koin cannot resolve.
        viewModel { HomeViewModel(get(), get(), get()) }
        viewModel { SearchViewModel(get(), get(), get(), get()) }
        viewModel { ClaimsViewModel(get(), get(), get()) }
        viewModel { EntitiesViewModel(get(), get(), get(), get()) }
    }
