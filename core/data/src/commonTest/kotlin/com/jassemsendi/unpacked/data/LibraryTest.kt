package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.fixtures.InstagramFixtures
import com.jassemsendi.unpacked.fixtures.SyntheticInstagramExport
import com.jassemsendi.unpacked.fixtures.writeText
import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.ingest.InstagramAdapter
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.Inventory
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Seam 1: a synthetic Export in, a library out, asserted only through the repository interfaces.
 * Abstract because opening an in-memory library differs per platform; each target has a one-line subclass.
 */
abstract class LibraryTest {
    private val fs = FakeFileSystem()
    private val root = "/exports/instagram-synthetic".toPath()
    private val clock = TickingClock()
    private lateinit var library: Library

    protected abstract fun openLibrary(clock: Clock): Library

    @BeforeTest
    fun open() {
        library = openLibrary(clock)
    }

    @AfterTest
    fun close() = library.close()

    private suspend fun import(export: Export): Import {
        val steps = library.import(export, InstagramAdapter).toList()
        return assertIs<ImportStep.Stored>(steps.last()).import
    }

    @Test
    fun importedExportIsCountedPerKindAndItsFilesAreListedWithReasons() =
        runTest {
            SyntheticInstagramExport(adsViewed = 4, mediaFiles = 2, otherFiles = 1).writeTo(fs, root)

            val import = import(Export.directory(fs, root))

            assertEquals(mapOf(EventKind.AdShown to 4), library.events.countByKind(import.id))
            assertEquals(emptyMap(), library.claims.countByKind(import.id))
            assertEquals(import, library.imports.get(import.id))
            assertEquals(listOf(import), library.imports.all())
            assertEquals(ExportId("instagram-synthetic"), import.summary.exportId)
            assertEquals(Source.Instagram, import.summary.source)
            assertEquals(Inventory(jsonFiles = 2, mediaFiles = 2, otherFiles = 1), import.summary.inventory)
            assertEquals(
                listOf(
                    FileResult.Parsed(InstagramFixtures.ADS_VIEWED_PATH, RawKind("ads_viewed"), 4, 4, 0),
                    FileResult.Skipped(
                        InstagramFixtures.UNMAPPED_PATH,
                        RawKind("advertisers_using_your_activity_or_information"),
                        "no mapper registered for advertisers_using_your_activity_or_information.json",
                    ),
                    FileResult.Skipped("personal_information/notes_0.txt", RawKind("notes_0"), "not a JSON file"),
                ),
                import.summary.files,
            )
            assertEquals(TickingClock.START, import.startedAt)
            assertEquals(TickingClock.START + TickingClock.TICK, import.completedAt)
        }

    @Test
    fun aFileThatCannotBeReadIsRecordedAsFailedWithItsReasonAndTheRestStillLands() =
        runTest {
            SyntheticInstagramExport(adsViewed = 2, malformedAdsViewed = true, mediaFiles = 1).writeTo(fs, root)

            val import = import(Export.directory(fs, root))

            val failed = import.summary.failed.single()
            assertEquals(InstagramFixtures.ADS_VIEWED_PATH, failed.path)
            assertEquals(RawKind("ads_viewed"), failed.rawKind)
            assertTrue(failed.reason.isNotBlank())
            assertEquals(1, import.summary.skipped.size)
            assertEquals(emptyMap(), library.events.countByKind(import.id))
            assertEquals(Inventory(jsonFiles = 2, mediaFiles = 1, otherFiles = 0), import.summary.inventory)
            assertEquals(import, library.imports.get(import.id), "the Failed entry and its reason are stored")
        }

    @Test
    fun anEventReadsBackWithItsAttributesRawKindProvenanceAndEntities() =
        runTest {
            fs.writeText(root / InstagramFixtures.ADS_VIEWED_PATH.toPath(), InstagramFixtures.ADS_VIEWED_JSON)
            val import = import(Export.directory(fs, root))
            val provenance = Provenance(ExportId("instagram-synthetic"), InstagramFixtures.ADS_VIEWED_PATH, 1)

            val event = assertNotNull(library.events.byProvenance(provenance))

            assertEquals(EventKind.AdShown, event.kind)
            assertEquals(RawKind("ads_viewed"), event.rawKind)
            assertEquals(provenance, event.provenance)
            assertEquals(Instant.fromEpochSeconds(InstagramFixtures.FIRST_AD_TIME_SECONDS) + 1.minutes, event.time)
            assertEquals(
                listOf("URL", "Caption", "Hashtags / Name", "Owner / URL", "Owner / Name", "Owner / Username"),
                event.attributes.keys.toList(),
                "attribute order follows the record",
            )
            assertEquals("Ship anything #logistics #growth", event.attributes["Caption"])
            assertEquals("logistics, growth", event.attributes["Hashtags / Name"])
            val advertiser = event.entities.single()
            assertEquals("instagram:advertiser:examplecourier", advertiser.id)
            assertEquals(EntityKind.Advertiser, advertiser.kind)
            assertEquals("Example Courier", advertiser.displayName)
            assertEquals(
                mapOf("username" to "ExampleCourier", "url" to "http://example.com/courier"),
                advertiser.attributes,
            )
            assertEquals(advertiser, library.entities.byId(advertiser.id))
            assertNull(library.events.byProvenance(provenance.copy(recordIndex = 2)), "the undated record is dropped")
            assertEquals(listOf(0, 1), library.events.forImport(import.id).map { it.provenance.recordIndex })
        }

