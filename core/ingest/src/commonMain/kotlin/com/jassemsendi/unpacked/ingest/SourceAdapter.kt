package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EvidenceRule
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Source

/**
 * Reads the files of one Source's Export into the canonical model, and declares what in such an Export
 * counts toward each Article item. One implementation per Source; it is all a new Source adds.
 */
public sealed interface SourceAdapter {
    public val source: Source

    /** The Source's Evidence rules: what the Gap report applies to an Import of this Source, and all it reads of the adapter. */
    public val evidenceRules: List<EvidenceRule>

    /**
     * Begins reading one Export: whatever the Source must know before any file is parsed, such as
     * whose Export it is, is read here. Must not throw; what cannot be read is simply not known.
     */
    public fun begin(export: Export): ExportReader

    /**
     * One file of this Source's Export as the Records the Source wrote, with no Kind assigned: what the Explorer
     * shows. A file in none of the Source's dialects is read as plain JSON instead, with why the Source could not
     * read it; one that is not JSON at all yields no Records. Must not throw.
     */
    public fun records(
        file: ExportFile,
        text: String,
    ): FileRecords

    public companion object {
        /** Every adapter there is: the Sources an Export can be imported from, in the order the Welcome screen lists them. */
        public val all: List<SourceAdapter> = listOf(InstagramAdapter)

        /**
         * The adapter for a Source. Only an Import's Source is ever looked up, and an Import can only be of a Source
         * with an adapter, so a Source without one here is a programming error, not an outcome.
         */
        public fun of(source: Source): SourceAdapter =
            when (source) {
                Source.Instagram -> InstagramAdapter
                Source.GoogleTakeout -> error("Google Takeout has no adapter yet, so nothing can have imported it")
            }
    }
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
