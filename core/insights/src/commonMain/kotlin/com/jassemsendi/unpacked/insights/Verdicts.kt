package com.jassemsendi.unpacked.insights

import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.EvidenceRule
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Provenance

/**
 * What one Import holds that Evidence rules are applied to: its file inventory, and for every Kind with any
 * Events or Claims in it, how many and where the first few came from. Nothing more is read for a Gap report.
 */
public data class ImportContents(
    val files: List<FileResult>,
    val events: Map<EventKind, KindRecords>,
    val claims: Map<ClaimKind, KindRecords>,
)

/** How many Events or Claims of one Kind an Import holds, and the Provenance of the ones a Verdict cites. */
public data class KindRecords(
    val count: Int,
    val cited: List<Provenance>,
)

/** What one satisfied Evidence rule found in the Import: the files, or the records, that count toward its item. */
public sealed interface Evidence {
    public val rule: EvidenceRule

    /** The files of the inventory whose path matched the rule's pattern, in inventory order. */
    public data class Files(
        override val rule: EvidenceRule.FileNamed,
        val files: List<FileResult>,
    ) : Evidence

    /** How many Events or Claims of the rule's Kind the Import holds, and the Provenance of the cited ones. */
    public data class Records(
        override val rule: EvidenceRule,
        val count: Int,
        val cited: List<Provenance>,
    ) : Evidence
}

/**
 * The outcome of applying the Evidence rules for one Article item to one Import (glossary: Verdict): how far
 * the Import goes toward the item, with what was found, in rule order. Missing carries no evidence.
 */
public data class Verdict(
    val item: ArticleItem,
    val coverage: Coverage,
    val evidence: List<Evidence>,
)

/**
 * The Verdict function: one Verdict per Article item, in the article's order, from [rules] applied to
 * [contents]. Pure, so the same Import always yields the same nine Verdicts; a Source that declares no
 * rules yields nine Missing ones. Each item is the strongest Coverage its satisfied rules establish.
 */
public fun verdictsOf(
    rules: List<EvidenceRule>,
    contents: ImportContents,
): List<Verdict> =
    ArticleItem.entries.map { item ->
        val evidence = rules.filter { it.item == item }.mapNotNull { it.findIn(contents) }
        Verdict(item, evidence.minOfOrNull { it.rule.establishes } ?: Coverage.Missing, evidence)
    }

private fun EvidenceRule.findIn(contents: ImportContents): Evidence? =
    when (this) {
        is EvidenceRule.FileNamed -> {
            val regex = toRegex(pattern)
            contents.files
                .filter {
                    regex.matches(
                        it.path,
                    )
                }.takeIf { it.isNotEmpty() }
                ?.let { Evidence.Files(this, it) }
        }

        is EvidenceRule.EventsOf -> {
            contents.events[kind]?.let { Evidence.Records(this, it.count, it.cited) }
        }

        is EvidenceRule.ClaimsOf -> {
            contents.claims[kind]?.let { Evidence.Records(this, it.count, it.cited) }
        }
    }

/** A star stands for any run of characters; everything else is literal; case does not matter. */
private fun toRegex(pattern: String): Regex =
    Regex(pattern.split('*').joinToString(".*") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
