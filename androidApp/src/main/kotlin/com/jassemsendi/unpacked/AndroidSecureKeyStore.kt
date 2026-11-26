package com.jassemsendi.unpacked

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.jassemsendi.unpacked.agent.KeyStoreError
import com.jassemsendi.unpacked.agent.PreferenceStore
import com.jassemsendi.unpacked.agent.SecureKeyStore
import com.jassemsendi.unpacked.model.EmptyResult
import com.jassemsendi.unpacked.model.Result
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64

/** Seals and opens a secret with a key that never leaves the device: the Android Keystore in the app, a fake in tests. */
interface SecretCipher {
    fun seal(plain: ByteArray): ByteArray

    fun open(sealed: ByteArray): ByteArray
}

/**
 * The Android binding of [SecureKeyStore] (spec story 54, ADR 0008): each secret is sealed with an AES key held in the
 * Android Keystore, which the app can use but never read, and the sealed bytes go to a SharedPreferences file of
 * their own. A device whose Keystore refuses reports that rather than falling back to plain text.
 */
class AndroidSecureKeyStore(
    private val vault: PreferenceStore,
    private val cipher: SecretCipher,
) : SecureKeyStore {
    override val description: String = "the Android Keystore"

    override suspend fun read(name: String): Result<String?, KeyStoreError> {
        val sealed = vault.read(name) ?: return Result.Success(null)
        return guarded { cipher.open(Base64.decode(sealed)).decodeToString() }
    }

    override suspend fun write(
        name: String,
        value: String,
    ): EmptyResult<KeyStoreError> {
        val sealed = guarded { Base64.encode(cipher.seal(value.encodeToByteArray())) }
        return when (sealed) {
            is Result.Error -> sealed
            is Result.Success -> Result.Success(vault.write(name, sealed.data))
        }
    }

    override suspend fun remove(name: String): EmptyResult<KeyStoreError> = Result.Success(vault.write(name, null))

    private inline fun <T> guarded(work: () -> T): Result<T, KeyStoreError> =
        try {
            Result.Success(work())
        } catch (_: GeneralSecurityException) {
            Result.Error(KeyStoreError.NOT_AVAILABLE)
        } catch (_: IllegalArgumentException) {
            // Base64's own refusal: the vault holds something that was never a sealed secret.
            Result.Error(KeyStoreError.NOT_AVAILABLE)
        }
}

/** AES-GCM with a key generated in the Android Keystore on first use; the IV travels in front of the sealed bytes. */
class KeystoreCipher(
    private val alias: String = ALIAS,
) : SecretCipher {
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()
    }

    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray {
        if (sealed.size <= IV_BYTES) throw GeneralSecurityException("too short to hold an IV and a tag")
        val spec = GCMParameterSpec(TAG_BITS, sealed.copyOfRange(0, IV_BYTES))
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), spec) }
        return cipher.doFinal(sealed.copyOfRange(IV_BYTES, sealed.size))
    }

    companion object {
        const val ALIAS: String = "unpacked-secrets"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
