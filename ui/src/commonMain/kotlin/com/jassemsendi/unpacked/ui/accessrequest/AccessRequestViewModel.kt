package com.jassemsendi.unpacked.ui.accessrequest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassemsendi.unpacked.insights.GapReportReader
import com.jassemsendi.unpacked.insights.accessRequest
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.ui.toMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Access request screen: the letter drafted from one Import's Gap report, to copy or to hand to the platform
 * as a text file. Nothing is read until the screen names an Import with [AccessRequestAction.Load].
 */
class AccessRequestViewModel(
    private val reader: GapReportReader,
    private val exporter: LetterExporter,
) : ViewModel() {
    private val _state = MutableStateFlow(AccessRequestState(canExport = exporter.isAvailable))
    val state = _state.asStateFlow()

    fun onAction(action: AccessRequestAction) {
        when (action) {
            is AccessRequestAction.Load -> load(action.importId)
            AccessRequestAction.Export -> export()
        }
    }

    private fun load(importId: ImportId) {
        _state.update { it.copy(isLoading = true, exportedTo = null) }
        viewModelScope.launch {
            val read = reader.read(importId)
            _state.update {
                when (read) {
                    is Result.Success -> {
                        it.copy(isLoading = false, letter = read.data.accessRequest().toUi(), error = null)
                    }

                    is Result.Error -> {
                        it.copy(isLoading = false, letter = null, error = read.error.toMessage())
                    }
                }
            }
        }
    }

    private fun export() {
        val letter = _state.value.letter ?: return
        if (!exporter.isAvailable) return
        viewModelScope.launch {
            val exported = exporter.export(letter.fileName, letter.text)
            _state.update {
                when (exported) {
                    is Result.Success -> it.copy(exportedTo = exported.data, error = null)
                    is Result.Error -> it.copy(exportedTo = null, error = exported.error.toMessage())
                }
            }
        }
    }
}
