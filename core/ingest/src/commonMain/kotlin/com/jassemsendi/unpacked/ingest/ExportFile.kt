package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.RawKind

/** One file inside an Export, identified by its path relative to the Export root. */
public data class ExportFile(
    val path: String,
) {
    val name: String get() = path.substringAfterLast('/')
    val extension: String get() = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    /** The Raw kind this file gives its records: the file name without extension, lowercased. */
    val rawKind: RawKind get() = RawKind(name.substringBeforeLast('.').lowercase())
    val isJson: Boolean get() = extension == "json"
    val isMedia: Boolean get() = extension in MEDIA_EXTENSIONS

    private companion object {
        val MEDIA_EXTENSIONS =
            setOf(
                "jpg",
                "jpeg",
                "png",
                "gif",
                "webp",
                "heic",
                "heif",
                "bmp",
                "mp4",
                "mov",
                "m4v",
                "webm",
                "aac",
                "ogg",
                "wav",
                "mp3",
                "m4a",
            )
    }
}
