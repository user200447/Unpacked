package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.model.EmptyResult
import com.jassemsendi.unpacked.model.Error
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.map
import com.jassemsendi.unpacked.model.onSuccess

/** How the user wants the agent to run (spec story 53): on the platform's own model, on Gemini, or not at all. */
public enum class ProviderChoice {
    Local,
    Cloud,
    Off,
}

/**
 * The platform's plain preferences (ADR 0008): small named values, such as the provider choice. Nothing secret goes
 * in unless sealed first, as the Android binding does. Each app binds its own: Java preferences on desktop,
 * SharedPreferences on Android, NSUserDefaults on iOS.
 */
public interface PreferenceStore {
    public suspend fun read(name: String): String?

    /** Writes [value] under [name]; null removes it. */
    public suspend fun write(
        name: String,
        value: String?,
    )
}

/** Why the platform's secure storage could not be used. */
public enum class KeyStoreError : Error {
    /** The storage refused or is missing: a locked keychain, a keystore the device has not got, an unreadable file. */
    NOT_AVAILABLE,
}

/**
 * The platform's secure storage for secrets (spec story 54, ADR 0008): the macOS Keychain or an owner-only file on
 * desktop, the Android Keystore, the iOS Keychain. Each app binds its own; a secret never goes anywhere else.
 */
public interface SecureKeyStore {
    /** Where a secret is kept, in words Settings shows: "the macOS Keychain", "the Android Keystore". */
    public val description: String

    public suspend fun read(name: String): Result<String?, KeyStoreError>

    public suspend fun write(
        name: String,
        value: String,
    ): EmptyResult<KeyStoreError>

    public suspend fun remove(name: String): EmptyResult<KeyStoreError>
}

/** Why a key could not be saved. */
public sealed interface KeySaveError : Error {
    public data object Blank : KeySaveError

    public data class Storage(
        val error: KeyStoreError,
    ) : KeySaveError
}

/**
 * The agent's settings: the provider choice as a preference and the Gemini key in secure storage, never the other
 * way round. [localOffered] says whether this platform binds a model of its own; without one, Local is not a choice
 * and the agent starts Off, so a phone answers nothing until a key is saved (ADR 0006).
 */
public class AgentSettings(
    private val preferences: PreferenceStore,
    private val keys: SecureKeyStore,
    public val localOffered: Boolean,
) {
    /** The choices this platform offers, in the order Settings lists them. */
    public val choices: List<ProviderChoice> =
        buildList {
            if (localOffered) add(ProviderChoice.Local)
            add(ProviderChoice.Cloud)
            add(ProviderChoice.Off)
        }

    private val default: ProviderChoice = if (localOffered) ProviderChoice.Local else ProviderChoice.Off

    /** Where the key is kept on this platform, for Settings to say. */
    public val keyStorage: String get() = keys.description

    /** The current choice; a stored name nobody recognises, or one this platform cannot offer, is the default. */
    public suspend fun provider(): ProviderChoice {
        val stored = preferences.read(PROVIDER_PREFERENCE)
        return ProviderChoice.entries.firstOrNull { it.name == stored }?.takeIf { it in choices } ?: default
    }

    /** Remembers [choice]; one this platform cannot offer is replaced by the default. */
    public suspend fun choose(choice: ProviderChoice) {
        preferences.write(PROVIDER_PREFERENCE, (choice.takeIf { it in choices } ?: default).name)
    }

    public suspend fun hasKey(): Result<Boolean, KeyStoreError> = keys.read(GEMINI_KEY).map { it != null }

    /** Stores the key, trimmed, and turns the cloud on: a key is saved to be used. A blank key is refused. */
    public suspend fun saveKey(key: String): EmptyResult<KeySaveError> {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return Result.Error(KeySaveError.Blank)
        return when (val written = keys.write(GEMINI_KEY, trimmed)) {
            is Result.Error -> Result.Error(KeySaveError.Storage(written.error))
            is Result.Success -> written.also { choose(ProviderChoice.Cloud) }
        }
    }

    /** Forgets the key; a choice of the cloud falls back to the default, since nothing would answer there. */
    public suspend fun removeKey(): EmptyResult<KeyStoreError> =
        keys.remove(GEMINI_KEY).onSuccess { if (provider() == ProviderChoice.Cloud) choose(default) }

    public companion object {
        /** The preference holding the [ProviderChoice] by name. */
        public const val PROVIDER_PREFERENCE: String = "agent.provider"

        /** The secret holding the Gemini API key. */
        public const val GEMINI_KEY: String = "gemini-api-key"
    }
}
