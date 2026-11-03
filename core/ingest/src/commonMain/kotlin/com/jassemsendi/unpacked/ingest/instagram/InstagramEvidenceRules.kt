package com.jassemsendi.unpacked.ingest.instagram

import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.EvidenceRule

/**
 * What in an Instagram Export counts toward each Article item, and how far. Instagram's Export is a copy
 * of the data organised by category and says much about what was inferred and who holds the user's
 * contact details, but it states no purposes, retention period, rights or logic, so most of what it shows
 * establishes Partial at best, and three items have nothing to count at all.
 */
internal object InstagramEvidenceRules {
    val all: List<EvidenceRule> =
        listOf(
            // 15(1)(a): the consents given and the settings over activity off the platform say what the Source
            // may do with some of the data; nothing states the purposes of the processing.
            EvidenceRule.ClaimsOf(ArticleItem.Purposes, ClaimKind.ConsentGiven, Coverage.Partial),
            EvidenceRule.ClaimsOf(ArticleItem.Purposes, ClaimKind.OffPlatformSetting, Coverage.Partial),
            // 15(1)(b): the Export is organised by category; every JSON file in it is one.
            EvidenceRule.FileNamed(ArticleItem.Categories, "*.json", Coverage.Covered),
            // 15(1)(c): advertisers holding the user's contact details are named; processors and other recipients are not.
            EvidenceRule.ClaimsOf(ArticleItem.Recipients, ClaimKind.AdvertiserHoldsContact, Coverage.Partial),
            // 15(1)(d), (e) and (f): nothing in the Export speaks to retention, to the user's rights or to complaints.
            // 15(1)(g): contacts synced from the user's device are marked as such; nothing says where the rest came from.
            EvidenceRule.ClaimsOf(ArticleItem.SourceOfData, ClaimKind.ContactSynced, Coverage.Partial),
            // 15(1)(h): the inferences and audience categories are shown; the logic behind them is not.
            EvidenceRule.ClaimsOf(ArticleItem.AutomatedDecisions, ClaimKind.InferredInterest, Coverage.Partial),
            EvidenceRule.ClaimsOf(ArticleItem.AutomatedDecisions, ClaimKind.InferredLocation, Coverage.Partial),
            EvidenceRule.ClaimsOf(ArticleItem.AutomatedDecisions, ClaimKind.ReachCategory, Coverage.Partial),
            // 15(3): the Export's JSON files are the copy of the data.
            EvidenceRule.FileNamed(ArticleItem.CopyOfData, "*.json", Coverage.Covered),
        )
}
