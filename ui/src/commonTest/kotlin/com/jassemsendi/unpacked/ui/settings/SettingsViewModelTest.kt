package com.jassemsendi.unpacked.ui.settings

import com.jassemsendi.unpacked.agent.AgentSettings
import com.jassemsendi.unpacked.agent.KeyStoreError
import com.jassemsendi.unpacked.agent.ProviderChoice
import com.jassemsendi.unpacked.ingest.TestedExport
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Seam 5: the Settings screen over the agent settings with in-memory stores (spec stories 53 to 55). */
class SettingsViewModelTest {
    private val preferences = FakePreferenceStore()
    private val keys = FakeSecureKeyStore()
    private val tested =
        listOf(TestedExport(Source.Instagram, "JSON", LocalDate(2026, 10, 20), "All time, JSON, zip or folder."))

    private fun viewModel(localOffered: Boolean = true) =
        SettingsViewModel(AgentSettings(preferences, keys, localOffered), tested)

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun aDesktopOffersThreeChoicesAndStartsLocalWithNoKey() =
        runTest {
            val state = viewModel().state.value

            assertFalse(state.isLoading)
            assertEquals(ProviderChoice.entries, state.choices.map { it.choice })
            assertEquals(ProviderChoice.Local, state.provider)
            assertFalse(state.hasKey)
            assertEquals("a map in the test", state.keyStorage)
        }

    @Test
    fun aPhoneOffersNoLocalModelAndStartsOff() =
        runTest {
            val state = viewModel(localOffered = false).state.value

            assertEquals(listOf(ProviderChoice.Cloud, ProviderChoice.Off), state.choices.map { it.choice })
            assertEquals(ProviderChoice.Off, state.provider)
        }

    @Test
    fun choosingAProviderIsRemembered() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(SettingsAction.ChooseProvider(ProviderChoice.Off))

            assertEquals(ProviderChoice.Off, viewModel.state.value.provider)
            assertEquals("Off", preferences.values[AgentSettings.PROVIDER_PREFERENCE])
        }

    @Test
    fun savingAKeyClearsTheDraftTurnsTheCloudOnAndNeverShowsTheKeyAgain() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(SettingsAction.KeyChanged("AIza-example"))
            viewModel.onAction(SettingsAction.SaveKey)

            val state = viewModel.state.value
            assertTrue(state.hasKey)
            assertEquals("", state.keyDraft)
            assertEquals(ProviderChoice.Cloud, state.provider)
            assertNotNull(state.notice)
            assertNull(state.error)
            assertEquals("AIza-example", keys.secrets[AgentSettings.GEMINI_KEY])
            assertFalse(state.toString().contains("AIza"), "the key is not on the screen")
        }

    @Test
    fun aBlankKeyIsRefusedInWords() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(SettingsAction.KeyChanged("   "))
            viewModel.onAction(SettingsAction.SaveKey)

            assertEquals("Paste a key first.", viewModel.state.value.error)
            assertFalse(viewModel.state.value.hasKey)
        }

    @Test
    fun removingTheKeyFallsBackToTheLocalModel() =
        runTest {
            keys.secrets[AgentSettings.GEMINI_KEY] = "AIza-example"
            preferences.values[AgentSettings.PROVIDER_PREFERENCE] = "Cloud"
            val viewModel = viewModel()
            assertTrue(viewModel.state.value.hasKey)

            viewModel.onAction(SettingsAction.RemoveKey)

            assertFalse(viewModel.state.value.hasKey)
            assertEquals(ProviderChoice.Local, viewModel.state.value.provider)
            assertTrue(keys.secrets.isEmpty())
        }

    @Test
    fun aStoreThatCannotBeUsedIsSaidNotThrown() =
        runTest {
            keys.failure = KeyStoreError.NOT_AVAILABLE
            val viewModel = viewModel()

            assertEquals(KeyStoreError.NOT_AVAILABLE.toMessage(), viewModel.state.value.error)

            viewModel.onAction(SettingsAction.KeyChanged("AIza-example"))
            viewModel.onAction(SettingsAction.SaveKey)
            assertEquals(KeyStoreError.NOT_AVAILABLE.toMessage(), viewModel.state.value.error)
            assertEquals("AIza-example", viewModel.state.value.keyDraft, "a failed save keeps what was typed")
        }

    @Test
    fun theTestedExportsComeFromTheDeclaredList() =
        runTest {
            val shown = viewModel().state.value.testedExports

            val instagram = TestedExportUi("Instagram", "JSON", "2026-10-20", "All time, JSON, zip or folder.")
            assertEquals(listOf(instagram), shown)
        }
}
