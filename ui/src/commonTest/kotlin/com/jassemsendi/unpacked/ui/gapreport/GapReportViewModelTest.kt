package com.jassemsendi.unpacked.ui.gapreport

import app.cash.turbine.test
import com.jassemsendi.unpacked.insights.DeclaredEvidenceRules
import com.jassemsendi.unpacked.insights.EvidenceRules
import com.jassemsendi.unpacked.insights.GapReportReader
import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.EvidenceRule
import com.jassemsendi.unpacked.model.Extent
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.ui.home.FakeClaimRepository
import com.jassemsendi.unpacked.ui.home.FakeEventRepository
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.advertiser
import com.jassemsendi.unpacked.ui.home.event
import com.jassemsendi.unpacked.ui.home.provenanceOf
import com.jassemsendi.unpacked.ui.home.toDetail
import com.jassemsendi.unpacked.ui.imports.FakeImportRepository
import com.jassemsendi.unpacked.ui.imports.FileResultUi
import com.jassemsendi.unpacked.ui.imports.storedImport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Seam 5: the Gap report screen's state transitions, driven through actions against fake repositories. */
class GapReportViewModelTest {
    private val courier = advertiser("Example Courier")
    private val adShown = event(EventKind.AdShown, minutesAfterFirst = 0, entities = listOf(courier))
    private val holdsContact =
        Claim(
            kind = ClaimKind.AdvertiserHoldsContact,
            rawKind = RawKind("advertisers_using_your_activity_or_information"),
            provenance = provenanceOf("ads_information/advertisers.json", 0, itemIndex = 1),
            attributes = mapOf("Advertiser" to "Example Courier"),
            entities = listOf(courier),
        )
    private val consent =
        Claim(
            kind = ClaimKind.ConsentGiven,
            rawKind = RawKind("consents"),
            provenance = provenanceOf("preferences/settings/consents.json", 0),
            attributes = mapOf("Consented to the terms" to ""),
        )
    private val interest =
        Claim(
            kind = ClaimKind.InferredInterest,
            rawKind = RawKind("your_topics"),
            provenance = provenanceOf("preferences/your_topics/your_topics.json", 0),
            attributes = mapOf("Name" to "Sourdough"),
        )
    private val adsViewed = FileResult.Parsed("ads_information/ads_viewed.json", RawKind("ads_viewed"), 1, 1, 0)
    private val notes = FileResult.Skipped("personal_information/notes.txt", RawKind("notes"), "not a JSON file")

    private val events = FakeEventRepository(adShown)
    private val claims = FakeClaimRepository(holdsContact, consent, interest)
    private val older = storedImport(1, "instagram-me-2026-09-01", files = listOf(adsViewed, notes))
    private val unfinished = storedImport(2, "instagram-me-2026-10-20", files = listOf(adsViewed), complete = false)
    private val imports = FakeImportRepository(older, unfinished)

    private fun viewModel(
        timeZone: TimeZone = TimeZone.UTC,
        rules: EvidenceRules = DeclaredEvidenceRules,
    ): GapReportViewModel =
        GapReportViewModel(imports, events, claims, timeZone, GapReportReader(imports, events, claims, rules))

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun nothingIsReadUntilTheScreenAsksForARefresh() =
        runTest {
            assertEquals(GapReportState(), viewModel().state.value)
        }

    @Test
    fun refreshingReportsOnTheLatestCompleteImportWithOneVerdictPerArticleItem() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(GapReportAction.Refresh)

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertEquals(
                listOf(
                    ImportChoiceUi(ImportId(2), "instagram-me-2026-10-20", "2026-10-27 10:13", isComplete = false),
                    ImportChoiceUi(ImportId(1), "instagram-me-2026-09-01", "2026-10-27 10:12", isComplete = true),
                ),
                state.imports,
            )
            assertEquals(ImportId(1), state.selected, "the latest Import that finished, not the one that did not")
            val report = assertNotNull(state.report)
            assertEquals("instagram-me-2026-09-01", report.exportName)
            assertEquals("Instagram", report.sourceName)
            assertEquals("2 covered, 3 partial, 4 missing", report.summary)
            assertEquals(ArticleItem.entries, report.items.map { it.item })

            val recipients = report.items.first { it.item == ArticleItem.Recipients }
            assertEquals("15(1)(c)", recipients.article)
            assertEquals(Coverage.Partial, recipients.coverage)
            assertEquals("Partial", recipients.coverageLabel)
            assertNull(recipients.emptyText)
            assertEquals(
                listOf(
                    EvidenceUi.Records(
                        "Counts as Partial: 1 Claim of Advertiser holds contact",
                        listOf(
                            CitedRecordUi(
                                RecordRef.Claim(holdsContact.provenance),
                                "ads_information/advertisers.json, record 0, item 1",
                            ),
                        ),
                    ),
                ),
                recipients.evidence,
            )

