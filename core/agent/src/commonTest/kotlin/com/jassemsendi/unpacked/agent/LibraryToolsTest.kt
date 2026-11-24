package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.insights.Evidence
import com.jassemsendi.unpacked.insights.EvidenceRules
import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.EvidenceRule
import com.jassemsendi.unpacked.model.Extent
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Seam 2: each Tool against fake repositories, with every value worked out by hand from [TestLibrary]. */
class LibraryToolsTest {
    private val library = TestLibrary()

    /** Who holds the user's data is Covered by a contact list, Partial by ads alone: enough rules to see a Verdict cite. */
    private val rules =
        EvidenceRules {
            listOf(
                EvidenceRule.EventsOf(ArticleItem.Recipients, EventKind.AdShown),
                EvidenceRule.ClaimsOf(ArticleItem.Recipients, ClaimKind.AdvertiserHoldsContact, Extent.Covered),
            )
        }

    private val tools =
        with(library) {
            LibraryTools(imports, events, claims, entities, search, zone, rules)
        }

    private fun <E : ToolError> Result<*, ToolError>.errorAs(): E {
        val error = assertIs<Result.Error<ToolError>>(this).error
        @Suppress("UNCHECKED_CAST")
        return error as E
    }

    @Test
    fun listImportsDescribesEveryImportOldestFirstWithWhatItProduced() =
        runTest {
            val listed = tools.listImports().orFail()

            assertEquals(listOf(1L, 2L), listed.imports.map { it.id })
            assertEquals(
                ImportRef(
                    id = 1,
                    source = Source.Instagram,
                    exportId = library.instagramExport,
                    startedAt = library.startedAt,
                    completedAt = library.instagramImport.completedAt,
                    eventCount = 3,
                    claimCount = 0,
                    parsedFiles = 1,
                    skippedFiles = 1,
                    failedFiles = 1,
                ),
                listed.imports[0],
            )
            assertEquals(null, listed.imports[1].completedAt, "an Import still running is listed as such")
            assertEquals(emptyList(), listed.cited)
        }

    @Test
    fun countEventsNarrowsByKindRangeAndSourceAndCitesTheNewestCounted() =
        runTest {
            with(library) {
                val all = tools.countEvents().orFail()
                assertEquals(5, all.total)
                assertEquals(listOf(googleAd, login, courierAd).map { it.provenance }, all.cited)
                assertEquals(null, all.series)

                val ads = tools.countEvents(kind = "AdShown").orFail()
                assertEquals(4, ads.total)
                assertEquals(listOf(googleAd, courierAd, courierAdAtMidnight).map { it.provenance }, ads.cited)

                // Names are read as the model writes them, by enum name or Source id, in any case.
                assertEquals(3, tools.countEvents(kind = "adshown", source = "instagram").orFail().total)
                assertEquals(1, tools.countEvents(source = "GoogleTakeout").orFail().total)

                // A day starts at midnight in Tunis, so the ad at 23:30 UTC on the 26th is the 27th's.
                val from27 = tools.countEvents(kind = "AdShown", from = "2026-10-27").orFail()
                assertEquals(3, from27.total)
                assertEquals(DateRange(LocalDate(2026, 10, 27), null), from27.range)
                assertEquals(1, tools.countEvents(kind = "AdShown", to = "2026-10-26").orFail().total)
                assertEquals(
                    3,
                    tools.countEvents(kind = "AdShown", from = "2026-10-27", to = "2026-10-27").orFail().total,
                )
                assertEquals(
                    2,
                    tools
                        .countEvents(
                            kind = "AdShown",
                            from = "2026-10-27",
                            to = "2026-10-27",
                            source = "Instagram",
                        ).orFail()
                        .total,
                    "every part given narrows at once",
                )
            }
        }

    @Test
    fun countEventsSpreadsOverEveryMonthOrDayFromTheFirstWithAnEventToTheLast() =
        runTest {
            val byMonth = tools.countEvents(kind = "AdShown", groupBy = "month").orFail()
            assertEquals(4, byMonth.total)
            assertEquals(Grouping.Month, byMonth.grouping)
            assertEquals(
                listOf(SeriesPoint(LocalDate(2026, 9, 1), 1), SeriesPoint(LocalDate(2026, 10, 1), 3)),
                byMonth.series,
            )

            val byDay = tools.countEvents(kind = "AdShown", from = "2026-10-01", groupBy = "day").orFail()
            assertEquals(
                listOf(SeriesPoint(LocalDate(2026, 10, 27), 3)),
                byDay.series,
                "quiet days before the first are not shown",
            )

            val none = tools.countEvents(kind = "Logout", groupBy = "Day").orFail()
            assertEquals(0, none.total)
            assertEquals(emptyList(), none.series)
            assertEquals(emptyList(), none.cited)
        }

