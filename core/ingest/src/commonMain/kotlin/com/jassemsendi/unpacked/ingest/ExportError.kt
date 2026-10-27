package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.Error
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.CancellationException
import okio.FileNotFoundException

/** Why an Export could not be opened or listed. */
public enum class ExportError : Error {
    /** Nothing is at the location: the zip or folder was moved or deleted since it was picked. */
    NOT_FOUND,

    /** Something is there, but it is not an Export that can be read: a file that is not a zip, or a folder that will not list. */
    NOT_READABLE,
}

/**
 * Runs one file-system access, turning what it throws into the [ExportError] it means. The one place
 * ingest catches file-system exceptions; cancellation is never swallowed.
 */
internal inline fun <T> exportCall(block: () -> T): Result<T, ExportError> =
    runCatching(block).fold(
        onSuccess = { Result.Success(it) },
        onFailure = { error ->
            if (error is CancellationException) throw error
            Result.Error(if (error is FileNotFoundException) ExportError.NOT_FOUND else ExportError.NOT_READABLE)
        },
    )
