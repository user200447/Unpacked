package com.jassemsendi.unpacked.ui.explorer

import com.jassemsendi.unpacked.ingest.ExplorerError
import com.jassemsendi.unpacked.ingest.ExportError
import com.jassemsendi.unpacked.ingest.FileRecords
import com.jassemsendi.unpacked.ingest.Record
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.ui.home.AttributeUi
import com.jassemsendi.unpacked.ui.imports.FakeExportPicker
import com.jassemsendi.unpacked.ui.imports.FakeImportRepository
import com.jassemsendi.unpacked.ui.imports.ImportChoiceUi
import com.jassemsendi.unpacked.ui.imports.storedImport
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
import kotlin.test.assertTrue

/** Seam 5: the Explorer's state transitions, driven through actions against a fake library, Export and picker. */
class ExplorerViewModelTest {
    private val adsViewed = FileResult.Parsed("ads_information/ads_viewed.json", RawKind("ads_viewed"), 5, 5, 0)
    private val unmapped =
        FileResult.Skipped(
            "ads_information/subscription_for_no_ads.json",
            RawKind("subscription_for_no_ads"),
            "no mapper registered for subscription_for_no_ads.json",
        )
    private val notes = FileResult.Skipped("personal_information/notes_0.txt", RawKind("notes_0"), "not a JSON file")
    private val liked = FileResult.Failed("activity/liked_posts.json", RawKind("liked_posts"), "could not decode JSON")
    private val zip = ExportLocation.Zip("/Users/me/Downloads/instagram-me-2026-09-01.zip")
    private val older =
        storedImport(
            1,
            "instagram-me-2026-09-01",
            listOf(adsViewed, unmapped, notes, liked),
            mediaFiles = 3,
            location = zip,
        )
    private val unfinished = storedImport(2, "instagram-me-2026-10-20", listOf(adsViewed), complete = false)
    private val imports = FakeImportRepository(older, unfinished)

    private val subscription =
        Record(
            0,
            mapOf(
                "Timestamp" to "2026-10-08T07:48:39Z",
                "Subscription status" to "Not subscribed",
            ),
        )
    private val reader =
        FakeExportFileReader(
            mapOf(
                unmapped.path to FileRecords(listOf(subscription)),
                liked.path to FileRecords(listOf(Record(0, mapOf("title" to "A post"))), "could not decode JSON"),
                adsViewed.path to FileRecords(emptyList(), "expected a list of records"),
            ),
        )
    private val picker = FakeExportPicker(zip = ExportLocation.Zip("/Volumes/usb/instagram-me-2026-09-01.zip"))

    private fun viewModel(picker: FakeExportPicker = this.picker) =
        ExplorerViewModel(imports, reader, picker, TimeZone.UTC)

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun nothingIsReadUntilTheScreenAsksForARefresh() =
        runTest {
            assertEquals(ExplorerState(), viewModel().state.value)
        }

