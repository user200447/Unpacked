package com.jassemsendi.unpacked.fixtures

import okio.FileSystem
import okio.Path

/** Writes UTF-8 text, creating parent directories first. Shared by the generator and the tests. */
public fun FileSystem.writeText(
    path: Path,
    text: String,
) {
    path.parent?.let { createDirectories(it) }
    write(path) { writeUtf8(text) }
}

/** Every regular file under [root] as UTF-8 text, by its path relative to [root] with `/` between segments, in path order. */
public fun FileSystem.textFiles(root: Path): Map<String, String> =
    listRecursively(root)
        .filter { metadata(it).isRegularFile }
        .map { path -> path.relativeTo(root).segments.joinToString("/") to read(path) { readUtf8() } }
        .sortedBy { (path, _) -> path }
        .toMap()
