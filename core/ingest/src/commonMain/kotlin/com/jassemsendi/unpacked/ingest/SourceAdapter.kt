package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Source

/** Reads the files of one Source's Export into the canonical model. One implementation per Source. */
public sealed interface SourceAdapter {
    public val source: Source

    /**
     * Begins reading one Export: whatever the Source must know before any file is parsed, such as
     * whose Export it is, is read here. Must not throw; what cannot be read is simply not known.
     */
    public fun begin(export: Export): ExportReader
}

/** Reads the files of the one Export it was begun for. */
public fun interface ExportReader {
    /** Turns one JSON file's text into Events and Claims. Must not throw for bad content; report it as Failed. */
    public fun parse(
        file: ExportFile,
        text: String,
    ): FileOutcome
}

/** The Events and Claims that one record, or one whole file, produced. */
public data class Output(
    val events: List<Event> = emptyList(),
    val claims: List<Claim> = emptyList(),
) {
    val isEmpty: Boolean get() = events.isEmpty() && claims.isEmpty()

    public operator fun plus(other: Output): Output = Output(events + other.events, claims + other.claims)

    public companion object {
        public val NONE: Output = Output()
    }
}

/** What one file produced: its result for the summary plus the Events and Claims to hand on. */
public data class FileOutcome(
    val result: FileResult,
    val output: Output = Output.NONE,
)