    @Test
    fun countEventsAnswersABadInputWithATypedErrorNamingWhatItAccepts() =
        runTest {
            assertEquals(
                ToolError.UnknownKind("Advertisement", EVENT_KIND_NAMES),
                tools.countEvents(kind = "Advertisement").errorAs(),
            )
            assertEquals(ToolError.InvalidDate("yesterday"), tools.countEvents(from = "yesterday").errorAs())
            assertEquals(
                ToolError.InvertedRange(LocalDate(2026, 10, 27), LocalDate(2026, 10, 26)),
                tools.countEvents(from = "2026-10-27", to = "2026-10-26").errorAs(),
            )
            assertEquals(
                ToolError.UnknownSource("TikTok", SOURCE_NAMES),
                tools.countEvents(source = "TikTok").errorAs(),
            )
            assertEquals(
                ToolError.UnknownGrouping("week", GROUPING_NAMES),
                tools.countEvents(groupBy = "week").errorAs(),
            )

            library.events.failure = DataError.Local.DISK_FULL
            assertEquals(ToolError.Library(DataError.Local.DISK_FULL), tools.countEvents().errorAs())
        }

    @Test
    fun topEntitiesRanksMostNamedFirstTiesByNameAndCitesEachByItsOwnEvents() =
        runTest {
            with(library) {
                val top = tools.topEntities("AdShown", "Advertiser").orFail()

                assertEquals(3, top.count)
                assertEquals(
                    listOf(
                        RankedEntity(
                            EntityRef.of(courier),
                            2,
                            listOf(courierAd, courierAdAtMidnight).map { it.provenance },
                        ),
                        RankedEntity(EntityRef.of(googleCourier), 1, listOf(googleAd.provenance)),
                        RankedEntity(EntityRef.of(hotels), 1, listOf(hotelsAd.provenance)),
                    ),
                    top.top,
                )
                assertEquals(top.top.flatMap { it.cited }, top.cited)

                val one = tools.topEntities("AdShown", "Advertiser", count = 1).orFail()
                assertEquals(listOf(courier.id), one.top.map { it.entity.id })
                assertEquals(3, one.count, "how many there are in all does not shrink with the page")

                val sinceThe27th = tools.topEntities("AdShown", "advertiser", from = "2026-10-27").orFail()
                assertEquals(
                    listOf(courier.id to 2, googleCourier.id to 1),
                    sinceThe27th.top.map {
                        it.entity.id to
                            it.count
                    },
                )
                assertEquals(2, sinceThe27th.count)

                assertEquals(
                    TopEntities(EventKind.Login, EntityKind.Advertiser, DateRange(null, null), 0, emptyList()),
                    tools.topEntities("Login", "Advertiser").orFail(),
                )
            }
        }

    @Test
    fun topEntitiesRefusesAnUnknownKindOrACountOutsideItsRange() =
        runTest {
            assertEquals(ToolError.UnknownKind("Ad"), tools.topEntities("Ad", "Advertiser").errorAs())
            assertEquals(ToolError.UnknownEntityKind("Brand"), tools.topEntities("AdShown", "Brand").errorAs())
            assertEquals(
                ToolError.CountOutOfRange(0, LibraryTools.TOP_RANGE),
                tools.topEntities("AdShown", "Advertiser", count = 0).errorAs(),
            )
            assertEquals(
                ToolError.CountOutOfRange(-5, LibraryTools.TOP_RANGE),
                tools.topEntities("AdShown", "Advertiser", count = -5).errorAs(),
            )
            assertEquals(
                ToolError.CountOutOfRange(51, LibraryTools.TOP_RANGE),
                tools.topEntities("AdShown", "Advertiser", count = 51).errorAs(),
            )
        }

