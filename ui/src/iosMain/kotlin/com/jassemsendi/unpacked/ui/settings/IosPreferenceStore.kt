package com.jassemsendi.unpacked.ui.settings

import com.jassemsendi.unpacked.agent.PreferenceStore
import platform.Foundation.NSUserDefaults

/** The iOS binding of [PreferenceStore] (ADR 0008): the app's user defaults. */
internal class IosPreferenceStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : PreferenceStore {
    override suspend fun read(name: String): String? = defaults.stringForKey(name)

    override suspend fun write(
        name: String,
        value: String?,
    ) {
        if (value == null) defaults.removeObjectForKey(name) else defaults.setObject(value, name)
    }
}
