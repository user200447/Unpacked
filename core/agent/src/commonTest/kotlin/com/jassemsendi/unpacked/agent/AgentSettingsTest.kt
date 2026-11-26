package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Settings seam (spec stories 53, 54): the provider choice is a preference, the key lives in secure storage only. */
class AgentSettingsTest {
    private val preferences = InMemoryPreferenceStore()
    private val keys = InMemorySecureKeyStore()

    private fun settings(localOffered: Boolean = true) = AgentSettings(preferences, keys, localOffered)

    @Test
    fun aDesktopStartsLocalAndAPhoneStartsOff() =
        runTest {
            assertEquals(ProviderChoice.Local, settings(localOffered = true).provider())
            assertEquals(ProviderChoice.Off, settings(localOffered = false).provider())
        }

    @Test
    fun theChoiceIsRememberedAsAPreferenceAndNeverAsASecret() =
        runTest {
            val settings = settings()

            settings.choose(ProviderChoice.Cloud)

            assertEquals(ProviderChoice.Cloud, settings.provider())
            assertEquals(mapOf(AgentSettings.PROVIDER_PREFERENCE to "Cloud"), preferences.values)
            assertTrue(keys.secrets.isEmpty())
        }

    @Test
    fun aChoiceNobodyRecognisesFallsBackToTheDefault() =
        runTest {
            preferences.values[AgentSettings.PROVIDER_PREFERENCE] = "Mistral"

            assertEquals(ProviderChoice.Local, settings().provider())
        }

    @Test
    fun localCannotBeChosenWhereNoLocalModelIsOffered() =
        runTest {
            val settings = settings(localOffered = false)

            settings.choose(ProviderChoice.Local)

            assertEquals(ProviderChoice.Off, settings.provider())
            assertEquals(listOf(ProviderChoice.Cloud, ProviderChoice.Off), settings.choices)
        }

    @Test
    fun savingAKeyKeepsItInSecureStorageOnlyAndTurnsTheCloudOn() =
        runTest {
            val settings = settings()

            settings.saveKey("  AIza-example  ").orFail()

            assertEquals(mapOf(AgentSettings.GEMINI_KEY to "AIza-example"), keys.secrets)
            assertFalse(preferences.values.values.any { it.contains("AIza") }, "the key never lands in a preference")
            assertTrue(settings.hasKey().orFail())
            assertEquals(ProviderChoice.Cloud, settings.provider())
        }

    @Test
    fun aBlankKeyIsRefusedAndChangesNothing() =
        runTest {
            val settings = settings()

            assertEquals(Result.Error(KeySaveError.Blank), settings.saveKey("   "))

            assertFalse(settings.hasKey().orFail())
            assertEquals(ProviderChoice.Local, settings.provider())
        }

    @Test
    fun removingTheKeyLeavesTheCloudForTheLocalModelOrForOff() =
        runTest {
            val onDesktop = settings(localOffered = true)
            onDesktop.saveKey("AIza-example").orFail()
            onDesktop.removeKey().orFail()
            assertFalse(onDesktop.hasKey().orFail())
            assertEquals(ProviderChoice.Local, onDesktop.provider())

            val onPhone = AgentSettings(InMemoryPreferenceStore(), InMemorySecureKeyStore(), localOffered = false)
            onPhone.saveKey("AIza-example").orFail()
            onPhone.removeKey().orFail()
            assertEquals(ProviderChoice.Off, onPhone.provider())
        }

    @Test
    fun aStoreThatRefusesIsReportedNotThrown() =
        runTest {
            keys.failure = KeyStoreError.NOT_AVAILABLE
            val settings = settings()

            assertEquals(Result.Error(KeySaveError.Storage(KeyStoreError.NOT_AVAILABLE)), settings.saveKey("AIza"))
            assertEquals(Result.Error(KeyStoreError.NOT_AVAILABLE), settings.hasKey())
            assertEquals(ProviderChoice.Local, settings.provider(), "a failed save leaves the choice alone")
        }
}
