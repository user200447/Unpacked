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
import com.jassemsendi.unpacked.ui.imports.ImportRoot
import com.jassemsendi.unpacked.ui.welcome.WelcomeScreen

/** Root of the shared UI: the Welcome screen first, then the Import screen. Koin must be started before this runs. */
@Composable
fun UnpackedApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var welcomed by rememberSaveable { mutableStateOf(false) }
            if (welcomed) {
                ImportRoot()
            } else {
                WelcomeScreen(onContinue = { welcomed = true })
            }
        }
    }
}
