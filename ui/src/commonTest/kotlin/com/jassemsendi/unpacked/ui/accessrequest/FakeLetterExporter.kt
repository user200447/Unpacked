package com.jassemsendi.unpacked.ui.accessrequest

import com.jassemsendi.unpacked.model.Result

/** Records every letter handed to it; a test sets where it goes, null for a cancelled dialog, or a [failure]. */
class FakeLetterExporter(
    var destination: String? = "/Users/me/Documents/access-request.txt",
    var failure: LetterExportError? = null,
) : LetterExporter {
    override val isAvailable: Boolean = true
    val exported = mutableListOf<Pair<String, String>>()

    override suspend fun export(
        fileName: String,
        text: String,
    ): Result<String?, LetterExportError> {
        exported += fileName to text
        return failure?.let { Result.Error(it) } ?: Result.Success(destination)
    }
}