    @Test
    fun entityTimelineListsOneEntityNewestEventsAndEveryClaimNamingIt() =
        runTest {
            with(library) {
                val timeline = tools.entityTimeline(courier.id).orFail()

                assertEquals(EntityRef.of(courier), timeline.entity)
                assertEquals(2, timeline.eventCount)
                assertEquals(1, timeline.claimCount)
                assertEquals(listOf(RecordRef.of(courierAd), RecordRef.of(courierAdAtMidnight)), timeline.events)
                assertEquals(listOf(RecordRef.of(holdsContact)), timeline.claims)
                assertEquals(
                    listOf(courierAd, courierAdAtMidnight).map { it.provenance } + holdsContact.provenance,
                    timeline.cited,
                )

                val page = tools.entityTimeline(courier.id, limit = 1).orFail()
                assertEquals(listOf(RecordRef.of(courierAd)), page.events)
                assertEquals(2, page.eventCount)

                assertEquals(
                    ToolError.UnknownEntity("instagram:advertiser:nobody"),
                    tools.entityTimeline("instagram:advertiser:nobody").errorAs(),
                )
                assertEquals(
                    ToolError.CountOutOfRange(0, LibraryTools.PAGE_RANGE),
                    tools.entityTimeline(courier.id, limit = 0).errorAs(),
                )
                entities.failure = DataError.Local.UNKNOWN
                assertEquals(ToolError.Library(DataError.Local.UNKNOWN), tools.entityTimeline(courier.id).errorAs())
            }
        }

    @Test
    fun searchTextListsHitsNewestFirstUndatedClaimsLastAndRefusesBlankText() =
        runTest {
            with(library) {
                val found = tools.searchText("cour").orFail()

                assertEquals("cour", found.text)
                assertEquals(
                    listOf(
                        RecordRef.of(googleAd),
                        RecordRef.of(courierAd),
                        RecordRef.of(courierAdAtMidnight),
                        RecordRef.of(holdsContact),
                    ),
                    found.hits,
                )
                assertEquals(found.hits.map { it.provenance }, found.cited)

                assertEquals(
                    listOf(RecordRef.of(courierAdAtMidnight)),
                    tools.searchText("parcels overnight").orFail().hits,
                )
                assertEquals(
                    2,
                    tools
                        .searchText("  cour ", limit = 2)
                        .orFail()
                        .hits.size,
                )
                assertEquals(ToolError.BlankText, tools.searchText("   ").errorAs())
                assertEquals(
                    ToolError.CountOutOfRange(101, LibraryTools.PAGE_RANGE),
                    tools.searchText("cour", limit = 101).errorAs(),
                )
            }
        }

    @Test
    fun gapReportCitesTheRecordsItsVerdictsRestOnAndNamesAnUnknownImport() =
        runTest {
            with(library) {
                val reported = tools.gapReport(1).orFail()

                assertEquals(ArticleItem.entries, reported.report.verdicts.map { it.item })
                val recipients = reported.report.verdicts.single { it.item == ArticleItem.Recipients }
                assertEquals(Coverage.Covered, recipients.coverage)
                assertEquals(
                    listOf(
                        Evidence.Records(
                            rules.of(
                                Source.Instagram,
                            )[0] as EvidenceRule.KindRule,
                            3,
                            listOf(courierAd, courierAdAtMidnight, hotelsAd).map {
                                it.provenance
                            },
                        ),
                        Evidence.Records(
                            rules.of(Source.Instagram)[1] as EvidenceRule.KindRule,
                            1,
                            listOf(holdsContact.provenance),
                        ),
                    ),
                    recipients.evidence,
                )
                assertEquals(
                    listOf(courierAd, courierAdAtMidnight, hotelsAd).map { it.provenance } + holdsContact.provenance,
                    reported.cited,
                )
                assertEquals(8, reported.report.count(Coverage.Missing))

                assertEquals(ToolError.UnknownImport(99), tools.gapReport(99).errorAs())
            }
        }

    @Test
    fun gapReportOfASourceWithNoAdapterYetIsNineMissingVerdictsNotAFailure() =
        runTest {
            val declared = with(library) { LibraryTools(imports, events, claims, entities, search, zone) }

            val reported = declared.gapReport(2).orFail()

            assertEquals(Source.GoogleTakeout, reported.report.source)
            assertEquals(9, reported.report.count(Coverage.Missing))
            assertEquals(emptyList(), reported.cited)
        }
}
