package com.jassemsendi.unpacked.ui.explorer

import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.ui.home.AttributeUi
import com.jassemsendi.unpacked.ui.imports.ImportChoiceUi

data class ExplorerState(
    /** True from the start until the Imports and the chosen one's inventory have been read, and during a refresh. */
    val isLoading: Boolean = true,
    /** Every stored Import, newest first: the ones whose files can be explored. */
    val imports: List<ImportChoiceUi> = emptyList(),
    /** The Import explored, or null while there is none. */
    val selected: ImportId? = null,
    /** Every file of [selected]'s inventory; the last one read while a refresh is in flight or failed. */
    val inventory: InventoryUi? = null,
    /** True while a file's Records are being read from the Export. */
    val isOpening: Boolean = false,
    /** The file opened to its Records, if any. */
    val opened: OpenedFileUi? = null,
    /** Set when the Export is not where the Import remembers it; the screen says so and offers to pick it again. */
    val exportGone: ExportGoneUi? = null,
    /** Why the last read failed, until the next one succeeds; what is shown is the last successful read. */
    val error: String? = null,
)

/** One Import's inventory as the Explorer lists it: every file the Import gave a result, plus the media it counted. */
data class InventoryUi(
    val exportName: String,
    val sourceName: String,
    /** Where the Import remembers the Export, such as "the zip at /Users/me/Downloads/instagram-me.zip". */
    val location: String,
    /** In the order the Import visited them. */
    val files: List<ExplorerFileUi>,
    /** The media files, counted during the Import and never opened. */
    val mediaFiles: Int,
)

/** One file as the Import reported it, with whether the Explorer can show its Records. */
data class ExplorerFileUi(
    val path: String,
    val rawKind: String,
    /** Parsed, Skipped or Failed. */
    val outcome: String,
    /** The counts of a Parsed file, or the reason it was Skipped or Failed. */
    val detail: String,
    /** True for a JSON file: the only kind the Explorer reads as Records. */
    val canOpen: Boolean,
)

/** One file opened to its Records, read from the Export now. */
data class OpenedFileUi(
    val file: ExplorerFileUi,
    val records: List<RecordUi>,
    /**
     * What to say over the Records when the Source could not read the file as it does during an Import: that they
     * are its plain JSON, or that nothing in it could be read at all. Null when the Source read it.
     */
    val note: String?,
)

/** One Record: its position in the file and its label and value pairs in the file's order. */
data class RecordUi(
    val index: Int,
    val fields: List<AttributeUi>,
)

/** The Export is not where the Import remembers it. */
data class ExportGoneUi(
    /** Why, naming the remembered location. */
    val message: String,
    /** True when this platform can offer the chooser for that kind of location, so the user can point at it again. */
    val canPickAgain: Boolean,
)

sealed interface ExplorerAction {
    /** Re-read the Imports and the inventory; the screen sends it whenever it appears, as Imports may have changed. */
    data object Refresh : ExplorerAction

    /** Explore another Import. */
    data class Select(
        val id: ImportId,
    ) : ExplorerAction

    /** Read one file's Records from the Export. */
    data class Open(
        val path: String,
    ) : ExplorerAction

    data object Close : ExplorerAction

    /** Point at the Export again after it moved; the Import then remembers the new place. */
    data object PickExportAgain : ExplorerAction
}
