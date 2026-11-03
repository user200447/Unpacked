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
import com.jassemsendi.unpacked.ui.claims.ClaimsRoot
import com.jassemsendi.unpacked.ui.entities.EntitiesRoot
import com.jassemsendi.unpacked.ui.home.HomeDestinations
import com.jassemsendi.unpacked.ui.home.HomeRoot
import com.jassemsendi.unpacked.ui.imports.ImportRoot
import com.jassemsendi.unpacked.ui.search.SearchRoot
import com.jassemsendi.unpacked.ui.welcome.WelcomeScreen

/**
 * Root of the shared UI: the Welcome screen first, then the Import screen, then Home with the Insight cards
 * and the timeline; Home and Import lead to each other, and Home to Search, Claims and Entities and back,
 * a number on a card opening them on what it counted. Koin must be started before this runs.
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
                    ImportRoot(onShowTimeline = home)
                }

                Screen.Home -> {
                    HomeRoot(
                        HomeDestinations(
                            onImport = { screen = Screen.Import },
                            onSearch = { screen = Screen.Search(it) },
                            onClaims = { screen = Screen.Claims(it) },
                            onEntities = { screen = Screen.Entities(filter = it) },
                            onEntity = { screen = Screen.Entities(opened = it) },
                        ),
                    )
                }

                is Screen.Search -> {
                    SearchRoot(onBack = home, initialText = current.text)
                }

                is Screen.Claims -> {
                    ClaimsRoot(onBack = home, expanded = current.expanded)
                }

                is Screen.Entities -> {
                    EntitiesRoot(onBack = home, filter = current.filter, opened = current.opened)
                }
            }
        }
    }
}
