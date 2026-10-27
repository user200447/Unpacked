package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.ExportId

/**
 * Which part of a split Export a name says it is. A Source that splits a large Export into several
 * zips numbers them in the file name; only one part is imported at a time, and the user is told so.
 * [count] is null when the name gives the part number but not how many parts there are.
 */
public data class ExportPart(
    val number: Int,
    val count: Int?,
) {
    public companion object {
        // "part-2-of-3", "part 2 of 3", "part2", "(part 2 of 3)"
        private val PART = Regex("""\bpart[\s_-]*(\d+)(?:[\s_-]*of[\s_-]*(\d+))?""", RegexOption.IGNORE_CASE)

        public fun of(id: ExportId): ExportPart? =
            PART.find(id.value)?.let { match ->
                ExportPart(match.groupValues[1].toInt(), match.groupValues[2].toIntOrNull())
            }
    }
}
