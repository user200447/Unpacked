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
import com.jassemsendi.unpacked.ui.home.HomeRoot
import com.jassemsendi.unpacked.ui.imports.ImportRoot
import com.jassemsendi.unpacked.ui.search.SearchRoot
import com.jassemsendi.unpacked.ui.welcome.WelcomeScreen

/**
 * Root of the shared UI: the Welcome screen first, then the Import screen, then Home with the timeline;
 * Home and Import lead to each other, and Home to Search and back. Koin must be started before this runs.
 */
@Composable
fun UnpackedApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var screen by rememberSaveable(stateSaver = Screen.saver) { mutableStateOf(Screen.Welcome) }
            when (screen) {
                Screen.Welcome -> WelcomeScreen(onContinue = { screen = Screen.Import })
                Screen.Import -> ImportRoot(onShowTimeline = { screen = Screen.Home })
                Screen.Home -> HomeRoot(onImport = { screen = Screen.Import }, onSearch = { screen = Screen.Search })
                Screen.Search -> SearchRoot(onBack = { screen = Screen.Home })
            }
        }
    }
}
