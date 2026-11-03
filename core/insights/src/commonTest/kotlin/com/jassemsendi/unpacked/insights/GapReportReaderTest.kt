package com.jassemsendi.unpacked.insights

import com.jassemsendi.unpacked.data.ImportRepository
import com.jassemsendi.unpacked.fixtures.SyntheticInstagramExport
import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.ingest.Importer
import com.jassemsendi.unpacked.ingest.InstagramAdapter
import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EmptyResult
import com.jassemsendi.unpacked.model.EvidenceRule
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Seam 2: the Gap report against fake repositories seeded from the synthetic Export through the real Instagram
 * adapter, with every Verdict worked out by hand from the Instagram Evidence rules and the fixture's counts.
 */
class GapReportReaderTest {
    private val events = FakeEventRepository()
    private val claims = FakeClaimRepository()
    private val imports = FakeImportRepository()

    /** The fixture with its untimed files, read by the adapter into the fakes; the stored Import it would make. */
    private suspend fun seeded(): Import {
        val fs = FakeFileSystem()
        val root = "/exports/instagram-synthetic".toPath()
        SyntheticInstagramExport(includeClaimFiles = true).writeTo(fs, root)
        val steps = Importer(InstagramAdapter).run(Export.directory(fs, root)).toList()
        steps.filterIsInstance<ImportStep.FileFinished>().forEach { step ->
            events.events += step.outcome.output.events
            claims.claims += step.outcome.output.claims
        }
        val summary = assertIs<ImportStep.Completed>(steps.last()).summary
        val startedAt = Instant.parse("2026-10-27T10:12:00Z")
        return Import(ImportId(1), startedAt, startedAt + 1.minutes, summary).also { imports.imports += it }
    }

    private fun reader(rules: EvidenceRules = DeclaredEvidenceRules) = GapReportReader(imports, events, claims, rules)

    @Test
    fun theInstagramRulesAgainstTheFixtureYieldTheHandWorkedVerdicts() =
        runTest {
            val import = seeded()

            val report = reader().read(import.id).orFail()

            assertEquals(import.id, report.importId)
            assertEquals(import.summary.exportId, report.exportId)
            assertEquals(Source.Instagram, report.source)
            assertEquals(ArticleItem.entries, report.verdicts.map { it.item })
            assertEquals(
                mapOf(
                    ArticleItem.Purposes to Coverage.Partial,
                    ArticleItem.Categories to Coverage.Covered,
                    ArticleItem.Recipients to Coverage.Partial,
                    ArticleItem.RetentionPeriod to Coverage.Missing,
                    ArticleItem.RightsToRectifyAndErase to Coverage.Missing,
                    ArticleItem.RightToComplain to Coverage.Missing,
                    ArticleItem.SourceOfData to Coverage.Missing,
                    ArticleItem.AutomatedDecisions to Coverage.Partial,
                    ArticleItem.CopyOfData to Coverage.Covered,
                ),
                report.verdicts.associate { it.item to it.coverage },
            )

            // The fixture's Claims per Kind, as its generator documents them.
            assertEquals(
                mapOf(
                    ArticleItem.Purposes to listOf(ClaimKind.ConsentGiven to 1, ClaimKind.OffPlatformSetting to 1),
                    ArticleItem.Recipients to listOf(ClaimKind.AdvertiserHoldsContact to 4),
                    ArticleItem.AutomatedDecisions to
                        listOf(
                            ClaimKind.InferredInterest to 8,
                            ClaimKind.InferredLocation to 5,
                            ClaimKind.ReachCategory to 3,
                        ),
                ),
                report.verdicts
                    .filter { it.coverage == Coverage.Partial }
                    .associate { verdict ->
                        verdict.item to
                            verdict.evidence.map { evidence ->
                                val records = assertIs<Evidence.Records>(evidence)
                                assertIs<EvidenceRule.ClaimsOf>(records.rule).kind to records.count
                            }
                    },
            )
            // The covered items cite every JSON file of the inventory, the ones no mapper knows included.
            val jsonFiles = import.summary.files.filter { it.path.endsWith(".json") }
            assertTrue(jsonFiles.size > 1 && jsonFiles.any { it.path.endsWith("subscription_for_no_ads.json") })
            report.verdicts.filter { it.coverage == Coverage.Covered }.forEach { verdict ->
                assertEquals(
                    listOf(jsonFiles),
                    verdict.evidence.map { assertIs<Evidence.Files>(it).files },
                    "${verdict.item}",
                )
            }
            // Missing carries nothing.
            report.verdicts.filter { it.coverage == Coverage.Missing }.forEach {
                assertEquals(
                    emptyList(),
                    it.evidence,
                )
            }
        }

    @Test
    fun aVerdictCitesTheFirstRecordsOfItsKindByTheirProvenanceAndNoMore() =
        runTest {
            seeded()

            val report = reader().read(ImportId(1)).orFail()

            report.verdicts.flatMap { it.evidence }.filterIsInstance<Evidence.Records>().forEach { records ->
                val kind = assertIs<EvidenceRule.ClaimsOf>(records.rule).kind
                assertEquals(minOf(records.count, GapReportReader.CITED_COUNT), records.cited.size, "$kind")
                records.cited.forEach { provenance ->
                    assertEquals(kind, claims.byProvenance(provenance).orFail().kind, "$provenance")
                }
            }
        }

    @Test
    fun aSourceWithNoEvidenceRulesYieldsNineMissingVerdictsNotAnError() =
        runTest {
            val import = seeded()

            val report = reader(rules = EvidenceRules { emptyList() }).read(import.id).orFail()

            assertEquals(ArticleItem.entries.map { Verdict(it, Coverage.Missing, emptyList()) }, report.verdicts)
        }

    @Test
    fun theReportIsIdenticalOnRepeatedComputationOverTheSameImport() =
        runTest {
            val import = seeded()

            assertEquals(reader().read(import.id), reader().read(import.id))
        }

    @Test
    fun anImportThatIsNotThereIsNotFoundAndALibraryThatWillNotReadFailsTheReport() =
        runTest {
            val import = seeded()

            assertEquals(Result.Error(DataError.Local.NOT_FOUND), reader().read(ImportId(99)))

            claims.failure = DataError.Local.UNKNOWN
            assertEquals(Result.Error(DataError.Local.UNKNOWN), reader().read(import.id))
        }
}

/** The stored Imports in memory, answering as the Room one does. */
class FakeImportRepository : ImportRepository {
    val imports = mutableListOf<Import>()

    override suspend fun all(): Result<List<Import>, DataError.Local> = Result.Success(imports.toList())

    override suspend fun get(id: ImportId): Result<Import, DataError.Local> =
        imports.find { it.id == id }?.let { Result.Success(it) } ?: Result.Error(DataError.Local.NOT_FOUND)

    override suspend fun delete(id: ImportId): EmptyResult<DataError.Local> {
        imports.removeAll { it.id == id }
        return Result.Success(Unit)
    }
}
