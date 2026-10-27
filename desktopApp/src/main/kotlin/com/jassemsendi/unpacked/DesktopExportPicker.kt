package com.jassemsendi.unpacked

import com.jassemsendi.unpacked.ingest.ExportLocation
import com.jassemsendi.unpacked.ui.imports.ExportPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import javax.swing.JFileChooser

/**
 * The desktop file and folder chooser: the native file dialog for a zip, and for a folder the
 * native dialog on macOS (which can only pick folders through a system property) or Swing's elsewhere.
 */
class DesktopExportPicker : ExportPicker {
    override val canPickZip: Boolean = true
    override val canPickFolder: Boolean = true

    override suspend fun pickZip(): ExportLocation? =
        withContext(Dispatchers.Swing) {
            val dialog = FileDialog(null as Frame?, "Choose an Export zip", FileDialog.LOAD)
            // macOS honours the filter; Windows ignores it and reads the pattern from the file field instead.
            dialog.setFilenameFilter { _, name -> name.endsWith(".zip", ignoreCase = true) }
            dialog.file = "*.zip"
            dialog.isVisible = true
            dialog.files.firstOrNull()?.let { ExportLocation.Zip(it.absolutePath) }
        }

    override suspend fun pickFolder(): ExportLocation? =
        withContext(Dispatchers.Swing) {
            if (HostOs.isMac) pickFolderNatively() else pickFolderWithSwing()
        }

    private fun pickFolderNatively(): ExportLocation? {
        System.setProperty(MAC_FOLDER_DIALOG, "true")
        try {
            val dialog = FileDialog(null as Frame?, "Choose an unzipped Export folder", FileDialog.LOAD)
            dialog.isVisible = true
            return dialog.files.firstOrNull()?.let { ExportLocation.Folder(it.absolutePath) }
        } finally {
            System.setProperty(MAC_FOLDER_DIALOG, "false")
        }
    }

    private fun pickFolderWithSwing(): ExportLocation? {
        val chooser =
            JFileChooser().apply {
                dialogTitle = "Choose an unzipped Export folder"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            ExportLocation.Folder(chooser.selectedFile.absolutePath)
        } else {
            null
        }
    }

    private companion object {
        const val MAC_FOLDER_DIALOG = "apple.awt.fileDialogForDirectories"
    }
}
