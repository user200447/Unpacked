package com.jassemsendi.unpacked.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassemsendi.unpacked.data.ImportRepository
import com.jassemsendi.unpacked.ingest.ExplorerError
import com.jassemsendi.unpacked.ingest.ExportFileReader
import com.jassemsendi.unpacked.ingest.exportId
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.ui.imports.ExportPicker
import com.jassemsendi.unpacked.ui.imports.toChoice
import com.jassemsendi.unpacked.ui.toMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The Explorer: every file of one stored Import, chosen among the stored ones, and any one of them opened to the
 * Records read now from where the Import remembers its Export. Nothing is read until the screen asks for an
 * [ExplorerAction.Refresh].
 */
class ExplorerViewModel(
    private val imports: ImportRepository,
    private val reader: ExportFileReader,
    private val picker: ExportPicker,
    private val timeZone: TimeZone,
) : ViewModel() {
    private val _state = MutableStateFlow(ExplorerState())
    val state = _state.asStateFlow()

    /** Bumped by every reload so an inventory in flight for an older choice is dropped on arrival. */
    private var generation = 0

    /** The Import explored, as stored: its location and Source are what every read needs. */
    private var selected: Import? = null

    /** The file asked for when the Export turned out to be gone; opened once the Export is found again. */
    private var pendingOpen: String? = null

    fun onAction(action: ExplorerAction) {
        when (action) {
            ExplorerAction.Refresh -> reload(_state.value.selected)
            is ExplorerAction.Select -> reload(action.id)
            is ExplorerAction.Open -> open(action.path)
            ExplorerAction.Close -> _state.update { it.copy(opened = null) }
            ExplorerAction.PickExportAgain -> pickExportAgain()
        }
    }

    private fun reload(requested: ImportId?) {
        val current = ++generation
        // Another Import closes what was open at once; a plain refresh keeps the last inventory until the new one is in.
        _state.update {
            if (requested == it.selected) {
                it.copy(isLoading = true)
            } else {
                it.copy(isLoading = true, selected = requested, inventory = null, opened = null, exportGone = null)
            }
        }
        viewModelScope.launch {
            val read = imports.all()
            if (current != generation) return@launch
            _state.update { state ->
                when (read) {
                    is Result.Success -> {
                        val newestFirst = read.data.asReversed()
                        val chosen = newestFirst.choose(requested)
                        selected = chosen
                        state.copy(
                            isLoading = false,
                            error = null,
                            imports = newestFirst.map { it.toChoice(timeZone) },
                            selected = chosen?.id,
                            inventory = chosen?.toInventory(),
                        )
                    }

                    is Result.Error -> {
                        state.copy(isLoading = false, error = read.error.toMessage())
                    }
                }
            }
        }
    }

    /**
     * The one asked for when it is still there; otherwise the latest that finished, or failing that the latest at all,
     * since an Import that stopped early still lists the files it visited.
     */
    private fun List<Import>.choose(requested: ImportId?): Import? =
        firstOrNull { it.id == requested }
            ?: firstOrNull { it.completedAt != null }
            ?: firstOrNull()

    private fun open(path: String) {
        val import = selected ?: return
        val file =
            _state.value.inventory
                ?.files
                ?.firstOrNull { it.path == path }
        if (file == null) {
            _state.update { it.copy(opened = null, error = fileGone(path)) }
            return
        }
        _state.update { it.copy(isOpening = true) }
        viewModelScope.launch {
            val read = reader.read(import.location, import.summary.source, path)
            _state.update { state ->
                when (read) {
                    is Result.Success -> {
                        state.copy(
                            isOpening = false,
                            opened = read.data.toOpened(file, import.summary.source),
                            exportGone = null,
                            error = null,
                        )
                    }

                    is Result.Error -> {
                        when (val error = read.error) {
                            is ExplorerError.ExportGone -> {
                                pendingOpen = path
                                state.copy(
                                    isOpening = false,
                                    opened = null,
                                    exportGone =
                                        error.error.toExportGone(
                                            import.location,
                                            picker.canPick(import.location),
                                        ),
                                    error = null,
                                )
                            }

                            ExplorerError.FileGone -> {
                                state.copy(isOpening = false, opened = null, error = fileGone(path))
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The user points at the Export again, with the chooser for the kind of place it was. A cancelled pick changes
     * nothing; a different Export is refused by name; the right one is remembered and the file asked for opens from it.
     */
    private fun pickExportAgain() {
        val import = selected ?: return
        viewModelScope.launch {
            val picked = picker.pick(import.location) ?: return@launch
            val expected = import.summary.exportId
            if (picked.exportId != expected) {
                _state.update {
                    it.copy(
                        error =
                            "That is ${picked.exportId.value}, not ${expected.value}: " +
                                "pick the Export this Import was read from.",
                    )
                }
                return@launch
            }
            when (val relocated = imports.relocate(import.id, picked)) {
                is Result.Error -> {
                    _state.update { it.copy(error = relocated.error.toMessage()) }
                }

                is Result.Success -> {
                    val moved = import.copy(location = picked)
                    selected = moved
                    _state.update { it.copy(exportGone = null, error = null, inventory = moved.toInventory()) }
                    pendingOpen?.let { path ->
                        pendingOpen = null
                        open(path)
                    }
                }
            }
        }
    }

    private fun ExportPicker.canPick(location: ExportLocation): Boolean =
        when (location) {
            is ExportLocation.Zip -> canPickZip
            is ExportLocation.Folder -> canPickFolder
        }

    private suspend fun ExportPicker.pick(location: ExportLocation): ExportLocation? =
        when (location) {
            is ExportLocation.Zip -> pickZip()
            is ExportLocation.Folder -> pickFolder()
        }

    private fun fileGone(path: String): String = "The Export no longer holds $path."
}
