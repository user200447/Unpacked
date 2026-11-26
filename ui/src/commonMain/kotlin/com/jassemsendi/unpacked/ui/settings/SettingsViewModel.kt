package com.jassemsendi.unpacked.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassemsendi.unpacked.agent.AgentSettings
import com.jassemsendi.unpacked.agent.ProviderChoice
import com.jassemsendi.unpacked.ingest.SourceAdapter
import com.jassemsendi.unpacked.ingest.TestedExport
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Settings (spec stories 53 to 55): how the agent runs, the Gemini key in the platform's secure storage, and the
 * Exports the app was tested against. The key is written and removed, never read back onto the screen.
 */
class SettingsViewModel(
    private val settings: AgentSettings,
    testedExports: List<TestedExport> = SourceAdapter.allTestedExports,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            SettingsState(
                choices = settings.choices.map { it.toUi() },
                keyStorage = settings.keyStorage,
                testedExports = testedExports.map { it.toUi() },
            ),
        )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.ChooseProvider -> choose(action.choice)
            is SettingsAction.KeyChanged -> _state.update { it.copy(keyDraft = action.key) }
            SettingsAction.SaveKey -> saveKey()
            SettingsAction.RemoveKey -> removeKey()
        }
    }

    private suspend fun reload() {
        val provider = settings.provider()
        val hasKey = settings.hasKey()
        _state.update { state ->
            when (hasKey) {
                is Result.Success -> state.copy(isLoading = false, provider = provider, hasKey = hasKey.data)
                is Result.Error -> state.copy(isLoading = false, provider = provider, error = hasKey.error.toMessage())
            }
        }
    }

    private fun choose(choice: ProviderChoice) {
        viewModelScope.launch {
            settings.choose(choice)
            _state.update { it.copy(notice = null, error = null) }
            reload()
        }
    }

    private fun saveKey() {
        val draft = _state.value.keyDraft
        if (_state.value.isSavingKey) return
        _state.update { it.copy(isSavingKey = true, notice = null, error = null) }
        viewModelScope.launch {
            when (val saved = settings.saveKey(draft)) {
                is Result.Success -> {
                    _state.update { it.copy(isSavingKey = false, keyDraft = "", notice = KEY_SAVED) }
                    reload()
                }

                is Result.Error -> {
                    _state.update { it.copy(isSavingKey = false, error = saved.error.toMessage()) }
                }
            }
        }
    }

    private fun removeKey() {
        viewModelScope.launch {
            when (val removed = settings.removeKey()) {
                is Result.Success -> {
                    _state.update { it.copy(notice = KEY_REMOVED, error = null) }
                    reload()
                }

                is Result.Error -> {
                    _state.update { it.copy(error = removed.error.toMessage()) }
                }
            }
        }
    }

    private companion object {
        const val KEY_SAVED =
            "Key saved. The agent now answers through Gemini; the Payload inspector shows what is sent before the " +
                "first call of every question."
        const val KEY_REMOVED = "Key removed. Nothing can be sent to Gemini any more."
    }
}

internal fun ProviderChoice.toUi(): ProviderChoiceUi =
    when (this) {
        ProviderChoice.Local -> {
            ProviderChoiceUi(this, "Local model", "Ollama on this computer. Your questions never leave this device.")
        }

        ProviderChoice.Cloud -> {
            ProviderChoiceUi(
                this,
                "Cloud model",
                "Google Gemini Flash, free tier, with your key. Only your question and the Tool outputs are sent, " +
                    "never a record from your Export, and you see the exact payload before the first call of every " +
                    "question. Google states that free-tier content may be used to improve its products.",
            )
        }

        ProviderChoice.Off -> {
            ProviderChoiceUi(this, "Off", "No model answers. The Insight cards and the Gap report work without one.")
        }
    }

internal fun TestedExport.toUi(): TestedExportUi =
    TestedExportUi(sourceName = source.name, format = format, downloadedOn = downloadedOn.toString(), note = note)
