package com.jassemsendi.unpacked

import com.jassemsendi.unpacked.agent.PreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences

/** The desktop binding of [PreferenceStore] (ADR 0008): the JDK's per-user preferences, under the app's own node. */
class DesktopPreferenceStore(
    node: String = NODE,
) : PreferenceStore {
    private val preferences: Preferences = Preferences.userRoot().node(node)

    override suspend fun read(name: String): String? = withContext(Dispatchers.IO) { preferences.get(name, null) }

    override suspend fun write(
        name: String,
        value: String?,
    ) {
        withContext(Dispatchers.IO) {
            if (value == null) preferences.remove(name) else preferences.put(name, value)
            try {
                preferences.flush()
            } catch (_: BackingStoreException) {
                // The value is kept in memory for this run; the JDK writes it when the store is back.
            }
        }
    }

    companion object {
        const val NODE: String = "com/jassemsendi/unpacked"
    }
}
