package com.jassemsendi.unpacked.ingest

import okio.FileSystem
import okio.Path

/** Writes UTF-8 text, creating parent directories first. Shared by the generator and the tests. */
internal fun FileSystem.writeText(
    path: Path,
    text: String,
) {
    path.parent?.let { createDirectories(it) }
    write(path) { writeUtf8(text) }
}
