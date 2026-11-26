package com.jassemsendi.unpacked.ui.imports

import com.jassemsendi.unpacked.model.ExportLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import kotlin.coroutines.resume

/**
 * What shows the system document picker and reports its answer: UIKit in the app
 * ([UiKitDocumentPickerPresenter]), a fake in tests. Called on the main thread.
 */
internal fun interface DocumentPickerPresenter {
    /** Shows the picker for one zip; [onPicked] is called exactly once, with the file or null when the user backed out. */
    fun pickZip(onPicked: (NSURL?) -> Unit)
}

/**
 * The iOS document picker for a single zip (ADR 0008); the app offers no folder to pick on iOS. The zip is opened in place,
 * so it is read where it is through the file system by its path, under the security scope the picker grants.
 * That scope is held for the rest of the process, so the Explorer can open the same Export again without asking;
 * after a relaunch the Export outside the app's own documents is no longer reachable, and the Explorer offers the
 * picker again, as it does for an Export that moved.
 */
internal class IosExportPicker(
    private val presenter: DocumentPickerPresenter,
) : ExportPicker {
    override val canPickZip: Boolean = true
    override val canPickFolder: Boolean = false

    /** The picked files whose security scope this process holds; a scope lives as long as its URL object does. */
    private val scoped = mutableListOf<NSURL>()

    override suspend fun pickZip(): ExportLocation? {
        val url = awaitPick()?.takeIf { it.fileURL } ?: return null
        if (url.startAccessingSecurityScopedResource()) scoped += url
        return url.path?.let { ExportLocation.Zip(it) }
    }

    override suspend fun pickFolder(): ExportLocation? = null

    // A caller cancelled while the sheet is up (its screen gone) leaves the sheet to the user; its late answer is dropped.
    private suspend fun awaitPick(): NSURL? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                presenter.pickZip { picked -> continuation.resume(picked) }
            }
        }
}
