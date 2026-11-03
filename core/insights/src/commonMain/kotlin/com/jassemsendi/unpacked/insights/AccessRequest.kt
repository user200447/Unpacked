package com.jassemsendi.unpacked.insights

import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.Source

/**
 * The follow-up letter drafted from a Gap report (glossary: Access request): asks the Source for each Missing or
 * Partial Article item by its article letter. A deterministic template filled from the Verdicts with no model
 * involved (ADR 0006), so the same report always yields the same letter.
 */
public data class AccessRequest(
    public val source: Source,
    public val exportId: ExportId,
    /** The Missing and Partial items, in the article's order; empty when nothing is outstanding. */
    public val items: List<RequestedItem>,
    /** The letter, ready to copy or save. */
    public val text: String,
)

/** One Article item the letter asks for, and what its Verdict was: not provided, or provided only in part. */
public data class RequestedItem(
    public val item: ArticleItem,
    public val coverage: Coverage,
)

/** The Access request for this report. */
public fun GapReport.accessRequest(): AccessRequest {
    val items =
        verdicts
            .filter { it.coverage != Coverage.Covered }
            .map { RequestedItem(it.item, it.coverage) }
    return AccessRequest(source, exportId, items, letter(source, exportId, items))
}

private fun letter(
    source: Source,
    exportId: ExportId,
    items: List<RequestedItem>,
): String {
    val opening =
        "I received a copy of my personal data from ${source.name} under my right of access (the export named " +
            "${exportId.value})."
    val body =
        if (items.isEmpty()) {
            """
            |Subject: Access request under Article 15 GDPR: nothing outstanding
            |
            |To the data protection officer of ${source.name},
            |
            |$opening Checked against the nine things Article 15 says I must be given, the copy addresses every
            |one of them, so there is nothing outstanding to ask for.
            """.trimMargin()
        } else {
            """
            |Subject: Access request under Article 15 GDPR
            |
            |To the data protection officer of ${source.name},
            |
            |$opening Article 15 entitles me to more than the data itself. The copy I received leaves the
            |following out, or gives only part of it:
            |
            |${items.joinToString("\n") { it.line() }}
            |
            |Please provide each of these in writing. If any of them cannot be provided, please say which and why.
            """.trimMargin()
        }
    return body + "\n\n" + SIGN_OFF
}

private fun RequestedItem.line(): String {
    val state =
        when (coverage) {
            Coverage.Partial -> "provided only in part"
            Coverage.Missing -> "not provided"
            Coverage.Covered -> error("a Covered item is never asked for")
        }
    return "- Article ${item.article}: ${item.wording()} ($state)."
}

/** How the letter names each item: the article's own words, after the article letter. */
private fun ArticleItem.wording(): String =
    when (this) {
        ArticleItem.Purposes -> "the purposes of the processing"
        ArticleItem.Categories -> "the categories of personal data concerned"
        ArticleItem.Recipients -> "the recipients or categories of recipient my data has been or will be disclosed to"
        ArticleItem.RetentionPeriod -> "how long my data will be stored, or the criteria used to decide that period"
        ArticleItem.RightsToRectifyAndErase -> "my rights to rectification, erasure, restriction and objection"
        ArticleItem.RightToComplain -> "my right to lodge a complaint with a supervisory authority"
        ArticleItem.SourceOfData -> "where any data not collected from me came from"
        ArticleItem.AutomatedDecisions -> "any automated decision-making or profiling I am subject to, and its logic"
        ArticleItem.CopyOfData -> "a copy of the personal data undergoing processing"
    }

private const val SIGN_OFF = "Yours faithfully,\n\n[Your name]\n[The account or email address the data concerns]"
