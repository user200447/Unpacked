package com.jassemsendi.unpacked.buildlogic

import java.util.Locale
import kotlin.math.roundToInt

/** One file as cloc counted it: its path relative to the repository root and its lines of code. */
data class CountedFile(
    val path: String,
    val code: Int,
)

/** What a source set is for: the table totals production and test code separately and keeps build code apart. */
enum class Role { Production, Test, Build }

/** One row of the table: a source set, the modules that have one, and what cloc counted in them. */
data class Row(
    val sourceSet: String,
    val role: Role,
    val modules: List<String>,
    val files: Int,
    val code: Int,
)

/** How much of the production code is common Kotlin. */
data class Summary(
    val commonCode: Int,
    val productionCode: Int,
) {
    val commonPercent: Int get() = if (productionCode == 0) 0 else (commonCode * 100.0 / productionCode).roundToInt()
}

/**
 * The shared-code report for the README: cloc's per-file counts grouped by source set, so a reader sees how
 * much of the code is `commonMain`, with the share of production code that is common Kotlin under the table.
 */
object SharedCodeReport {
    const val START_MARKER: String = "<!-- shared-code-report:start -->"
    const val END_MARKER: String = "<!-- shared-code-report:end -->"
    private const val COMMON = "commonMain"
    private const val SWIFT = "Swift"
    private const val GRADLE = "Gradle scripts"
    private const val BUILD_LOGIC = "Build logic"
    private const val BUILD_LOGIC_MODULE = "build-logic"
    private const val ROOT = "root"
    private const val NO_SOURCE_SET = "other"

    /** Modules whose main code exists only for tests, counted with the tests whatever their source set is called. */
    private val TEST_SUPPORT_MODULES = setOf("core:fixtures")

    /** Reads `cloc --csv --by-file` output: one line per file, the header and the SUM line left out. */
    fun parseClocCsv(csv: String): List<CountedFile> =
        csv
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val fields = line.split(',')
                val code = fields.last().toIntOrNull() ?: return@mapNotNull null
                val language = fields.first()
                if (language == "language" || language == "SUM") return@mapNotNull null
                // The file name is everything between the language and the three counts.
                val path = fields.subList(1, fields.size - 3).joinToString(",")
                CountedFile(path, code)
            }.toList()

    /** The table's rows: one per source set and role, production first, the largest first within a role. */
    fun rows(files: List<CountedFile>): List<Row> =
        files
            .groupBy { file -> file.classify() }
            .entries
            .groupBy({ (place, _) -> place.sourceSet to place.role }, { (place, counted) -> place.module to counted })
            .map { (key, perModule) ->
                Row(
                    sourceSet = key.first,
                    role = key.second,
                    modules = perModule.map { (module, _) -> module }.distinct().sorted(),
                    files = perModule.sumOf { (_, counted) -> counted.size },
                    code = perModule.sumOf { (_, counted) -> counted.sumOf { it.code } },
                )
            }.sortedWith(compareBy<Row> { it.role }.thenByDescending { it.code }.thenBy { it.sourceSet })

    /** Common Kotlin over every production source set, the Swift shell included. */
    fun summary(rows: List<Row>): Summary {
        val production = rows.filter { it.role == Role.Production }
        return Summary(
            commonCode = production.filter { it.sourceSet == COMMON }.sumOf { it.code },
            productionCode = production.sumOf { it.code },
        )
    }

    /** The Markdown table with a total under the production rows and under the test rows, then the share. */
    fun render(files: List<CountedFile>): String {
        val rows = rows(files)
        val summary = summary(rows)
        val lines = mutableListOf("| Source set | Modules | Files | Lines of code |", "| --- | --- | ---: | ---: |")
        fun total(
            label: String,
            role: Role,
        ) {
            val of = rows.filter { it.role == role }
            lines += "| **$label** | | ${of.sumOf { it.files }} | ${of.sumOf { it.code }.grouped()} |"
        }
        for (role in Role.entries) {
            rows.filter { it.role == role }.forEach { row ->
                lines += "| `${row.sourceSet}` | ${row.modules.joinToString(", ")} | ${row.files} | ${row.code.grouped()} |"
            }
            when (role) {
                Role.Production -> total("Production", role)
                Role.Test -> total("Tests", role)
                Role.Build -> Unit
            }
        }
        lines += ""
        lines +=
            "Common Kotlin (`$COMMON`) is ${summary.commonPercent}% of the production code: " +
            "${summary.commonCode.grouped()} of ${summary.productionCode.grouped()} lines."
        return lines.joinToString("\n", postfix = "\n")
    }

    /** The README with the block between the markers replaced by [table]; fails when the markers are missing. */
    fun updateReadme(
        readme: String,
        table: String,
    ): String {
        val start = readme.indexOf(START_MARKER)
        val end = readme.indexOf(END_MARKER)
        if (start < 0 || end <= start) error("README has no $START_MARKER / $END_MARKER block to write the table into")
        return readme.substring(0, start + START_MARKER.length) + "\n" + table + readme.substring(end)
    }

    /** Where one file belongs in the table. */
    private data class Place(
        val module: String,
        val sourceSet: String,
        val role: Role,
    )

    /**
     * Reads a file's place from its path once. A module is what comes before `src`, a Gradle script's is its
     * directory, and anything else (the Swift shell) is its first directory. Every file of build-logic is build
     * code, as is every Gradle script; a test-support module's code counts with the tests; otherwise the source
     * set decides, `commonTest` and the apps' `test` being the test sets.
     */
    private fun CountedFile.classify(): Place {
        val segments = path.removePrefix("./").split('/')
        val src = segments.indexOf("src")
        val isScript = path.endsWith(".kts")
        val module =
            when {
                src > 0 -> segments.subList(0, src).joinToString(":")
                isScript && segments.size > 1 -> segments.dropLast(1).joinToString(":")
                isScript -> ROOT
                segments.size > 1 -> segments.first()
                else -> ROOT
            }
        val sourceSet =
            when {
                module == BUILD_LOGIC_MODULE -> BUILD_LOGIC
                isScript -> GRADLE
                path.endsWith(".swift") -> SWIFT
                src in 0 until segments.lastIndex -> segments[src + 1]
                else -> NO_SOURCE_SET
            }
        val role =
            when {
                module == BUILD_LOGIC_MODULE || isScript -> Role.Build
                module in TEST_SUPPORT_MODULES -> Role.Test
                sourceSet.endsWith("Test") || sourceSet == "test" -> Role.Test
                else -> Role.Production
            }
        return Place(module, sourceSet, role)
    }

    private fun Int.grouped(): String = String.format(Locale.ROOT, "%,d", this)
}
