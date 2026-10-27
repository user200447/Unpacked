package com.jassemsendi.unpacked.ui.imports

import app.cash.turbine.test
import com.jassemsendi.unpacked.ingest.ExportLocation
import com.jassemsendi.unpacked.ingest.ExportPart
import com.jassemsendi.unpacked.ingest.FileOutcome
import com.jassemsendi.unpacked.ingest.ImportProgress
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Inventory
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Source
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

/** Seam 5: the Import screen's state transitions, driven through actions against fakes. */
class ImportViewModelTest {
    private val parsed = FileResult.Parsed("ads_information/ads_viewed.json", RawKind("ads_viewed"), 4, 4, 0)
    private val skipped = FileResult.Skipped("personal_information/notes_0.txt", RawKind("notes_0"), "not a JSON file")
    private val failed = FileResult.Failed("activity/liked_posts.json", RawKind("liked_posts"), "could not decode JSON")
    private val earlier = storedImport(1, "instagram-me-2026-09-01", listOf(parsed))
    private val repository = FakeImportRepository(earlier)
    private val useCase = FakeImportUseCase(repository)
    private val picker = FakeExportPicker(zip = ExportLocation.Zip("/downloads/instagram-me-2026-10-20.zip"))

    private fun viewModel() = ImportViewModel(repository, useCase, picker, TimeZone.UTC)

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun startsIdleWithTheStoredImportsListedNewestFirstAndTheChoicesThePlatformOffers() =
        runTest {
            repository.add(storedImport(2, "instagram-me-2026-10-01", listOf(parsed, skipped), complete = false))

            val state = viewModel().state.value

            assertEquals(ImportRunUi.Idle, state.run)
            assertEquals(ImportState(canPickZip = true, canPickFolder = true), state.copy(imports = emptyList()))
            assertEquals(
                listOf(
                    ImportUi(
                        id = ImportId(2),
                        exportName = "instagram-me-2026-10-01",
                        sourceName = "Instagram",
                        startedAt = "2026-10-27 10:13",
                        isComplete = false,
                        eventCount = 4,
                        claimCount = 0,
                        parsed = listOf(FileResultUi(parsed.path, "4 records: 4 Events, 0 Claims")),
                        skipped = listOf(FileResultUi(skipped.path, "not a JSON file")),
                        failed = emptyList(),
                    ),
                    ImportUi(
                        id = ImportId(1),
                        exportName = "instagram-me-2026-09-01",
                        sourceName = "Instagram",
                        startedAt = "2026-10-27 10:12",
                        isComplete = true,
                        eventCount = 4,
                        claimCount = 0,
                        parsed = listOf(FileResultUi(parsed.path, "4 records: 4 Events, 0 Claims")),
                        skipped = emptyList(),
                        failed = emptyList(),
                    ),
                ),
                state.imports,
            )
        }

    @Test
    fun pickingAZipRunsTheImportShowingProgressFileByFileAndThenTheResultsWithReasons() =
        runTest {
            val viewModel = viewModel()
            val name = "instagram-me-2026-10-20"

            viewModel.onAction(ImportAction.PickZip)

            assertEquals(listOf(picker.zip), useCase.runs)
            assertEquals(ImportRunUi.Running(name, 0, 0, null, emptyList()), viewModel.state.value.run)
            useCase.steps.send(ImportStep.Started(earlier.summary.exportId, Source.Instagram, Inventory(2, 3, 1)))
            assertEquals(ImportRunUi.Running(name, 0, 3, null, emptyList()), viewModel.state.value.run)
            useCase.steps.send(ImportStep.FileStarted(parsed.path, ImportProgress(0, 3)))
            assertEquals(ImportRunUi.Running(name, 0, 3, parsed.path, emptyList()), viewModel.state.value.run)
            useCase.steps.send(ImportStep.FileFinished(FileOutcome(parsed), ImportProgress(0, 3)))
            useCase.steps.send(ImportStep.FileStarted(skipped.path, ImportProgress(1, 3)))
            useCase.steps.send(ImportStep.FileFinished(FileOutcome(skipped), ImportProgress(1, 3)))
            useCase.steps.send(ImportStep.FileStarted(failed.path, ImportProgress(2, 3)))
            assertEquals(
                ImportRunUi.Running(
                    name,
                    2,
                    3,
                    failed.path,
                    listOf(
                        FileResultUi(parsed.path, "4 records: 4 Events, 0 Claims"),
                        FileResultUi(skipped.path, "not a JSON file"),
                    ),
                ),
                viewModel.state.value.run,
            )
            useCase.steps.send(ImportStep.FileFinished(FileOutcome(failed), ImportProgress(2, 3)))
            val stored = storedImport(2, name, listOf(parsed, skipped, failed))
            useCase.steps.send(ImportStep.Stored(stored))
            useCase.steps.close()

            val finished = viewModel.state.value.run as ImportRunUi.Finished
            assertEquals(listOf(FileResultUi(parsed.path, "4 records: 4 Events, 0 Claims")), finished.import.parsed)
            assertEquals(listOf(FileResultUi(skipped.path, "not a JSON file")), finished.import.skipped)
            assertEquals(listOf(FileResultUi(failed.path, "could not decode JSON")), finished.import.failed)
            assertEquals(ImportId(2), finished.import.id)
            assertEquals(
                listOf(name, earlier.summary.exportId.value),
                viewModel.state.value.imports
                    .map { it.exportName },
            )
            assertEquals(null, viewModel.state.value.part)
        }

