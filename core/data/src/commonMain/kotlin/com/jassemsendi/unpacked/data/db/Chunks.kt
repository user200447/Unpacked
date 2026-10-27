package com.jassemsendi.unpacked.data.db

/** SQLite caps bound variables per statement; keep `IN (:ids)` lists well under the limit. */
private const val CHUNK = 500

/** Runs [query] once per chunk of this list and concatenates the results, in order. */
internal suspend fun <T, R> List<T>.inChunks(query: suspend (List<T>) -> List<R>): List<R> =
    if (size <= CHUNK) query(this) else chunked(CHUNK).flatMap { query(it) }

/** Runs [statement] once per chunk of this list. */
internal suspend fun <T> List<T>.forEachChunk(statement: suspend (List<T>) -> Unit) {
    if (isEmpty()) return
    if (size <= CHUNK) statement(this) else chunked(CHUNK).forEach { statement(it) }
}
