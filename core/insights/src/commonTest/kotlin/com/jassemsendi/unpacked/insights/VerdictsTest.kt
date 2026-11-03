package com.jassemsendi.unpacked.insights

import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.EvidenceRule
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Verdict function alone: Evidence rules applied to what one Import holds, with nothing read from anywhere. */
class VerdictsTest {
    private val export = ExportId("instagram-me-2026-10-20")
    private val adsViewed =
        FileResult.Parsed("ads_information/ads_viewed.json", RawKind("ads_viewed"), 3, 3, 0)
    private val notes = FileResult.Skipped("personal_information/notes.txt", RawKind("notes"), "not a JSON file")
    private val likedPosts =
        FileResult.Failed("your_instagram_activity/Liked_Posts.json", RawKind("liked_posts"), "could not decode JSON")
    private val consents = FileResult.Parsed("preferences/settings/consents.json", RawKind("consents"), 1, 0, 1)

    private val firstAd = Provenance(export, adsViewed.path, 0)
    private val secondAd = Provenance(export, adsViewed.path, 1)
    private val firstAdvertiser = Provenance(export, "ads_information/advertisers.json", 0, itemIndex = 0)

    private val contents =
        ImportContents(
            files = listOf(adsViewed, notes, likedPosts, consents),
            events = mapOf(EventKind.AdShown to KindRecords(3, listOf(firstAd, secondAd))),
            claims =
                mapOf(
                    ClaimKind.AdvertiserHoldsContact to KindRecords(4, listOf(firstAdvertiser)),
                    ClaimKind.ConsentGiven to KindRecords(1, listOf(Provenance(export, consents.path, 0))),
                ),
        )

    @Test
    fun everyArticleItemGetsOneVerdictInArticleOrderFromTheFilesAndKindsThatMatchItsRules() {
        val anyJson = EvidenceRule.FileNamed(ArticleItem.Categories, "*.json")
        val advertisers = EvidenceRule.ClaimsOf(ArticleItem.Recipients, ClaimKind.AdvertiserHoldsContact)
        val ads = EvidenceRule.EventsOf(ArticleItem.CopyOfData, EventKind.AdShown, Coverage.Covered)
        val interests = EvidenceRule.ClaimsOf(ArticleItem.AutomatedDecisions, ClaimKind.InferredInterest)
        val logic = EvidenceRule.FileNamed(ArticleItem.AutomatedDecisions, "*/profiling_logic.json")

        val verdicts = verdictsOf(listOf(anyJson, advertisers, ads, interests, logic), contents)

        assertEquals(ArticleItem.entries, verdicts.map { it.item })
        val byItem = verdicts.associateBy { it.item }
        assertEquals(
            Verdict(
                ArticleItem.Categories,
                Coverage.Covered,
                listOf(Evidence.Files(anyJson, listOf(adsViewed, likedPosts, consents))),
            ),
            byItem.getValue(ArticleItem.Categories),
        )
        assertEquals(
            Verdict(
                ArticleItem.Recipients,
                Coverage.Partial,
                listOf(Evidence.Records(advertisers, 4, listOf(firstAdvertiser))),
            ),
            byItem.getValue(ArticleItem.Recipients),
        )
        assertEquals(
            Verdict(
                ArticleItem.CopyOfData,
                Coverage.Covered,
                listOf(Evidence.Records(ads, 3, listOf(firstAd, secondAd))),
            ),
            byItem.getValue(ArticleItem.CopyOfData),
        )
        // Rules whose Kind or file is absent find nothing, and an item nothing counts toward is Missing.
        assertEquals(
            Verdict(ArticleItem.AutomatedDecisions, Coverage.Missing, emptyList()),
            byItem.getValue(ArticleItem.AutomatedDecisions),
        )
        val untouched =
            listOf(
                ArticleItem.Purposes,
                ArticleItem.RetentionPeriod,
                ArticleItem.RightsToRectifyAndErase,
                ArticleItem.RightToComplain,
                ArticleItem.SourceOfData,
            )
        untouched.forEach { item -> assertEquals(Verdict(item, Coverage.Missing, emptyList()), byItem.getValue(item)) }
    }

    @Test
    fun aVerdictIsTheStrongestItsSatisfiedRulesEstablishInRuleOrder() {
        val partial = EvidenceRule.ClaimsOf(ArticleItem.Purposes, ClaimKind.ConsentGiven, Coverage.Partial)
        val covered = EvidenceRule.FileNamed(ArticleItem.Purposes, "*/CONSENTS.json", Coverage.Covered)
        val absent = EvidenceRule.FileNamed(ArticleItem.Purposes, "*/purposes.json", Coverage.Covered)

        val both = verdictsOf(listOf(partial, covered), contents).first { it.item == ArticleItem.Purposes }
        assertEquals(Coverage.Covered, both.coverage)
        assertEquals(listOf(partial, covered), both.evidence.map { it.rule })

        val partialOnly = verdictsOf(listOf(partial, absent), contents).first { it.item == ArticleItem.Purposes }
        assertEquals(Coverage.Partial, partialOnly.coverage)
        assertEquals(listOf(partial), partialOnly.evidence.map { it.rule })
    }

    @Test
    fun noRulesAtAllYieldNineMissingVerdicts() {
        val verdicts = verdictsOf(emptyList(), contents)

        assertEquals(ArticleItem.entries.map { Verdict(it, Coverage.Missing, emptyList()) }, verdicts)
    }
}
