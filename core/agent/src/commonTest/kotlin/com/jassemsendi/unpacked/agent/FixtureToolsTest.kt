package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.fixtures.InstagramFixtures
import com.jassemsendi.unpacked.fixtures.SyntheticInstagramExport
import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.ingest.Importer
import com.jassemsendi.unpacked.ingest.InstagramAdapter
import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Seam 2 over the fixture: the Tools against fake repositories seeded from the synthetic Export through the real
 * Instagram adapter, with the values worked out from what the fixture is known to hold and every citation opened.
 */
class FixtureToolsTest {
    private val imports = FakeImportRepository()
    private val events = FakeEventRepository(imports)
    private val claims = FakeClaimRepository(imports)
    private val entities = FakeEntityRepository(events, claims)
    private val search = FakeSearchRepository(events, claims)
    private val tools =
        LibraryTools(imports, events, claims, entities, search, TimeZone.UTC)

    /** Five ads an hour apart from the first time, plus the untimed files the Instagram Evidence rules look at. */
    private val export = SyntheticInstagramExport(adsViewed = 5, includeClaimFiles = true)
    private val exportId = ExportId("instagram-synthetic")

    private suspend fun seeded(): Import {
        val fs = FakeFileSystem()
        val root = "/exports/instagram-synthetic".toPath()
        export.writeTo(fs, root)
        val steps = Importer(InstagramAdapter).run(Export.directory(fs, root)).toList()
        steps.filterIsInstance<ImportStep.FileFinished>().forEach { step ->
            events.events += step.outcome.output.events
            claims.claims += step.outcome.output.claims
        }
        val summary = assertIs<ImportStep.Completed>(steps.last()).summary
        val startedAt = Instant.parse("2026-10-27T10:12:00Z")
        return Import(ImportId(1), startedAt, startedAt + 1.minutes, summary, ExportLocation.Folder(root.toString()))
            .also { imports.imports += it }
    }

    /** A citation is only worth giving if it opens: every cited Provenance must be an Event's or a Claim's. */
    private suspend fun assertEveryCitationOpens(cited: List<Provenance>) {
        assertTrue(cited.isNotEmpty(), "nothing cited")
        cited.forEach { provenance ->
            val opens =
                events.byProvenance(provenance) is Result.Success || claims.byProvenance(provenance) is Result.Success
            assertTrue(opens, "$provenance opens no record")
        }
    }

    @Test
    fun theToolsAnswerOverTheFixtureWithItsKnownCountsAndEveryCitationOpens() =
        runTest {
            val import = seeded()

            val listed = tools.listImports().orFail()
            assertEquals(listOf(ImportRef.of(import)), listed.imports)
            assertEquals(import.summary.eventCount, listed.imports.single().eventCount)
            assertEquals(Source.Instagram, listed.imports.single().source)

            // Ads are an hour apart in file order, so the newest three are the last three records of the file.
            val ads = tools.countEvents(kind = "AdShown").orFail()
            assertEquals(5, ads.total)
            assertEquals(listOf(4, 3, 2).map { Provenance(exportId, InstagramFixtures.ADS_VIEWED_PATH, it) }, ads.cited)
            assertEveryCitationOpens(ads.cited)

            // The five ads span five hours of one day; the untimed files add no Events.
            val everything = tools.countEvents(groupBy = "day").orFail()
            assertEquals(5, everything.total)
            assertEquals(import.summary.eventCount, everything.total)
            assertEquals(listOf(5), everything.series.orEmpty().map { it.count })

            // Ads name their advertiser; the ranking is the fixture's own tally, most shown first, ties by name.
            val shown =
                export.ads
                    .groupingBy { it.name }
                    .eachCount()
                    .entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
                    .map { it.key to it.value }
            val top = tools.topEntities("AdShown", "Advertiser", count = 10).orFail()
            assertEquals(shown.size, top.count)
            assertEquals(shown, top.top.map { it.entity.displayName to it.count })
            top.top.forEach { ranked -> assertEquals(minOf(ranked.count, LibraryTools.CITED_COUNT), ranked.cited.size) }
            assertEveryCitationOpens(top.cited)

            val mostShown =
                export.ads
                    .groupingBy { it.username }
                    .eachCount()
                    .maxBy { it.value }
            val timeline = tools.entityTimeline("instagram:advertiser:${mostShown.key}").orFail()
            assertEquals(mostShown.value, timeline.eventCount)
            assertEquals(mostShown.value, timeline.events.size)
            assertEveryCitationOpens(timeline.cited)

            // Each ad's caption names its index, so one caption is found and nothing else.
            val found = tools.searchText("Synthetic ad 3").orFail()
            assertEquals(listOf(Provenance(exportId, InstagramFixtures.ADS_VIEWED_PATH, 3)), found.cited)
            assertEveryCitationOpens(found.cited)

            assertEquals(emptyList(), tools.findMatches("Advertiser").orFail().matches, "one Source imported today")

            // The Instagram rules over the fixture's untimed files, as the Gap report seam works them out by hand.
            val reported = tools.gapReport(1).orFail()
            assertEquals(
                listOf(
                    Coverage.Partial,
                    Coverage.Covered,
                    Coverage.Partial,
                    Coverage.Missing,
                    Coverage.Missing,
                    Coverage.Missing,
                    Coverage.Missing,
                    Coverage.Partial,
                    Coverage.Covered,
                ),
                reported.report.verdicts.map { it.coverage },
            )
            assertEquals(ArticleItem.entries, reported.report.verdicts.map { it.item })
            assertEveryCitationOpens(reported.cited)
        }
}
