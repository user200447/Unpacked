package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Source

/** Reads the files of one Source's Export into the canonical model. One implementation per Source. */
public sealed interface SourceAdapter {
    public val source: Source

    /** Turns one JSON file's text into records. Must not throw for bad content; report it as Failed. */
    public fun parse(
        file: ExportFile,
        text: String,
        exportId: String,
    ): FileOutcome
}

/** What one file produced: its result for the summary plus the records to hand on. */
public data class FileOutcome(
    val result: FileResult,
    val events: List<Event> = emptyList(),
    val claims: List<Claim> = emptyList(),
)
