package com.jassemsendi.unpacked

import okio.Path
import okio.Path.Companion.toPath

/**
 * How an Export picked through the Storage Access Framework is written as an Export location path: the document
 * URI's authority and path under a `/content` root, then the zip's display name as the last segment. The Export's
 * id and part are read from the name exactly as they are from a file path on the other platforms, and the URI is
 * read back from the segments in between.
 */
internal object ContentPaths {
    private const val ROOT = "content"
    private const val SCHEME = "content://"

    fun pathOf(
        uri: String,
        displayName: String,
    ): Path {
        require(uri.startsWith(SCHEME)) { "not a document URI: $uri" }
        val name = displayName.replace('/', '_').takeUnless { it.isEmpty() || it == "." || it == ".." } ?: "export"
        return "/$ROOT/${uri.removePrefix(SCHEME)}/$name".toPath()
    }

    /** The document URI a path made by [pathOf] stands for; null for any other path. */
    fun uriOf(path: Path): String? {
        val segments = path.segments
        if (!path.isAbsolute || segments.size < MIN_SEGMENTS || segments.first() != ROOT) return null
        return SCHEME + segments.subList(1, segments.size - 1).joinToString("/")
    }

    // The root, at least one URI segment, and the name.
    private const val MIN_SEGMENTS = 3
}
