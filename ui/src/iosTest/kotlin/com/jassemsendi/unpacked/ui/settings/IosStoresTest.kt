package com.jassemsendi.unpacked.ui.settings

import com.jassemsendi.unpacked.agent.KeyStoreError
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The iOS stores: the Keychain binding over a fake standing in for the Security framework, and the user defaults. */
class IosStoresTest {
    private val suite = "com.jassemsendi.unpacked.test"

    @AfterTest
    fun cleanUp() {
        NSUserDefaults.standardUserDefaults.removePersistentDomainForName(suite)
    }

    @Test
    fun aSecretIsWrittenReadBackAndRemovedThroughTheKeychain() =
        runTest {
            val keychain = FakeKeychain()
            val store = IosSecureKeyStore(keychain)

            assertEquals(Result.Success(null), store.read("gemini"))
            assertEquals(Result.Success(Unit), store.write("gemini", "AIza-example"))
            assertEquals(mapOf("gemini" to "AIza-example"), keychain.items)
            assertEquals(Result.Success("AIza-example"), store.read("gemini"))
            assertEquals(Result.Success(Unit), store.remove("gemini"))
            assertEquals(Result.Success(null), store.read("gemini"))
            assertEquals("the iOS Keychain", store.description)
        }

    @Test
    fun aKeychainThatRefusesIsNotAvailable() =
        runTest {
            val keychain = FakeKeychain().apply { status = -34018 }
            val store = IosSecureKeyStore(keychain)

            assertEquals(Result.Error(KeyStoreError.NOT_AVAILABLE), store.read("gemini"))
            assertEquals(Result.Error(KeyStoreError.NOT_AVAILABLE), store.write("gemini", "x"))
            assertEquals(Result.Error(KeyStoreError.NOT_AVAILABLE), store.remove("gemini"))
        }

    @Test
    fun aPreferenceIsRememberedAndRemoved() =
        runTest {
            val defaults = NSUserDefaults(suiteName = suite)
            val store = IosPreferenceStore(defaults)

            assertNull(store.read("agent.provider"))
            store.write("agent.provider", "Cloud")
            assertEquals("Cloud", IosPreferenceStore(defaults).read("agent.provider"))
            store.write("agent.provider", null)
            assertNull(store.read("agent.provider"))
        }
}

/** A Keychain in a map; [status] other than success makes every call fail with it. */
private class FakeKeychain : Keychain {
    val items = mutableMapOf<String, String>()
    var status = 0

    override fun read(account: String): String? {
        failIfSet()
        return items[account]
    }

    override fun write(
        account: String,
        value: String,
    ) {
        failIfSet()
        items[account] = value
    }

    override fun delete(account: String) {
        failIfSet()
        items.remove(account)
    }

    private fun failIfSet() {
        if (status != 0) throw KeychainException(status)
    }
}
