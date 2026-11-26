package com.jassemsendi.unpacked.ui.settings

import com.jassemsendi.unpacked.agent.KeySaveError
import com.jassemsendi.unpacked.agent.KeyStoreError

// The one place the key store's typed refusals become words (ADR 0009).

fun KeyStoreError.toMessage(): String =
    when (this) {
        KeyStoreError.NOT_AVAILABLE -> "The secure storage on this device could not be used; the key was not touched."
    }

fun KeySaveError.toMessage(): String =
    when (this) {
        KeySaveError.Blank -> "Paste a key first."
        is KeySaveError.Storage -> error.toMessage()
    }
