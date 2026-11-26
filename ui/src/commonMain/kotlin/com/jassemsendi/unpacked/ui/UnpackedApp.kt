package com.jassemsendi.unpacked.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jassemsendi.unpacked.ui.accessrequest.AccessRequestRoot
import com.jassemsendi.unpacked.ui.ask.AskRoot
import com.jassemsendi.unpacked.ui.claims.ClaimsRoot
import com.jassemsendi.unpacked.ui.entities.EntitiesRoot
import com.jassemsendi.unpacked.ui.explorer.ExplorerRoot
import com.jassemsendi.unpacked.ui.gapreport.GapReportRoot
import com.jassemsendi.unpacked.ui.home.HomeDestinations
import com.jassemsendi.unpacked.ui.home.HomeRoot
import com.jassemsendi.unpacked.ui.imports.ImportRoot
import com.jassemsendi.unpacked.ui.search.SearchRoot
import com.jassemsendi.unpacked.ui.settings.SettingsRoot
import com.jassemsendi.unpacked.ui.welcome.WelcomeScreen

/**
 * Root of the shared UI: the Welcome screen first, then the Import screen, then Home with the Insight cards
 * and the timeline; Home and Import lead to each other, and Home to Ask, Search, Claims, Entities, the Gap
 * report, the Explorer and Settings and back, Ask to the Gap report and to Settings, a number on a card opening
 * them on what it counted; the Gap report leads to the Access request and back, and Import to the Explorer over
 * one of its Imports. Koin must be started before this runs.
 */
@Composable
fun UnpackedApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var screen: Screen by rememberSaveable(stateSaver = Screen.saver) { mutableStateOf(Screen.Welcome) }
            val home = { screen = Screen.Home }
            when (val current = screen) {
                Screen.Welcome -> {
                    WelcomeScreen(onContinue = { screen = Screen.Import })
                }

                Screen.Import -> {
                    ImportRoot(onShowTimeline = home, onExplore = { screen = Screen.Explorer(it) })
                }

                Screen.Home -> {
                    HomeRoot(homeDestinations { screen = it })
                }

                Screen.Ask -> {
                    AskRoot(
                        onBack = home,
                        onGapReport = { screen = Screen.GapReport() },
                        onSettings = { screen = Screen.Settings },
                    )
                }

                Screen.Settings -> {
                    SettingsRoot(onBack = home)
                }

                Screen.Search -> {
                    SearchRoot(onBack = home)
                }

                is Screen.Claims -> {
                    ClaimsRoot(onBack = home, expanded = current.expanded)
                }

                is Screen.Entities -> {
                    EntitiesRoot(onBack = home, opened = current.opened)
                }

                is Screen.GapReport -> {
                    GapReportRoot(
                        onBack = home,
                        onAccessRequest = { screen = Screen.AccessRequest(it) },
                        importId = current.importId,
                    )
                }

                is Screen.AccessRequest -> {
                    AccessRequestRoot(
                        importId = current.importId,
                        onBack = { screen = Screen.GapReport(current.importId) },
                    )
                }

                is Screen.Explorer -> {
                    ExplorerRoot(onBack = home, importId = current.importId)
                }
            }
        }
    }
}

/** Where Home leads, each destination setting the screen through [go]. */
private fun homeDestinations(go: (Screen) -> Unit) =
    HomeDestinations(
        onImport = { go(Screen.Import) },
        onAsk = { go(Screen.Ask) },
        onSearch = { go(Screen.Search) },
        onClaims = { go(Screen.Claims(it)) },
        onEntities = { go(Screen.Entities()) },
        onEntity = { go(Screen.Entities(it)) },
        onGapReport = { go(Screen.GapReport()) },
        onExplorer = { go(Screen.Explorer()) },
        onSettings = { go(Screen.Settings) },
    )