    @Test
    fun refreshingListsEveryFileOfTheLatestCompleteImportWithItsRawKindAndResultAndCountsTheMedia() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(ExplorerAction.Refresh)

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
            assertEquals(ImportId(1), state.selected)
            assertEquals(
                InventoryUi(
                    exportName = "instagram-me-2026-09-01",
                    sourceName = "Instagram",
                    location = "the zip at /Users/me/Downloads/instagram-me-2026-09-01.zip",
                    files =
                        listOf(
                            ExplorerFileUi(
                                adsViewed.path,
                                "ads_viewed",
                                "Parsed",
                                "5 records: 5 Events, 0 Claims",
                                canOpen = true,
                            ),
                            ExplorerFileUi(
                                unmapped.path,
                                "subscription_for_no_ads",
                                "Skipped",
                                "no mapper registered for subscription_for_no_ads.json",
                                canOpen = true,
                            ),
                            ExplorerFileUi(notes.path, "notes_0", "Skipped", "not a JSON file", canOpen = false),
                            ExplorerFileUi(
                                liked.path,
                                "liked_posts",
                                "Failed",
                                "could not decode JSON",
                                canOpen = true,
                            ),
                        ),
                    mediaFiles = 3,
                ),
                state.inventory,
            )
        }

    @Test
    fun openingAnUnrecognisedFileShowsItsRecordsAsLabelAndValuePairsReadFromTheRememberedExport() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ExplorerAction.Refresh)

            viewModel.onAction(ExplorerAction.Open(unmapped.path))

            val opened = assertNotNull(viewModel.state.value.opened)
            assertEquals(unmapped.path, opened.file.path)
            assertEquals("Skipped", opened.file.outcome)
            assertEquals(
                listOf(
                    RecordUi(
                        0,
                        listOf(
                            AttributeUi("Timestamp", "2026-10-08T07:48:39Z"),
                            AttributeUi("Subscription status", "Not subscribed"),
                        ),
                    ),
                ),
                opened.records,
            )
            assertNull(opened.note)
            assertEquals(listOf<ExportLocation>(zip), reader.readFrom)
            assertFalse(viewModel.state.value.isOpening)

            viewModel.onAction(ExplorerAction.Close)

            assertNull(viewModel.state.value.opened)
        }

    @Test
    fun openingAFailedFileShowsItsReasonAndWhateverRecordsWereReadable() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ExplorerAction.Refresh)

            viewModel.onAction(ExplorerAction.Open(liked.path))

            val readable = assertNotNull(viewModel.state.value.opened)
            assertEquals("Failed", readable.file.outcome)
            assertEquals("could not decode JSON", readable.file.detail)
            assertEquals(listOf(RecordUi(0, listOf(AttributeUi("title", "A post")))), readable.records)
            assertEquals(
                "Instagram could not read this file as it does during an Import (could not decode JSON), " +
                    "so its JSON is shown as written.",
                readable.note,
            )

            viewModel.onAction(ExplorerAction.Open(adsViewed.path))

            val unreadable = assertNotNull(viewModel.state.value.opened)
            assertEquals(emptyList<RecordUi>(), unreadable.records)
            assertEquals("Nothing in this file could be read as records: expected a list of records.", unreadable.note)
        }

    @Test
    fun anExportThatHasMovedIsSaidSoAndPickingItAgainRemembersTheNewPlaceAndOpensTheFile() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ExplorerAction.Refresh)
            reader.gone = ExplorerError.ExportGone(ExportError.NOT_FOUND)

            viewModel.onAction(ExplorerAction.Open(unmapped.path))

            assertEquals(
                ExportGoneUi(
                    "The Export is no longer at /Users/me/Downloads/instagram-me-2026-09-01.zip. " +
                        "Pick it again to open its files.",
                    canPickAgain = true,
                ),
                viewModel.state.value.exportGone,
            )
            assertNull(viewModel.state.value.opened)
            assertNull(viewModel.state.value.error)

            reader.gone = null
            viewModel.onAction(ExplorerAction.PickExportAgain)

            val state = viewModel.state.value
            assertNull(state.exportGone)
            assertEquals(picker.zip, assertIs<Result.Success<Import>>(imports.get(ImportId(1))).data.location)
            assertEquals("the zip at /Volumes/usb/instagram-me-2026-09-01.zip", assertNotNull(state.inventory).location)
            assertEquals(
                unmapped.path,
                assertNotNull(state.opened).file.path,
                "the file asked for opens from the new place",
            )
            assertEquals(listOf(zip, picker.zip!!), reader.readFrom)
        }

    @Test
    fun pickingADifferentExportIsRefusedAndACancelledPickChangesNothing() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ExplorerAction.Refresh)
            reader.gone = ExplorerError.ExportGone(ExportError.NOT_READABLE)
            viewModel.onAction(ExplorerAction.Open(unmapped.path))
            val gone = assertNotNull(viewModel.state.value.exportGone)
            assertTrue(
                gone.message.startsWith(
                    "What is at /Users/me/Downloads/instagram-me-2026-09-01.zip is no longer an Export",
                ),
            )

            picker.zip = ExportLocation.Zip("/Volumes/usb/instagram-someone-else.zip")
            viewModel.onAction(ExplorerAction.PickExportAgain)

            assertEquals(
                "That is instagram-someone-else, not instagram-me-2026-09-01: " +
                    "pick the Export this Import was read from.",
                viewModel.state.value.error,
            )
            assertEquals(gone, viewModel.state.value.exportGone)
            assertEquals(zip, assertIs<Result.Success<Import>>(imports.get(ImportId(1))).data.location)

            picker.zip = null
            viewModel.onAction(ExplorerAction.PickExportAgain)

            assertEquals(gone, viewModel.state.value.exportGone)
        }

    @Test
    fun aPlatformWithoutAChooserForThatKindOfLocationCannotOfferToPickAgain() =
        runTest {
            val viewModel = viewModel(picker = FakeExportPicker(canPickZip = false, canPickFolder = true))
            viewModel.onAction(ExplorerAction.Refresh)
            reader.gone = ExplorerError.ExportGone(ExportError.NOT_FOUND)

            viewModel.onAction(ExplorerAction.Open(unmapped.path))

            assertEquals(false, assertNotNull(viewModel.state.value.exportGone).canPickAgain)
        }

    @Test
    fun aFileNoLongerInTheExportIsReportedAndSelectingAnotherImportClosesWhatWasOpen() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ExplorerAction.Refresh)

            viewModel.onAction(ExplorerAction.Open("ads_information/removed_since.json"))

            assertEquals("The Export no longer holds ads_information/removed_since.json.", viewModel.state.value.error)
            assertNull(viewModel.state.value.opened)

            viewModel.onAction(ExplorerAction.Open(unmapped.path))
            assertNotNull(viewModel.state.value.opened)
            assertNull(viewModel.state.value.error, "the next successful read clears the error")

            viewModel.onAction(ExplorerAction.Select(ImportId(2)))

            val state = viewModel.state.value
            assertEquals(ImportId(2), state.selected)
            assertNull(state.opened)
            assertEquals("instagram-me-2026-10-20", assertNotNull(state.inventory).exportName)
        }

    @Test
    fun aLibraryThatCannotBeReadIsReportedAndTheLastInventoryStaysOnScreen() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ExplorerAction.Refresh)
            val before = viewModel.state.value
            imports.failure = DataError.Local.UNKNOWN

            viewModel.onAction(ExplorerAction.Refresh)

            val failed = viewModel.state.value
            assertFalse(failed.isLoading)
            assertEquals(before.inventory, failed.inventory)
            assertEquals("Your library could not be read or written.", failed.error)
        }
}
