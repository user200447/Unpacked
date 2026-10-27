package com.jassemsendi.unpacked.ui.imports

import com.jassemsendi.unpacked.ingest.ExportLocation

/**
 * The platform's way of letting the user point at an Export. Each app binds its own; a platform
 * that cannot offer one of the two choices reports so and the Import screen hides that choice.
 */
interface ExportPicker {
    val canPickZip: Boolean
    val canPickFolder: Boolean

    /** Null when the user cancels. */
    suspend fun pickZip(): ExportLocation?

    /** Null when the user cancels. */
    suspend fun pickFolder(): ExportLocation?
}

/** For a platform whose chooser is not bound yet: nothing can be picked, and the screen says so. */
object NoExportPicker : ExportPicker {
    override val canPickZip: Boolean = false
    override val canPickFolder: Boolean = false

    override suspend fun pickZip(): ExportLocation? = null

    override suspend fun pickFolder(): ExportLocation? = null
}