    @Test
    fun pickingAFolderRunsTheImportOfThatFolder() =
        runTest {
            picker.folder = ExportLocation.Folder("/exports/instagram-me-2026-10-20")

            viewModel().onAction(ImportAction.PickFolder)

            assertEquals(listOf(picker.folder), useCase.runs)
        }

    @Test
    fun cancellingThePickerChangesNothing() =
        runTest {
            picker.zip = null
            val viewModel = viewModel()
            val before = viewModel.state.value

            viewModel.onAction(ImportAction.PickZip)

            assertEquals(before, viewModel.state.value)
            assertEquals(emptyList(), useCase.runs)
        }

    @Test
    fun aMultiPartExportIsNoticedFromItsNameAndTheChosenPartIsStillImported() =
        runTest {
            picker.zip = ExportLocation.Zip("/downloads/instagram-me-2026-10-20-part-2-of-3.zip")
            val viewModel = viewModel()

            viewModel.onAction(ImportAction.PickZip)

            assertEquals(ExportPart(2, 3), viewModel.state.value.part)
            assertEquals(listOf(picker.zip), useCase.runs)
            assertEquals(
                "instagram-me-2026-10-20-part-2-of-3",
                (viewModel.state.value.run as ImportRunUi.Running).exportName,
            )
        }

    @Test
    fun anExportThatWillNotOpenLeavesTheRunFailedWithTheReason() =
        runTest {
            useCase.failure = IllegalStateException("not a zip file")
            val viewModel = viewModel()

            viewModel.onAction(ImportAction.PickZip)

            assertEquals(ImportRunUi.Failed("instagram-me-2026-10-20", "not a zip file"), viewModel.state.value.run)
            assertEquals(
                listOf(earlier.summary.exportId.value),
                viewModel.state.value.imports
                    .map { it.exportName },
            )
        }

    @Test
    fun aRunThatStopsHalfWayIsFailedAndThePartialImportStaysListed() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ImportAction.PickZip)
            useCase.steps.send(ImportStep.Started(earlier.summary.exportId, Source.Instagram, Inventory(2, 0, 0)))
            repository.add(storedImport(2, "instagram-me-2026-10-20", emptyList(), complete = false))

            useCase.steps.close(IllegalStateException("disk full"))

            assertEquals(ImportRunUi.Failed("instagram-me-2026-10-20", "disk full"), viewModel.state.value.run)
            assertEquals(
                listOf(false, true),
                viewModel.state.value.imports
                    .map { it.isComplete },
            )
        }

    @Test
    fun aSecondPickWhileARunIsInProgressIsIgnored() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ImportAction.PickZip)

            viewModel.onAction(ImportAction.PickZip)

            assertEquals(1, useCase.runs.size)
        }

    @Test
    fun deletingAnImportRemovesItFromTheListClearsItsResultsAndSaysSo() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ImportAction.PickZip)
            useCase.steps.send(ImportStep.Stored(storedImport(2, "instagram-me-2026-10-20", listOf(parsed))))
            useCase.steps.close()
            assertEquals(2, viewModel.state.value.imports.size)

            viewModel.events.test {
                viewModel.onAction(ImportAction.DeleteImport(ImportId(2)))

                assertEquals(ImportEvent.ImportDeleted("instagram-me-2026-10-20"), awaitItem())
            }
            assertEquals(
                listOf(ImportId(1)),
                viewModel.state.value.imports
                    .map { it.id },
            )
            assertEquals(ImportRunUi.Idle, viewModel.state.value.run)
            assertEquals(listOf(earlier), repository.all())
        }

    @Test
    fun deletingAnOlderImportKeepsTheCurrentResultsOnScreen() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ImportAction.PickZip)
            useCase.steps.send(ImportStep.Stored(storedImport(2, "instagram-me-2026-10-20", listOf(parsed))))
            useCase.steps.close()

            viewModel.onAction(ImportAction.DeleteImport(ImportId(1)))

            assertEquals(
                listOf(ImportId(2)),
                viewModel.state.value.imports
                    .map { it.id },
            )
            assertEquals(ImportId(2), (viewModel.state.value.run as ImportRunUi.Finished).import.id)
        }
}
