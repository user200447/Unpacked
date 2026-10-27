package com.jassemsendi.unpacked.fixtures

import okio.FileSystem
import okio.Path

/** Writes UTF-8 text, creating parent directories first. Shared by the generator and the tests. */
fun FileSystem.writeText(
    path: Path,
    text: String,
) {
    path.parent?.let { createDirectories(it) }
    write(path) { writeUtf8(text) }
}
