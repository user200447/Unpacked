package com.jassemsendi.unpacked.ingest.instagram

import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.EvidenceRule
import com.jassemsendi.unpacked.model.Extent

/**
 * What in an Instagram Export counts toward each Article item, and how far. Instagram's Export is a copy
 * of the data organised by category and says much about what was inferred and who holds the user's
 * contact details, but it states no purposes, retention period, rights, origins or logic, so most of what it
 * shows establishes Partial at best, and four items have nothing to count at all.
 */
internal object InstagramEvidenceRules {
    val all: List<EvidenceRule> =
        listOf(
            // 15(1)(a): the consents given and the settings over activity off the platform say what the Source
            // may do with some of the data; nothing states the purposes of the processing.
            EvidenceRule.ClaimsOf(ArticleItem.Purposes, ClaimKind.ConsentGiven, Extent.Partial),
            EvidenceRule.ClaimsOf(ArticleItem.Purposes, ClaimKind.OffPlatformSetting, Extent.Partial),
            // 15(1)(b): the Export is organised by category; every JSON file in it is one.
            EvidenceRule.FileNamed(ArticleItem.Categories, "*.json", Extent.Covered),
            // 15(1)(c): advertisers holding the user's contact details are named; processors and other recipients are not.
            EvidenceRule.ClaimsOf(ArticleItem.Recipients, ClaimKind.AdvertiserHoldsContact, Extent.Partial),
            // 15(1)(d), (e) and (f): nothing in the Export speaks to retention, to the user's rights or to complaints.
            // 15(1)(g): nothing says where data the user did not give came from; synced contacts came from the user.
            // 15(1)(h): the inferences and audience categories are shown; the logic behind them is not.
            EvidenceRule.ClaimsOf(ArticleItem.AutomatedDecisions, ClaimKind.InferredInterest, Extent.Partial),
            EvidenceRule.ClaimsOf(ArticleItem.AutomatedDecisions, ClaimKind.InferredLocation, Extent.Partial),
            EvidenceRule.ClaimsOf(ArticleItem.AutomatedDecisions, ClaimKind.ReachCategory, Extent.Partial),
            // 15(3): the Export's JSON files are the copy of the data.
            EvidenceRule.FileNamed(ArticleItem.CopyOfData, "*.json", Extent.Covered),
        )
}
