package com.jassemsendi.unpacked.ui

import androidx.compose.runtime.saveable.Saver

/** The top-level screens, in the order a first-time user meets them. */
enum class Screen {
    Welcome,
    Import,
    Home,
    ;

    companion object {
        /** Saved by name, so the screen survives process death on every platform. */
        val saver: Saver<Screen, String> = Saver(save = { it.name }, restore = { valueOf(it) })
    }
}
