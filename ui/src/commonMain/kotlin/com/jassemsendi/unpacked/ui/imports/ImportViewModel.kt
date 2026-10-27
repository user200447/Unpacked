package com.jassemsendi.unpacked.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassemsendi.unpacked.data.ImportRepository
import com.jassemsendi.unpacked.data.ImportUseCase
import com.jassemsendi.unpacked.ingest.ExportLocation
import com.jassemsendi.unpacked.ingest.ExportPart
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.model.ImportId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

class ImportViewModel(
    private val imports: ImportRepository,
    private val importUseCase: ImportUseCase,
    private val picker: ExportPicker,
    private val timeZone: TimeZone,
) : ViewModel() {
    private val _state =
        MutableStateFlow(ImportState(canPickZip = picker.canPickZip, canPickFolder = picker.canPickFolder))
    val state = _state.asStateFlow()

    private val _events = Channel<ImportEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { refreshImports() }
    }

    fun onAction(action: ImportAction) {
        when (action) {
            ImportAction.PickZip -> pickAndImport { picker.pickZip() }
            ImportAction.PickFolder -> pickAndImport { picker.pickFolder() }
            is ImportAction.DeleteImport -> delete(action.id)
        }
    }

    private fun pickAndImport(pick: suspend () -> ExportLocation?) {
        if (_state.value.run is ImportRunUi.Running) return
        viewModelScope.launch {
            val location = pick() ?: return@launch
            runImport(location)
        }
    }

    private suspend fun runImport(location: ExportLocation) {
        val name = location.exportId.value
        _state.update {
            it.copy(part = ExportPart.of(location.exportId), run = ImportRunUi.Running(name))
        }
        importUseCase
            .run(location)
            .catch { error -> fail(name, error) }
            .collect { step -> advance(step) }
    }

    private suspend fun advance(step: ImportStep) {
        when (step) {
            is ImportStep.Started -> {
                Unit
            }

            // the first FileStarted brings the total; until then the Export is being listed
            is ImportStep.FileStarted -> {
                updateRun {
                    it.copy(
                        filesDone = step.progress.index,
                        filesTotal = step.progress.total,
                        currentFile = step.path,
                    )
                }
            }

            is ImportStep.FileFinished -> {
                updateRun {
                    it.copy(
                        filesDone = step.progress.index + 1,
                        filesTotal = step.progress.total,
                        currentFile = null,
                        files = it.files + step.outcome.result.toUi(),
                    )
                }
            }

            is ImportStep.Stored -> {
                _state.update { it.copy(run = ImportRunUi.Finished(step.import.toUi(timeZone))) }
                refreshImports()
            }

            is ImportStep.Completed -> {
                Unit
            } // a library-backed run ends with Stored instead
        }
    }

    private suspend fun fail(
        name: String,
        error: Throwable,
    ) {
        _state.update { it.copy(run = ImportRunUi.Failed(name, error.message ?: "unknown error")) }
        // A run that stopped half-way may have left an incomplete Import behind; show it.
        refreshImports()
    }

    private fun updateRun(transform: (ImportRunUi.Running) -> ImportRunUi.Running) {
        _state.update { state ->
            val run = state.run
            if (run is ImportRunUi.Running) state.copy(run = transform(run)) else state
        }
    }

    private fun delete(id: ImportId) {
        viewModelScope.launch {
            val name =
                _state.value.imports
                    .find { it.id == id }
                    ?.exportName ?: return@launch
            imports.delete(id)
            _state.update { state ->
                val run = state.run
                state.copy(run = if (run is ImportRunUi.Finished && run.import.id == id) ImportRunUi.Idle else run)
            }
            refreshImports()
            _events.send(ImportEvent.ImportDeleted(name))
        }
    }

    private suspend fun refreshImports() {
        val stored = imports.all().asReversed().map { it.toUi(timeZone) }
        _state.update { it.copy(imports = stored) }
    }
}
