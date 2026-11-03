package com.jassemsendi.unpacked.ui.accessrequest

import com.jassemsendi.unpacked.model.Error
import com.jassemsendi.unpacked.model.Result

/**
 * The platform's way of handing a drafted letter out of the app as a text file: a save dialog on desktop, the
 * share sheet on a phone. Each app binds its own (ADR 0008); a platform without one says so and the Access
 * request screen offers copying alone.
 */
interface LetterExporter {
    val isAvailable: Boolean

    /**
     * Hands [text] out under [fileName]. Success carries where the letter went, in words the user can follow,
     * or null when the user cancelled.
     */
    suspend fun export(
        fileName: String,
        text: String,
    ): Result<String?, LetterExportError>
}

/** Why a letter could not be handed out: the one failure a user can meet once a place has been chosen. */
enum class LetterExportError : Error {
    /** The chosen place refused the file: a read-only folder, a full disk, a missing permission. */
    NOT_WRITABLE,
}

fun LetterExportError.toMessage(): String =
    when (this) {
        LetterExportError.NOT_WRITABLE -> "The letter could not be written there."
    }

/** For a platform whose exporter is not bound yet: the letter can only be copied, and the screen says so. */
object NoLetterExporter : LetterExporter {
    override val isAvailable: Boolean = false

    override suspend fun export(
        fileName: String,
        text: String,
    ): Result<String?, LetterExportError> = Result.Success(null)
}
