package com.jassemsendi.unpacked.ui.settings

import com.jassemsendi.unpacked.agent.KeyStoreError
import com.jassemsendi.unpacked.agent.PreferenceStore
import com.jassemsendi.unpacked.agent.SecureKeyStore
import com.jassemsendi.unpacked.model.EmptyResult
import com.jassemsendi.unpacked.model.Result

/** The plain preferences of one test, held in a map. */
class FakePreferenceStore : PreferenceStore {
    val values = mutableMapOf<String, String>()

    override suspend fun read(name: String): String? = values[name]

    override suspend fun write(
        name: String,
        value: String?,
    ) {
        if (value == null) values.remove(name) else values[name] = value
    }
}

/** The secrets of one test, held in a map; [failure] makes every call fail the way a locked keychain does. */
class FakeSecureKeyStore : SecureKeyStore {
    val secrets = mutableMapOf<String, String>()
    var failure: KeyStoreError? = null

    override val description: String = "a map in the test"

    override suspend fun read(name: String): Result<String?, KeyStoreError> =
        failure?.let { Result.Error(it) } ?: Result.Success(secrets[name])

    override suspend fun write(
        name: String,
        value: String,
    ): EmptyResult<KeyStoreError> =
        failure?.let { Result.Error(it) } ?: Result.Success(Unit).also { secrets[name] = value }

    override suspend fun remove(name: String): EmptyResult<KeyStoreError> =
        failure?.let { Result.Error(it) } ?: Result.Success(Unit).also { secrets.remove(name) }
}
