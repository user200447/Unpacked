package com.jassemsendi.unpacked.insights

import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.EvidenceRule
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Access request template: a deterministic letter filled from the Verdicts, asking for every Missing or Partial
 * Article item by its article letter. The assertions hold on to the letters and the items' wording, not the full
 * text, so the wording of the letter can change without touching them.
 */
class AccessRequestTest {
    private val export = ExportId("instagram-me-2026-10-20")
    private val anyJson = EvidenceRule.FileNamed(ArticleItem.Categories, "*.json")

    private fun report(vararg coverages: Pair<ArticleItem, Coverage>): GapReport {
        val byItem = coverages.toMap()
        val verdicts =
            ArticleItem.entries.map { item ->
                when (byItem[item] ?: Coverage.Missing) {
                    Coverage.Covered -> Verdict(item, Coverage.Covered, listOf(fileEvidence(item)))
                    Coverage.Partial -> Verdict(item, Coverage.Partial, listOf(fileEvidence(item)))
                    Coverage.Missing -> Verdict(item, Coverage.Missing, emptyList())
                }
            }
        return GapReport(ImportId(1), export, Source.Instagram, verdicts)
    }

    private fun fileEvidence(item: ArticleItem): Evidence =
        Evidence.Files(
            anyJson.copy(item = item),
            listOf(FileResult.Parsed("ads_information/ads_viewed.json", RawKind("ads_viewed"), 1, 1, 0)),
        )

    @Test
    fun theLetterAsksForExactlyTheMissingAndPartialItemsByArticleLetterInArticleOrder() {
        val report =
            report(
                ArticleItem.Purposes to Coverage.Partial,
                ArticleItem.Categories to Coverage.Covered,
                ArticleItem.Recipients to Coverage.Partial,
                ArticleItem.RetentionPeriod to Coverage.Missing,
                ArticleItem.RightsToRectifyAndErase to Coverage.Covered,
                ArticleItem.RightToComplain to Coverage.Missing,
                ArticleItem.SourceOfData to Coverage.Missing,
                ArticleItem.AutomatedDecisions to Coverage.Partial,
                ArticleItem.CopyOfData to Coverage.Covered,
            )

        val letter = report.accessRequest()

        assertEquals(Source.Instagram, letter.source)
        assertEquals(export, letter.exportId)
        assertEquals(
            listOf(
                ArticleItem.Purposes to Coverage.Partial,
                ArticleItem.Recipients to Coverage.Partial,
                ArticleItem.RetentionPeriod to Coverage.Missing,
                ArticleItem.RightToComplain to Coverage.Missing,
                ArticleItem.SourceOfData to Coverage.Missing,
                ArticleItem.AutomatedDecisions to Coverage.Partial,
            ),
            letter.items.map { it.item to it.coverage },
        )
        assertTrue(letter.isOutstanding)

        // Each requested item appears once, by its letter and its wording, in the article's order.
        var position = 0
        letter.items.forEach { requested ->
            val article = letter.text.indexOf(requested.item.article, position)
            assertTrue(article >= position, "${requested.item.article} comes after the item before it")
            val wording = letter.text.indexOf(requested.wording, article)
            assertTrue(wording > article, "${requested.item.article} is followed by ${requested.wording}")
            assertEquals(-1, letter.text.indexOf(requested.item.article, article + 1), "named once")
            position = wording
        }
        listOf(ArticleItem.Categories, ArticleItem.RightsToRectifyAndErase, ArticleItem.CopyOfData).forEach {
            assertFalse(letter.text.contains(it.article), "${it.article} is Covered and not asked for")
        }
        assertTrue(letter.text.contains(Source.Instagram.name))
        assertTrue(letter.text.contains(export.value))
    }

    @Test
    fun aReportWithEveryItemCoveredIsAShortLetterSayingNothingIsOutstanding() {
        val allCovered = report(*ArticleItem.entries.map { it to Coverage.Covered }.toTypedArray())

        val letter = allCovered.accessRequest()

        assertEquals(emptyList(), letter.items)
        assertFalse(letter.isOutstanding)
        ArticleItem.entries.forEach { assertFalse(letter.text.contains(it.article), "${it.article} is not asked for") }
        assertTrue(letter.text.contains(Source.Instagram.name))
        assertTrue(letter.text.contains(export.value))
        val fullRequest = report().accessRequest()
        assertTrue(letter.text.lines().size < fullRequest.text.lines().size, "shorter than a full request")
    }

    @Test
    fun theSameReportAlwaysYieldsTheSameLetter() {
        val report = report(ArticleItem.Categories to Coverage.Covered, ArticleItem.Purposes to Coverage.Partial)

        assertEquals(report.accessRequest(), report.accessRequest())
    }
}
