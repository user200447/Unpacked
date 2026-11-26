package com.jassemsendi.unpacked.ui.settings

import com.jassemsendi.unpacked.agent.ProviderChoice

data class SettingsState(
    /** True until the choice and whether a key is saved have been read. */
    val isLoading: Boolean = true,
    /** The choices this platform offers, in the order shown; a phone offers no local model. */
    val choices: List<ProviderChoiceUi> = emptyList(),
    /** The choice in force. */
    val provider: ProviderChoice? = null,
    /** Whether a Gemini key is saved; the key itself is never read back into the screen. */
    val hasKey: Boolean = false,
    /** Where this platform keeps the key, in words. */
    val keyStorage: String = "",
    /** The key as typed, until it is saved; cleared on save. */
    val keyDraft: String = "",
    val isSavingKey: Boolean = false,
    /** The Exports the app was tested against, from the adapters' own declarations. */
    val testedExports: List<TestedExportUi> = emptyList(),
    /** What the last change did, until the next one. */
    val notice: String? = null,
    /** Why the last change failed, until the next one succeeds. */
    val error: String? = null,
)

/** One way the agent may run, as Settings offers it. */
data class ProviderChoiceUi(
    val choice: ProviderChoice,
    val label: String,
    /** What choosing it means for the user's data. */
    val description: String,
)

/** One tested Export as Settings lists it. */
data class TestedExportUi(
    val sourceName: String,
    val format: String,
    /** When it was downloaded, as a date. */
    val downloadedOn: String,
    val note: String,
)

sealed interface SettingsAction {
    data class ChooseProvider(
        val choice: ProviderChoice,
    ) : SettingsAction

    data class KeyChanged(
        val key: String,
    ) : SettingsAction

    /** Save the key as typed; a blank one is refused. */
    data object SaveKey : SettingsAction

    data object RemoveKey : SettingsAction
}
