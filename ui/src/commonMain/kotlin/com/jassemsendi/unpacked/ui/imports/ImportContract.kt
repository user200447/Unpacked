package com.jassemsendi.unpacked.ui.imports

import com.jassemsendi.unpacked.ingest.ExportPart
import com.jassemsendi.unpacked.model.ImportId

data class ImportState(
    val canPickZip: Boolean = false,
    val canPickFolder: Boolean = false,
    /** Every stored Import, newest first. */
    val imports: List<ImportUi> = emptyList(),
    val run: ImportRunUi = ImportRunUi.Idle,
    /** Set when the picked Export's name says it is one part of several; the run still goes ahead. */
    val part: ExportPart? = null,
    /** Why the stored Imports could not be listed, when they could not; the list is then the last one read. */
    val error: String? = null,
)

/** The Import in progress on screen, if any. One runs at a time. */
sealed interface ImportRunUi {
    data object Idle : ImportRunUi

    data class Running(
        val exportName: String,
        val filesDone: Int = 0,
        /** Null until the first file starts: the Export is still being listed. */
        val filesTotal: Int? = null,
        /** The file being read, or null between files and while the stored Import is being finished. */
        val currentFile: String? = null,
        /** Files finished so far, in visit order. */
        val files: List<FileResultUi> = emptyList(),
    ) : ImportRunUi

    data class Finished(
        val import: ImportUi,
    ) : ImportRunUi

    /** The run stopped before storing an Import: the location would not open, or the library refused. */
    data class Failed(
        val exportName: String,
        val reason: String,
    ) : ImportRunUi
}

data class ImportUi(
    val id: ImportId,
    val exportName: String,
    val sourceName: String,
    /** When the run started, in the device's time zone, such as "2026-10-27 10:12". */
    val startedAt: String,
    /** False for a run that was cancelled or crashed (ADR 0007); it is still listed and deletable. */
    val isComplete: Boolean,
    val eventCount: Int,
    val claimCount: Int,
    val parsed: List<FileResultUi>,
    val skipped: List<FileResultUi>,
    val failed: List<FileResultUi>,
)

/** One file of an Export as the Import reported it: the reason it was Skipped or Failed, or its counts. */
data class FileResultUi(
    val path: String,
    val detail: String,
)

sealed interface ImportAction {
    data object PickZip : ImportAction

    data object PickFolder : ImportAction

    data class DeleteImport(
        val id: ImportId,
    ) : ImportAction
}

sealed interface ImportEvent {
    data class ImportDeleted(
        val exportName: String,
    ) : ImportEvent

    /** The Import is still there; [reason] is what the library said. */
    data class ImportNotDeleted(
        val exportName: String,
        val reason: String,
    ) : ImportEvent
}
