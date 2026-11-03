package com.jassemsendi.unpacked

import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.ui.accessrequest.LetterExportError
import com.jassemsendi.unpacked.ui.accessrequest.LetterExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException

/** The desktop save dialog: the native one, offered the letter's file name, writing the text where the user points. */
class DesktopLetterExporter : LetterExporter {
    override val isAvailable: Boolean = true

    override suspend fun export(
        fileName: String,
        text: String,
    ): Result<String?, LetterExportError> {
        val chosen = chooseFile(fileName) ?: return Result.Success(null)
        return withContext(Dispatchers.IO) {
            try {
                chosen.writeText(text)
                Result.Success(chosen.absolutePath)
            } catch (_: IOException) {
                Result.Error(LetterExportError.NOT_WRITABLE)
            }
        }
    }

    private suspend fun chooseFile(fileName: String): File? =
        withContext(Dispatchers.Swing) {
            val dialog = FileDialog(null as Frame?, "Save the Access request", FileDialog.SAVE)
            dialog.file = fileName
            dialog.isVisible = true
            dialog.files.firstOrNull()
        }
}