    @Test
    fun importingTheSameExportTwiceRecordsTwoImportsAndChangesNoCounts() =
        runTest {
            SyntheticInstagramExport(adsViewed = 6).writeTo(fs, root)
            val export = Export.directory(fs, root)

            val first = import(export)
            val second = import(export)

            assertEquals(listOf(first.id, second.id), library.imports.all().map { it.id })
            assertEquals(first.summary, second.summary)
            assertEquals(mapOf(EventKind.AdShown to 6), library.events.countByKind())
            assertEquals(mapOf(EventKind.AdShown to 6), library.events.countByKind(second.id))
            assertEquals(emptyMap(), library.events.countByKind(first.id), "the latest Import owns the rows")
        }

    @Test
    fun anEntityNamedByManyEventsIsStoredOnceUnderSourceKindKey() =
        runTest {
            val generator = SyntheticInstagramExport(adsViewed = 12)
            generator.writeTo(fs, root)

            import(Export.directory(fs, root))

            val expectedIds =
                generator.ads
                    .map { "instagram:advertiser:${it.username}" }
                    .distinct()
                    .sorted()
            assertTrue(expectedIds.size < generator.ads.size, "the fixture must repeat advertisers")
            assertEquals(expectedIds, library.entities.all().map { it.id })
        }

    @Test
    fun deletingAnImportRemovesOnlyWhatItProducedAndEntitiesNobodyNeedsAnyMore() =
        runTest {
            val rootA = "/exports/instagram-a".toPath()
            val rootB = "/exports/instagram-b".toPath()
            SyntheticInstagramExport(adsViewed = 8, seed = 1).writeTo(fs, rootA)
            SyntheticInstagramExport(adsViewed = 1, seed = 2).writeTo(fs, rootB)
            val importA = import(Export.directory(fs, rootA))
            val importB = import(Export.directory(fs, rootB))
            val entitiesOfA =
                library.events
                    .forImport(importA.id)
                    .flatMap { it.entities }
                    .toSet()
            val entitiesOfB =
                library.events
                    .forImport(importB.id)
                    .flatMap { it.entities }
                    .toSet()
            assertTrue((entitiesOfA - entitiesOfB).isNotEmpty(), "A must name an advertiser B does not")

            library.imports.delete(importA.id)

            assertNull(library.imports.get(importA.id))
            assertEquals(listOf(importB), library.imports.all())
            assertEquals(emptyMap(), library.events.countByKind(importA.id))
            assertEquals(emptyList(), library.events.forImport(importA.id))
            assertEquals(mapOf(EventKind.AdShown to 1), library.events.countByKind())
            assertEquals(entitiesOfB.sortedBy { it.id }, library.entities.all(), "only Entities nobody references go")
            val survivor = library.events.forImport(importB.id).single()
            assertEquals(entitiesOfB.toList(), survivor.entities)
            assertTrue(survivor.attributes.isNotEmpty())

            library.imports.delete(importB.id)

            assertEquals(emptyList(), library.imports.all())
            assertEquals(emptyMap(), library.events.countByKind())
            assertEquals(emptyList(), library.entities.all())
        }

    @Test
    fun anEntityTwoImportsShareSurvivesUntilTheLastOfThemIsDeleted() =
        runTest {
            val rootA = "/exports/instagram-a".toPath()
            val rootB = "/exports/instagram-b".toPath()
            SyntheticInstagramExport(adsViewed = 3).writeTo(fs, rootA)
            SyntheticInstagramExport(adsViewed = 3).writeTo(fs, rootB)
            val importA = import(Export.directory(fs, rootA))
            val importB = import(Export.directory(fs, rootB))
            val shared = library.entities.all()
            assertTrue(shared.isNotEmpty())

            library.imports.delete(importA.id)
            assertEquals(shared, library.entities.all())

            library.imports.delete(importB.id)
            assertEquals(emptyList(), library.entities.all())
        }

    @Test
    fun anImportCutShortStaysVisibleWithWhatItWroteAndNoCompletionTime() =
        runTest {
            SyntheticInstagramExport(adsViewed = 2).writeTo(fs, root)

            val steps = library.import(Export.directory(fs, root), InstagramAdapter).take(3).toList()

            assertIs<ImportStep.Started>(steps[0])
            assertIs<ImportStep.FileStarted>(steps[1])
            assertIs<ImportStep.FileFinished>(steps[2])
            val import = library.imports.all().single()
            assertNull(import.completedAt)
            assertEquals(TickingClock.START, import.startedAt)
            assertEquals(listOf(InstagramFixtures.ADS_VIEWED_PATH), import.summary.files.map { it.path })
            assertEquals(mapOf(EventKind.AdShown to 2), library.events.countByKind(import.id))
        }
}

/** A Clock that advances by one tick per reading, so started and completed times are distinct and predictable. */
private class TickingClock : Clock {
    private var readings = 0

    override fun now(): Instant = START + TICK * readings++

    companion object {
        val START: Instant = Instant.fromEpochSeconds(1_800_000_000)
        val TICK = 1.seconds
    }
}
