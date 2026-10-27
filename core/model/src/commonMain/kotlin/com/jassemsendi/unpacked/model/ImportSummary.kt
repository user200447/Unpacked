package com.jassemsendi.unpacked.model

/** What happened to one file of an Export during an Import. */
public sealed interface FileResult {
    public val path: String
    public val rawKind: RawKind

    public data class Parsed(
        override val path: String,
        override val rawKind: RawKind,
        val recordCount: Int,
        val eventCount: Int,
        val claimCount: Int,
    ) : FileResult

    public data class Skipped(
        override val path: String,
        override val rawKind: RawKind,
        val reason: String,
    ) : FileResult

    public data class Failed(
        override val path: String,
        override val rawKind: RawKind,
        val reason: String,
    ) : FileResult
}

/** Counts of what the Export contains, by file class. Media files are counted and never opened. */
public data class Inventory(
    val jsonFiles: Int,
    val mediaFiles: Int,
    val otherFiles: Int,
)

/** The outcome of one Import of one Export. */
public data class ImportSummary(
    val exportId: String,
    val source: Source,
    val files: List<FileResult>,
    val inventory: Inventory,
) {
    val parsed: List<FileResult.Parsed> get() = files.filterIsInstance<FileResult.Parsed>()
    val skipped: List<FileResult.Skipped> get() = files.filterIsInstance<FileResult.Skipped>()
    val failed: List<FileResult.Failed> get() = files.filterIsInstance<FileResult.Failed>()
    val eventCount: Int get() = parsed.sumOf { it.eventCount }
    val claimCount: Int get() = parsed.sumOf { it.claimCount }
}
