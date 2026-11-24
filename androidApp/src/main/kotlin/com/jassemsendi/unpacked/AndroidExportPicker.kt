package com.jassemsendi.unpacked

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.ui.imports.ExportPicker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Storage Access Framework picker for a single zip (ADR 0008); Android offers no folder to pick. The picker
 * is one object for the process with no Activity of its own: the Activity on screen attaches as its [Host] and
 * opens the system picker on request, and the result comes back here through whichever Activity is attached by
 * then, so a rotation while the picker is open loses nothing. The picked document is read in place through
 * [ContentFileSystem], and its read grant is kept across restarts so the Explorer can open the Export again.
 */
internal class AndroidExportPicker(
    private val contentResolver: ContentResolver,
) : ExportPicker {
    /** The Activity that can open the system picker; it reports what the user picked through [onPicked]. */
    interface Host {
        fun openZip()
    }

    override val canPickZip: Boolean = true
    override val canPickFolder: Boolean = false

    private var host: Host? = null
    private var pending: CompletableDeferred<Uri?>? = null

    fun attach(host: Host) {
        this.host = host
    }

    fun detach(host: Host) {
        if (this.host === host) this.host = null
    }

    override suspend fun pickZip(): ExportLocation? {
        val uri = awaitPick() ?: return null
        return withContext(Dispatchers.IO) { locationOf(uri) }
    }

    /** Opens the system picker through the attached Host and waits for its answer; null with no Host on screen. */
    private suspend fun awaitPick(): Uri? {
        val host = host ?: return null
        pending?.complete(null)
        val picked = CompletableDeferred<Uri?>()
        pending = picked
        host.openZip()
        return picked.await()
    }

    override suspend fun pickFolder(): ExportLocation? = null

    /** The system picker's answer: the document, or null when the user backed out. */
    fun onPicked(uri: Uri?) {
        pending?.complete(uri)
        pending = null
    }

    private fun locationOf(uri: Uri): ExportLocation {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Not every provider offers a persistable grant; the Import still runs on the one-time grant.
        }
        val name = displayName(uri) ?: uri.lastPathSegment ?: "export"
        return ExportLocation.Zip(ContentPaths.pathOf(uri.toString(), name).toString())
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
}
