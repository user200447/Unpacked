package com.jassemsendi.unpacked.ui.accessrequest

import com.jassemsendi.unpacked.model.ImportId

data class AccessRequestState(
    /** True from the start until the report has been read and the letter drafted. */
    val isLoading: Boolean = true,
    /** The Access request over the Import named by the last [AccessRequestAction.Load]. */
    val request: AccessRequestUi? = null,
    /** Whether this platform can hand the letter out as a text file; copying is always possible. */
    val canSave: Boolean = false,
    /** Where the last save went, until the next one; null after a cancelled one. */
    val savedTo: String? = null,
    /** Why the last read or save failed, until the next one succeeds. */
    val error: String? = null,
)

/** The Access request as the screen shows it. */
data class AccessRequestUi(
    val exportName: String,
    val sourceName: String,
    /** Which items the letter asks for, by article letter, or that nothing is outstanding. */
    val summary: String,
    /** The whole letter, as copied or saved. */
    val text: String,
    /** The name the saved text file is offered under. */
    val fileName: String,
)

sealed interface AccessRequestAction {
    /** Draft the letter over this Import; the screen sends it whenever it appears. */
    data class Load(
        val importId: ImportId,
    ) : AccessRequestAction

    /** Hand the letter to the platform as a text file. */
    data object Save : AccessRequestAction
}