            val categories = report.items.first { it.item == ArticleItem.Categories }
            assertEquals(Coverage.Covered, categories.coverage)
            assertEquals(
                listOf(
                    EvidenceUi.Files(
                        "Counts as Covered: 1 file matching *.json",
                        listOf(FileResultUi(adsViewed.path, "1 records: 1 Events, 0 Claims")),
                        moreFiles = 0,
                    ),
                ),
                categories.evidence,
            )

            val retention = report.items.first { it.item == ArticleItem.RetentionPeriod }
            assertEquals(Coverage.Missing, retention.coverage)
            assertEquals(emptyList(), retention.evidence)
            assertEquals("Nothing in this Export counts toward this item.", retention.emptyText)
        }

    @Test
    fun selectingAnotherImportClearsTheReportWhileItIsReadAndReportsOnIt() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(GapReportAction.Refresh)

            viewModel.state.test {
                val first = awaitItem()
                claims.gate = CompletableDeferred()
                viewModel.onAction(GapReportAction.Select(ImportId(2)))
                assertEquals(first.copy(isLoading = true, selected = ImportId(2), report = null), awaitItem())
                claims.gate?.complete(Unit)

                val second = awaitItem()
                assertFalse(second.isLoading)
                assertEquals(ImportId(2), second.selected)
                assertEquals("instagram-me-2026-10-20", assertNotNull(second.report).exportName)
                expectNoEvents()
            }
        }

    @Test
    fun anImportThatIsGoneFallsBackToTheLatestOneAndNoImportsAtAllIsNoReport() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(GapReportAction.Select(ImportId(99)))

            assertEquals(ImportId(1), viewModel.state.value.selected)

            imports.delete(ImportId(1))
            imports.delete(ImportId(2))
            viewModel.onAction(GapReportAction.Refresh)

            val empty = viewModel.state.value
            assertFalse(empty.isLoading)
            assertEquals(emptyList(), empty.imports)
            assertNull(empty.selected)
            assertNull(empty.report)
            assertNull(empty.error)
        }

    @Test
    fun openingACitedRecordShowsTheSameRecordDetailAsTheTimelineAndClosingHidesIt() =
        runTest {
            val viewModel = viewModel(timeZone = TimeZone.of("Asia/Tokyo"))
            viewModel.onAction(GapReportAction.Refresh)

            viewModel.onAction(GapReportAction.Open(RecordRef.Claim(holdsContact.provenance)))

            assertEquals(holdsContact.toDetail(TimeZone.of("Asia/Tokyo")), viewModel.state.value.detail)

            viewModel.onAction(GapReportAction.CloseDetail)

            assertNull(viewModel.state.value.detail)
        }

    @Test
    fun aRuleOverEventsCitesThemAsEventsThatOpenToTheirRecordDetail() =
        runTest {
            val adsAreTheCopy = EvidenceRule.EventsOf(ArticleItem.CopyOfData, EventKind.AdShown, Extent.Covered)
            val viewModel = viewModel(rules = EvidenceRules { listOf(adsAreTheCopy) })
            viewModel.onAction(GapReportAction.Refresh)

            val report = assertNotNull(viewModel.state.value.report)
            assertEquals("1 covered, 0 partial, 8 missing", report.summary)
            val cited = CitedRecordUi(RecordRef.Event(adShown.provenance), "ads_information/ads_viewed.json, record 0")
            assertEquals(
                listOf(EvidenceUi.Records("Counts as Covered: 1 Event of Ad shown", listOf(cited))),
                report.items.first { it.item == ArticleItem.CopyOfData }.evidence,
            )

            viewModel.onAction(GapReportAction.Open(cited.ref))

            assertEquals(adShown.toDetail(TimeZone.UTC), viewModel.state.value.detail)
        }

    @Test
    fun aLibraryThatCannotBeReadIsReportedAndTheLastReportStaysOnScreen() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(GapReportAction.Refresh)
            val before = viewModel.state.value
            claims.failure = DataError.Local.DISK_FULL

            viewModel.onAction(GapReportAction.Refresh)

            val failed = viewModel.state.value
            assertFalse(failed.isLoading)
            assertEquals(before.report, failed.report)
            assertEquals(before.imports, failed.imports)
            assertEquals("The device is out of storage.", failed.error)

            claims.failure = null
            viewModel.onAction(GapReportAction.Refresh)

            assertNull(viewModel.state.value.error)
            assertIs<GapReportUi>(viewModel.state.value.report)
        }
}
