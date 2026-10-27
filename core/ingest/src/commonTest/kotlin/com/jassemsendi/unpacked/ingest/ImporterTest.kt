package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.fixtures.InstagramFixtures
import com.jassemsendi.unpacked.fixtures.SyntheticInstagramExport
import com.jassemsendi.unpacked.fixtures.writeText
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Inventory
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class ImporterTest {
    private val fs = FakeFileSystem()
    private val root = "/exports/instagram-synthetic".toPath()

    private suspend fun import(export: Export): List<ImportStep> = Importer(InstagramAdapter).run(export).toList()

    private fun List<ImportStep>.finished(): List<ImportStep.FileFinished> = filterIsInstance<ImportStep.FileFinished>()

    private fun List<ImportStep>.summary() = assertIs<ImportStep.Completed>(last()).summary

    @Test
    fun adsViewedBecomeAdShownEventsWithProvenance() =
        runTest {
            val generator = SyntheticInstagramExport(adsViewed = 4)
            generator.writeTo(fs, root)

            val steps = import(Export.directory(fs, root))
            val events = steps.finished().flatMap { it.outcome.output.events }

            assertEquals(4, events.size)
            events.forEachIndexed { index, event ->
                val ad = generator.ads[index]
                assertEquals(EventKind.AdShown, event.kind)
                assertEquals(RawKind("ads_viewed"), event.rawKind)
                assertEquals(
                    Provenance(ExportId("instagram-synthetic"), InstagramFixtures.ADS_VIEWED_PATH, index),
                    event.provenance,
                )
                assertEquals(Instant.fromEpochSeconds(ad.timestampSeconds), event.time)
                val advertiser = event.entities.single()
                assertEquals(EntityKind.Advertiser, advertiser.kind)
                assertEquals(ad.name, advertiser.displayName)
                assertEquals("instagram:advertiser:${ad.username}", advertiser.id)
                assertEquals(ad.caption, event.attributes["Caption"])
            }
            val parsed = steps.summary().parsed.single()
            assertEquals(FileResult.Parsed(InstagramFixtures.ADS_VIEWED_PATH, RawKind("ads_viewed"), 4, 4, 0), parsed)
        }

    @Test
    fun malformedFileIsFailedWithAReasonAndTheRestIsStillProcessed() =
        runTest {
            SyntheticInstagramExport(adsViewed = 2, malformedAdsViewed = true, mediaFiles = 2).writeTo(fs, root)

            val summary = import(Export.directory(fs, root)).summary()

            val failed = summary.failed.single()
            assertEquals(InstagramFixtures.ADS_VIEWED_PATH, failed.path)
            assertEquals(RawKind("ads_viewed"), failed.rawKind)
            assertTrue(failed.reason.isNotBlank())
            assertEquals(1, summary.skipped.size)
            assertEquals(Inventory(jsonFiles = 2, mediaFiles = 2, otherFiles = 0), summary.inventory)
        }

    @Test
    fun unmappedJsonIsSkippedWithItsRawKindAndMediaIsCountedNeverOpened() =
        runTest {
            SyntheticInstagramExport(adsViewed = 1, mediaFiles = 5).writeTo(fs, root)
            val opened = mutableListOf<Path>()
            val recording =
                object : ForwardingFileSystem(fs) {
                    override fun source(file: Path): Source = super.source(file).also { opened += file }
                }

            val summary = import(Export.directory(recording, root)).summary()

            val skipped = summary.skipped.single()
            assertEquals(InstagramFixtures.UNMAPPED_PATH, skipped.path)
            assertEquals(RawKind("advertisers_using_your_activity_or_information"), skipped.rawKind)
            assertEquals(Inventory(jsonFiles = 2, mediaFiles = 5, otherFiles = 0), summary.inventory)
            assertEquals(
                emptyList(),
                opened.filter { ExportFile(ExportId("x"), it.toString()).isMedia },
                "media files must never be opened",
            )
            assertEquals(2, opened.size)
        }

    @Test
    fun zipAndDirectoryProduceIdenticalStepsAndTheZipIsNeverExtracted() =
        runTest {
            val zip = "/exports/instagram-synthetic.zip".toPath()
            SyntheticInstagramExport(adsViewed = 3, mediaFiles = 2).writeTo(fs, root)
            fs.zipDirectory(root, zip)
            val pathsBefore = fs.allPaths

            val fromDirectory = import(Export.directory(fs, root))
            val fromZip = import(Export.zip(fs, zip))

            assertEquals(fromDirectory, fromZip)
            assertEquals(pathsBefore, fs.allPaths)
        }

    @Test
    fun progressIsEmittedPerFileInVisitOrder() =
        runTest {
            SyntheticInstagramExport(adsViewed = 1, mediaFiles = 1).writeTo(fs, root)

            val steps = import(Export.directory(fs, root))

            val expectedPaths = listOf(InstagramFixtures.ADS_VIEWED_PATH, InstagramFixtures.UNMAPPED_PATH).sorted()
            val started = steps.filterIsInstance<ImportStep.FileStarted>()
            assertEquals(expectedPaths, started.map { it.path })
            assertEquals(expectedPaths, steps.finished().map { it.outcome.result.path })
            assertEquals(listOf(ImportProgress(0, 2), ImportProgress(1, 2)), started.map { it.progress })
            steps.zipWithNext().filter { (a, _) -> a is ImportStep.FileStarted }.forEach { (a, b) ->
                assertIs<ImportStep.FileFinished>(b)
                assertEquals((a as ImportStep.FileStarted).path, b.outcome.result.path)
            }
            assertEquals(expectedPaths, steps.summary().files.map { it.path })
        }

    @Test
    fun handWrittenFixtureDropsTheUndatedRecordButKeepsItsIndex() =
        runTest {
            fs.writeText(root / InstagramFixtures.ADS_VIEWED_PATH.toPath(), InstagramFixtures.ADS_VIEWED_JSON)

            val steps = import(Export.directory(fs, root))

            val events =
                steps
                    .finished()
                    .single()
                    .outcome.output.events
            assertEquals(listOf(0, 1), events.map { it.provenance.recordIndex })
            assertEquals("logistics, growth", events[1].attributes["Hashtags / Name"])
            assertEquals(
                listOf("instagram:advertiser:examplecourier", "instagram:topic:logistics", "instagram:topic:growth"),
                events[1].entities.map { it.id },
            )
            assertEquals(
                FileResult.Parsed(
                    InstagramFixtures.ADS_VIEWED_PATH,
                    RawKind("ads_viewed"),
                    recordCount = 3,
                    eventCount = 2,
                    claimCount = 0,
                    droppedCount = 1,
                ),
                steps.summary().parsed.single(),
            )
        }

    @Test
    fun nonJsonNonMediaFilesAreSkippedAsNotJsonAndStillCountedAsOther() =
        runTest {
            SyntheticInstagramExport(
                adsViewed = 1,
                includeUnmappedFile = false,
                mediaFiles = 1,
                otherFiles = 1,
            ).writeTo(fs, root)

            val steps = import(Export.directory(fs, root))

            val skipped = steps.summary().skipped.single()
            assertEquals("personal_information/notes_0.txt", skipped.path)
            assertEquals(RawKind("notes_0"), skipped.rawKind)
            assertEquals("not a JSON file", skipped.reason)
            assertEquals(Inventory(jsonFiles = 1, mediaFiles = 1, otherFiles = 1), steps.summary().inventory)
            assertEquals(
                listOf(InstagramFixtures.ADS_VIEWED_PATH, "personal_information/notes_0.txt"),
                steps.finished().map { it.outcome.result.path },
            )
        }

    @Test
    fun everyTimedFileIsParsedAndEventsCountPerKindAsComputedByHandFromTheFixture() =
        runTest {
            SyntheticInstagramExport(adsViewed = 5, includeTimedFiles = true).writeTo(fs, root)

            val steps = import(Export.directory(fs, root))
            val summary = steps.summary()
            val events = steps.finished().flatMap { it.outcome.output.events }

            assertEquals(emptyList(), summary.failed)
            assertEquals(listOf(InstagramFixtures.UNMAPPED_PATH), summary.skipped.map { it.path })
            assertEquals(32, summary.parsed.size, "the ads file and the 31 timed files")
            assertEquals(Inventory(jsonFiles = 33, mediaFiles = 3, otherFiles = 0), summary.inventory)
            assertEquals(
                mapOf(
                    EventKind.AdShown to 5,
                    EventKind.ContentViewed to 9,
                    EventKind.ContentLiked to 10,
                    EventKind.Login to 3,
                    EventKind.Logout to 2,
                    EventKind.Follow to 10,
                    EventKind.Unfollow to 2,
                    EventKind.Search to 4,
                    EventKind.LinkVisited to 2,
                    EventKind.Other to 10,
                ),
                events.groupingBy { it.kind }.eachCount(),
            )
            assertEquals(
                EventKind.entries.toSet() - EventKind.MessageSent - EventKind.MessageReceived,
                events.map { it.kind }.toSet(),
                "the full non-message Event vocabulary appears",
            )
            assertEquals(
                2,
                summary.parsed.sumOf { it.droppedCount },
                "the undated post view and the undated follower are dropped, not failed",
            )
            val social = events.filter { it.kind == EventKind.Follow || it.kind == EventKind.Unfollow }
            assertTrue(social.all { event -> event.entities.any { it.kind == EntityKind.Account } })
            val viewed = events.filter { it.kind == EventKind.ContentViewed || it.kind == EventKind.AdShown }
            val named = viewed.map { event -> event.entities.single { it.kind != EntityKind.Topic }.id }
            assertEquals(5, named.count { it.startsWith("instagram:advertiser:") }, "every ad names its advertiser")
            assertEquals(9, named.count { it.startsWith("instagram:account:") }, "every view names its author")
        }
}
