package com.jassemsendi.unpacked.ui.settings

import com.jassemsendi.unpacked.agent.KeyStoreError
import com.jassemsendi.unpacked.agent.SecureKeyStore
import com.jassemsendi.unpacked.model.EmptyResult
import com.jassemsendi.unpacked.model.Result

/**
 * What holds the secrets on iOS: the Keychain in the app ([SecurityKeychain]), a fake in tests. Each call answers
 * or throws a [KeychainException] with the status the Keychain gave; "no such item" is an answer, not a failure.
 */
internal interface Keychain {
    /** The item under [account], or null when there is none. */
    fun read(account: String): String?

    /** Writes [value] under [account], replacing what was there. */
    fun write(
        account: String,
        value: String,
    )

    /** Removes the item under [account]; nothing to remove is fine. */
    fun delete(account: String)
}

internal class KeychainException(
    val status: Int,
) : Exception("Keychain status $status")

/** The iOS binding of [SecureKeyStore] (spec story 54, ADR 0008): generic passwords in the app's Keychain. */
internal class IosSecureKeyStore(
    private val keychain: Keychain,
) : SecureKeyStore {
    override val description: String = "the iOS Keychain"

    override suspend fun read(name: String): Result<String?, KeyStoreError> = guarded { keychain.read(name) }

    override suspend fun write(
        name: String,
        value: String,
    ): EmptyResult<KeyStoreError> = guarded { keychain.write(name, value) }

    override suspend fun remove(name: String): EmptyResult<KeyStoreError> = guarded { keychain.delete(name) }

    private inline fun <T> guarded(work: () -> T): Result<T, KeyStoreError> =
        try {
            Result.Success(work())
        } catch (_: KeychainException) {
            Result.Error(KeyStoreError.NOT_AVAILABLE)
        }
}
