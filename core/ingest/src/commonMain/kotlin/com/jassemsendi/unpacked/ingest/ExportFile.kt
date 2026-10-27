package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind

/** One file inside an Export, identified by the Export and its path relative to the Export root. */
public data class ExportFile(
    val exportId: ExportId,
    val path: String,
) {
    val name: String get() = path.substringAfterLast('/')
    val extension: String get() = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    /** The Raw kind this file gives its records: the file name without extension, lowercased. */
    val rawKind: RawKind get() = RawKind(name.substringBeforeLast('.').lowercase())
    val isJson: Boolean get() = extension == "json"
    val isMedia: Boolean get() = extension in MEDIA_EXTENSIONS

    /** Where the record at [recordIndex] in this file came from. */
    public fun provenanceOf(recordIndex: Int): Provenance = Provenance(exportId, path, recordIndex)

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
