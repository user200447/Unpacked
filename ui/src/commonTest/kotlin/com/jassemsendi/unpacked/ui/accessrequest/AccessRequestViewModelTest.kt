package com.jassemsendi.unpacked.ui.accessrequest

import com.jassemsendi.unpacked.insights.EvidenceRules
import com.jassemsendi.unpacked.insights.GapReportReader
import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.EvidenceRule
import com.jassemsendi.unpacked.model.Extent
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.ui.home.FakeClaimRepository
import com.jassemsendi.unpacked.ui.home.FakeEventRepository
import com.jassemsendi.unpacked.ui.home.advertiser
import com.jassemsendi.unpacked.ui.home.event
import com.jassemsendi.unpacked.ui.home.provenanceOf
import com.jassemsendi.unpacked.ui.imports.FakeImportRepository
import com.jassemsendi.unpacked.ui.imports.storedImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Seam 5: the Access request screen's state transitions, driven through actions against fake repositories. */
class AccessRequestViewModelTest {
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
    private val adsViewed = FileResult.Parsed("ads_information/ads_viewed.json", RawKind("ads_viewed"), 1, 1, 0)

    private val events = FakeEventRepository(adShown)
    private val claims = FakeClaimRepository(holdsContact)
    private val imports = FakeImportRepository(storedImport(1, "instagram-me-2026-09-01", files = listOf(adsViewed)))
    private val exporter = FakeLetterExporter()

    /** Categories is Covered by the JSON files, Recipients only Partial through the advertiser, the rest Missing. */
    private val someRules =
        EvidenceRules {
            listOf(
                EvidenceRule.FileNamed(ArticleItem.Categories, "*.json"),
                EvidenceRule.ClaimsOf(ArticleItem.Recipients, ClaimKind.AdvertiserHoldsContact, Extent.Partial),
            )
        }

    private fun viewModel(
        rules: EvidenceRules = someRules,
        exporter: LetterExporter = this.exporter,
    ): AccessRequestViewModel = AccessRequestViewModel(GapReportReader(imports, events, claims, rules), exporter)

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun nothingIsReadUntilTheScreenNamesAnImportAndThePlatformSaysWhetherItCanExport() =
        runTest {
            assertEquals(AccessRequestState(canExport = true), viewModel().state.value)
            assertEquals(AccessRequestState(canExport = false), viewModel(exporter = NoLetterExporter).state.value)
        }

    @Test
    fun loadingDraftsTheLetterOverTheImportAskingForItsMissingAndPartialItemsByArticleLetter() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(AccessRequestAction.Load(ImportId(1)))

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertNull(state.error)
            val letter = assertNotNull(state.letter)
            assertEquals("instagram-me-2026-09-01", letter.exportName)
            assertEquals("Instagram", letter.sourceName)
            assertEquals(
                "Asks for 8 of the nine items: " +
                    "15(1)(a), 15(1)(c), 15(1)(d), 15(1)(e), 15(1)(f), 15(1)(g), 15(1)(h), 15(3).",
                letter.summary,
            )
            assertEquals("access-request-instagram-me-2026-09-01.txt", letter.fileName)
            assertTrue(letter.text.contains("15(1)(c)"))
            assertFalse(letter.text.contains("15(1)(b)"), "Categories is Covered and not asked for")
        }

    @Test
    fun anImportWithEveryItemCoveredDraftsTheShortLetterSayingNothingIsOutstanding() =
        runTest {
            val everything = EvidenceRules { ArticleItem.entries.map { EvidenceRule.FileNamed(it, "*.json") } }
            val viewModel = viewModel(rules = everything)

            viewModel.onAction(AccessRequestAction.Load(ImportId(1)))

            val letter = assertNotNull(viewModel.state.value.letter)
            assertEquals("Nothing outstanding: the Export addresses all nine items.", letter.summary)
            ArticleItem.entries.forEach { assertFalse(letter.text.contains(it.article)) }
        }

    @Test
    fun exportingHandsTheLetterToThePlatformUnderItsFileNameAndSaysWhereItWent() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(AccessRequestAction.Load(ImportId(1)))
            val letter = assertNotNull(viewModel.state.value.letter)

            viewModel.onAction(AccessRequestAction.Export)

            assertEquals(listOf(letter.fileName to letter.text), exporter.exported)
            assertEquals("/Users/me/Documents/access-request.txt", viewModel.state.value.exportedTo)
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun aCancelledExportLeavesNothingToSayAndAFailedOneIsReported() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(AccessRequestAction.Load(ImportId(1)))

            exporter.destination = null
            viewModel.onAction(AccessRequestAction.Export)

            assertNull(viewModel.state.value.exportedTo)
            assertNull(viewModel.state.value.error)

            exporter.failure = LetterExportError.NOT_WRITABLE
            viewModel.onAction(AccessRequestAction.Export)

            assertEquals("The letter could not be written there.", viewModel.state.value.error)
            assertNotNull(viewModel.state.value.letter, "the letter stays on screen")
        }

    @Test
    fun aPlatformWithoutAnExporterIsNeverAskedToExport() =
        runTest {
            val viewModel = viewModel(exporter = NoLetterExporter)
            viewModel.onAction(AccessRequestAction.Load(ImportId(1)))

            viewModel.onAction(AccessRequestAction.Export)

            assertNull(viewModel.state.value.exportedTo)
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun anImportThatIsGoneIsReportedWithNoLetter() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(AccessRequestAction.Load(ImportId(99)))

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertNull(state.letter)
            assertEquals("That is no longer in your library.", state.error)
        }
}
