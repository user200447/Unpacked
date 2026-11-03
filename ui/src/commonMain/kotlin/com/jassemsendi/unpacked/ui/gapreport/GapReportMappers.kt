package com.jassemsendi.unpacked.ui.gapreport

import com.jassemsendi.unpacked.insights.Evidence
import com.jassemsendi.unpacked.insights.GapReport
import com.jassemsendi.unpacked.insights.Verdict
import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.EvidenceRule
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.counted
import com.jassemsendi.unpacked.ui.home.label
import com.jassemsendi.unpacked.ui.home.text
import com.jassemsendi.unpacked.ui.imports.toUi
import com.jassemsendi.unpacked.ui.time.toDeviceMinute
import kotlinx.datetime.TimeZone

internal fun Import.toChoice(timeZone: TimeZone): ImportChoiceUi =
    ImportChoiceUi(
        id = id,
        exportName = summary.exportId.value,
        startedAt = startedAt.toDeviceMinute(timeZone),
        isComplete = completedAt != null,
    )

internal fun GapReport.toUi(): GapReportUi =
    GapReportUi(
        exportName = exportId.value,
        sourceName = source.name,
        summary = Coverage.entries.joinToString { "${count(it)} ${it.name.lowercase()}" },
        items = verdicts.map { it.toUi() },
    )

private fun Verdict.toUi(): ArticleItemUi =
    ArticleItemUi(
        item = item,
        article = item.article,
        title = item.title(),
        description = item.description(),
        coverage = coverage,
        coverageLabel = coverage.name,
        evidence = evidence.map { it.toUi() },
        emptyText = "Nothing in this Export counts toward this item.".takeIf { coverage == Coverage.Missing },
    )

/** Captioned with how far it counts, so the user sees why an item with evidence is still only Partial. */
private fun Evidence.toUi(): EvidenceUi =
    when (this) {
        is Evidence.Files -> {
            EvidenceUi.Files(
                caption = "Counts as ${rule.establishes.name}: ${counted(files.size, "file")} matching ${rule.pattern}",
                files = files.take(FILES_SHOWN).map { it.toUi() },
                moreFiles = (files.size - FILES_SHOWN).coerceAtLeast(0),
            )
        }

        is Evidence.Records -> {
            val (noun, kindLabel, refOf) =
                when (val rule = rule) {
                    is EvidenceRule.EventsOf -> Triple("Event", rule.kind.label(), RecordRef::Event)
                    is EvidenceRule.ClaimsOf -> Triple("Claim", rule.kind.label(), RecordRef::Claim)
                    is EvidenceRule.FileNamed -> error("a file rule finds files, not records")
                }
            EvidenceUi.Records(
                caption = "Counts as ${rule.establishes.name}: ${counted(count, noun)} of $kindLabel",
                cited = cited.map { CitedRecordUi(refOf(it), it.text()) },
            )
        }
    }

private fun ArticleItem.title(): String =
    when (this) {
        ArticleItem.Purposes -> "Purposes of the processing"
        ArticleItem.Categories -> "Categories of data"
        ArticleItem.Recipients -> "Recipients"
        ArticleItem.RetentionPeriod -> "Retention period"
        ArticleItem.RightsToRectifyAndErase -> "Rights to rectify and erase"
        ArticleItem.RightToComplain -> "Right to complain"
        ArticleItem.SourceOfData -> "Source of the data"
        ArticleItem.AutomatedDecisions -> "Automated decisions and profiling"
        ArticleItem.CopyOfData -> "A copy of the data"
    }

private fun ArticleItem.description(): String =
    when (this) {
        ArticleItem.Purposes -> {
            "Why the Source processes your data."
        }

        ArticleItem.Categories -> {
            "Which kinds of personal data the Source holds about you."
        }

        ArticleItem.Recipients -> {
            "Who your data has been or will be disclosed to."
        }

        ArticleItem.RetentionPeriod -> {
            "How long your data is kept, or how that period is decided."
        }

        ArticleItem.RightsToRectifyAndErase -> {
            "That you may have your data corrected, erased or restricted, and object to its processing."
        }

        ArticleItem.RightToComplain -> {
            "That you may complain to a supervisory authority."
        }

        ArticleItem.SourceOfData -> {
            "Where data the Source did not collect from you came from."
        }

        ArticleItem.AutomatedDecisions -> {
            "Whether you are profiled or subject to automated decisions, and the logic involved."
        }

        ArticleItem.CopyOfData -> {
            "A copy of the personal data being processed."
        }
    }

/** How many matching files a Verdict lists before saying how many more there are. */
private const val FILES_SHOWN = 5
