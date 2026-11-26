package com.jassemsendi.unpacked

import com.jassemsendi.unpacked.agent.KeyStoreError
import com.jassemsendi.unpacked.agent.PreferenceStore
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.test.runTest
import java.security.GeneralSecurityException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The Android secure store over an in-memory vault and a fake cipher standing in for the Keystore. */
class AndroidSecureKeyStoreTest {
    private val vault =
        object : PreferenceStore {
            val values = mutableMapOf<String, String>()

            override suspend fun read(name: String): String? = values[name]

            override suspend fun write(
                name: String,
                value: String?,
            ) {
                if (value == null) values.remove(name) else values[name] = value
            }
        }

    /** Reverses the bytes: enough to tell sealed from plain. */
    private val reversing =
        object : SecretCipher {
            override fun seal(plain: ByteArray): ByteArray = plain.reversedArray()

            override fun open(sealed: ByteArray): ByteArray = sealed.reversedArray()
        }

    @Test
    fun aSecretIsSealedIntoTheVaultAndOpenedOnTheWayBack() =
        runTest {
            val store = AndroidSecureKeyStore(vault, reversing)

            assertEquals(Result.Success(null), store.read("gemini"))
            assertEquals(Result.Success(Unit), store.write("gemini", "AIza-example"))
            assertFalse(vault.values.getValue("gemini").contains("AIza"), "the vault never holds the plain key")
            assertEquals(Result.Success("AIza-example"), store.read("gemini"))
            assertEquals(Result.Success(Unit), store.remove("gemini"))
            assertEquals(Result.Success(null), store.read("gemini"))
        }

    @Test
    fun aKeystoreThatRefusesIsNotAvailable() =
        runTest {
            val refusing =
                object : SecretCipher {
                    override fun seal(plain: ByteArray): ByteArray = throw GeneralSecurityException("locked")

                    override fun open(sealed: ByteArray): ByteArray = throw GeneralSecurityException("locked")
                }
            val store = AndroidSecureKeyStore(vault, refusing)

            assertEquals(Result.Error(KeyStoreError.NOT_AVAILABLE), store.write("gemini", "AIza-example"))
            assertEquals(null, vault.values["gemini"], "nothing is written in the clear")
            vault.values["gemini"] = "c2VhbGVk"
            assertEquals(Result.Error(KeyStoreError.NOT_AVAILABLE), store.read("gemini"))
        }

    @Test
    fun aVaultEntryThatIsNotBase64IsNotAvailableRatherThanACrash() =
        runTest {
            vault.values["gemini"] = "not base64 at all!"

            val read = AndroidSecureKeyStore(vault, reversing).read("gemini")
            assertEquals(Result.Error(KeyStoreError.NOT_AVAILABLE), read)
        }
}
