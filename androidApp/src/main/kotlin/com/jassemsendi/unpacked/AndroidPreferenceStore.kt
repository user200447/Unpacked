package com.jassemsendi.unpacked

import android.content.SharedPreferences
import com.jassemsendi.unpacked.agent.PreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Android binding of [PreferenceStore] (ADR 0008): one SharedPreferences file, written before the call returns. */
class AndroidPreferenceStore(
    private val preferences: SharedPreferences,
) : PreferenceStore {
    override suspend fun read(name: String): String? = withContext(Dispatchers.IO) { preferences.getString(name, null) }

    override suspend fun write(
        name: String,
        value: String?,
    ) {
        withContext(Dispatchers.IO) {
            preferences.edit().apply { if (value == null) remove(name) else putString(name, value) }.commit()
        }
    }
}
