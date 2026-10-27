package com.jassemsendi.unpacked.data

import androidx.sqlite.SQLiteException
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.CancellationException

/**
 * Runs one library access, turning what it throws into the [DataError.Local] it means. The one place
 * core:data catches database exceptions, so nothing above it ever sees one; cancellation is never swallowed.
 */
internal inline fun <T> localCall(block: () -> T): Result<T, DataError.Local> =
    runCatching(block).fold(
        onSuccess = { Result.Success(it) },
        onFailure = { error ->
            if (error is CancellationException) throw error
            Result.Error(error.toLocalError())
        },
    )

/**
 * The bundled driver reports SQLite's primary result code in the message ("Error code: 13, message:
 * database or disk is full"); Android's own driver would throw a `SQLiteFullException`. Both read as full.
 */
internal fun Throwable.toLocalError(): DataError.Local =
    localErrorOf(this::class.simpleName, if (this is SQLiteException) message else null)

/** The mapping itself, by the exception's name and what SQLite said; pure so every target can test it. */
internal fun localErrorOf(
    exceptionName: String?,
    sqliteMessage: String?,
): DataError.Local {
    val text = sqliteMessage.orEmpty()
    val isFull = exceptionName == "SQLiteFullException" || fullCode.containsMatchIn(text) || "disk is full" in text
    return if (isFull) DataError.Local.DISK_FULL else DataError.Local.UNKNOWN
}

private const val SQLITE_FULL = 13
private val fullCode = Regex("^Error code: $SQLITE_FULL(?:\\D|$)")
