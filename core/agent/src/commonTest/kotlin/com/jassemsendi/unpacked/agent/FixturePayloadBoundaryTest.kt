package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.fixtures.SyntheticInstagramExport
import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.ingest.Importer
import com.jassemsendi.unpacked.ingest.InstagramAdapter
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
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
 * The payload boundary over the fixture (spec story 40, ADR 0006): the synthetic Export, message threads and all,
 * goes through the real Instagram adapter into fake repositories, a cloud question calls the Tools over it, and no
 * request of that question carries a message's text, a row of the Export as written, or a Record's own fields.
 */
class FixturePayloadBoundaryTest {
    private val imports = FakeImportRepository()
    private val events = FakeEventRepository(imports)
    private val claims = FakeClaimRepository(imports)
    private val entities = FakeEntityRepository(events, claims)
    private val search = FakeSearchRepository(events, claims)
    private val tools = LibraryTools(imports, events, claims, entities, search, TimeZone.UTC)

    private val export =
        SyntheticInstagramExport(
            adsViewed = 5,
            includeTimedFiles = true,
            includeClaimFiles = true,
            includeMessageThreads = true,
        )
    private val fs = FakeFileSystem()
    private val root = "/exports/instagram-synthetic".toPath()

    /** Every file of the Export as the fixture wrote it, by path. */
    private val written: Map<String, String>
        get() =
            fs
                .listRecursively(root)
                .filter { fs.metadata(it).isRegularFile }
                .associate { it.relativeTo(root).toString() to fs.read(it) { readUtf8() } }

    private suspend fun seed() {
        export.writeTo(fs, root)
        val steps = Importer(InstagramAdapter).run(Export.directory(fs, root)).toList()
        steps.filterIsInstance<ImportStep.FileFinished>().forEach { step ->
            events.events += step.outcome.output.events
            claims.claims += step.outcome.output.claims
        }
        val summary = assertIs<ImportStep.Completed>(steps.last()).summary
        val startedAt = Instant.parse("2026-10-27T10:12:00Z")
        imports.imports +=
            Import(ImportId(1), startedAt, startedAt + 1.minutes, summary, ExportLocation.Folder("$root"))
    }

    @Test
    fun theFixtureHoldsWhatTheBoundaryMustKeepIn() =
        runTest {
            seed()

            assertTrue(messageTexts().contains("Are you coming to the match tonight?"), "${messageTexts()}")
            assertTrue(events.events.any { it.kind == EventKind.MessageReceived }, "the threads became message Events")
            assertTrue(rawRowValues().size > 10, "${rawRowValues()}")
            assertTrue(recordFields(events.events, claims.claims).isNotEmpty())
        }

    @Test
    fun noRequestOfACloudQuestionOverTheFixtureCarriesAMessageARowOrARecordField() =
        runTest {
            seed()
            val model =
                ScriptedModel(
                    says("Any"),
                    calls(COUNT_EVENTS, """{"kind":"MessageReceived"}"""),
                    calls(TOP_ENTITIES, """{"kind":"MessageReceived","entityKind":"Thread","count":5}"""),
                    calls(COUNT_EVENTS, """{"kind":"AdShown","groupBy":"day"}"""),
                    calls(SEARCH_TEXT, """{"text":"match tonight"}"""),
                    calls(LIST_IMPORTS, "{}"),
                    says("Nothing to count."),
                )
            val agent = KoogAskAgent(tools, FakeModelProvider(model, locality = ModelLocality.Cloud), TimeZone.UTC)

            agent.ask("Who messaged me, and what did they say?") { true }.orFail()

            assertEquals(7, model.calls.size, "classify, ask, five Tool rounds")
            val rendered = model.calls.map { CloudPayload.of(it.prompt, GeminiModelProvider.MODEL, it.tools).render() }
            assertTrue(rendered.last().contains("MessageReceived"), "the Tool outputs did travel")
            rendered.assertNoneContains(messageTexts(), "message")
            rendered.assertNoneNames(rawRowValues(), "row value")
            val fields = recordFields(events.events, claims.claims).notInVocabulary(staticRequest = rendered[1])
            assertTrue(fields.size > 10, "$fields")
            rendered.assertNoneNames(fields, "record field")
            rendered.assertNoneContains(written.keys, "Export file")
        }

    /** What people wrote in the threads: the one thing an Export holds that must never reach a model. */
    private fun messageTexts(): Set<String> =
        written
            .filterKeys { it.contains("/messages/") && it.endsWith(".json") }
            .values
            .flatMap { json -> CONTENT.findAll(json).map { it.groupValues[1] } }
            .filter { it.isNotBlank() }
            .toSet()

    /**
     * The rows as the Export wrote them: every epoch timestamp of every JSON file, which no Tool output repeats as
     * written. A zero stands for "no time" in a few files and is not a row's identity.
     */
    private fun rawRowValues(): Set<String> =
        written.values
            .flatMap { json -> TIMESTAMP.findAll(json).map { it.groupValues[1] } }
            .filter { it.length >= EPOCH_DIGITS }
            .toSet()

    private companion object {
        val CONTENT = Regex(""""content":\s*"([^"]*)"""")
        val TIMESTAMP = Regex(""""(?:timestamp|timestamp_ms|creation_timestamp)":\s*(\d+)""")
        const val EPOCH_DIGITS = 10
    }
}
