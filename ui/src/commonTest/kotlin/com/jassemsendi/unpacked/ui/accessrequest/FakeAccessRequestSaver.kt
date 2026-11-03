package com.jassemsendi.unpacked.ui.accessrequest

import com.jassemsendi.unpacked.model.Result

/** Records every letter handed to it; a test sets where it goes, null for a cancelled dialog, or a [failure]. */
class FakeAccessRequestSaver(
    var destination: String? = "/Users/me/Documents/access-request.txt",
    var failure: AccessRequestSaveError? = null,
) : AccessRequestSaver {
    override val isAvailable: Boolean = true
    val saved = mutableListOf<Pair<String, String>>()

    override suspend fun save(
        fileName: String,
        text: String,
    ): Result<String?, AccessRequestSaveError> {
        saved += fileName to text
        return failure?.let { Result.Error(it) } ?: Result.Success(destination)
    }
}
