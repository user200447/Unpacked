package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The payload boundary of ADR 0006: every request a cloud model receives for a question, serialised as the Payload
 * inspector shows it, holds the question and the Tool outputs and no field of an Export record. The Tools here
 * are the ones a cloud question spends: counts, rankings, a search, one Entity's timeline.
 */
class PayloadBoundaryTest {
    private val library = TestLibrary()
    private val tools = with(library) { LibraryTools(imports, events, claims, entities, search, zone) }

    /** What a Record holds that a model must never see, worked out by [recordFields]. */
    private val recordFields: Set<String> = recordFields(library.events.events, library.claims.claims)

    /** Where every Record came from: the model is told markers, never paths or positions. */
    private val paths: Set<String> =
        with(library) { (events.events.map { it.provenance.path } + claims.claims.map { it.provenance.path }).toSet() }

    @Test
    fun theRecordHasFieldsWorthHiding() {
        assertTrue(recordFields.containsAll(setOf("Caption", "Rooms by the sea", "Parcels overnight")), "$recordFields")
    }

    @Test
    fun everyRequestOfACloudQuestionCarriesTheQuestionAndToolOutputsAndNoRecordField() =
        runTest {
            val model =
                ScriptedModel(
                    says("Any"),
                    calls(COUNT_EVENTS, """{"kind":"AdShown"}"""),
                    calls(TOP_ENTITIES, """{"kind":"AdShown","entityKind":"Advertiser","count":3}"""),
                    calls(SEARCH_TEXT, """{"text":"parcels"}"""),
                    calls(ENTITY_TIMELINE, """{"entityId":"${library.courier.id}"}"""),
                    says("You were shown 4 ads [1]; Example Courier placed 2 [2]."),
                )
            val shown = mutableListOf<CloudPayload>()
            val agent = KoogAskAgent(tools, FakeModelProvider(model, locality = ModelLocality.Cloud), library.zone)

            val outcome =
                agent
                    .ask("How many ads did I see, and from whom?") { payload ->
                        shown += payload
                        true
                    }.orFail()

            assertIs<AskOutcome.Answered>(outcome)
            val payloads = model.calls.map { CloudPayload.of(it.prompt, GeminiModelProvider.MODEL, it.tools) }
            assertEquals(6, payloads.size, "classify, ask, four Tool rounds")
            assertEquals(payloads.first(), shown.single(), "the inspector saw the first request as it was sent")

            val rendered = payloads.map { it.render() }
            assertTrue(rendered.all { it.contains("How many ads did I see, and from whom?") })
            assertTrue(rendered.last().contains("\\\"total\\\":4"), "the Tool outputs travel as the model saw them")
            rendered.assertNoneNames(recordFields, "record field")
            rendered.assertNoneContains(paths, "Export file")
        }

    @Test
    fun theRenderingIsReadableJsonWithEveryMessageAndTool() =
        runTest {
            val model = ScriptedModel(says("Counts"), says("Nothing."))
            var first: CloudPayload? = null
            val agent = KoogAskAgent(tools, FakeModelProvider(model, locality = ModelLocality.Cloud), library.zone)

            agent
                .ask("How many ads?") { payload ->
                    first = payload
                    true
                }.orFail()

            val payload = checkNotNull(first)
            assertEquals(GeminiModelProvider.MODEL.id, payload.model)
            assertEquals(listOf("system", "user"), payload.messages.map { it.role })
            assertTrue(
                payload.messages[1]
                    .text
                    .orEmpty()
                    .contains("How many ads?"),
            )
            assertEquals(emptyList(), payload.tools, "the classification step offers no Tool")

            val second = CloudPayload.of(model.calls[1].prompt, GeminiModelProvider.MODEL, model.calls[1].tools)
            val offered = second.tools.map { it.name }.toSet()
            assertEquals(setOf(COUNT_EVENTS, TOP_ENTITIES, CHART_SPEC, LIST_IMPORTS), offered)
            val count = second.tools.first { it.name == COUNT_EVENTS }
            assertTrue(count.parameters.any { it.name == "kind" && it.type == "string" }, "$count")
            assertTrue(second.render().startsWith("{\n"), "pretty-printed for a person to read")
        }
}

/**
 * What a Record holds that a model must never see: its labels and values, except a value that is also how an Entity
 * is displayed, which a Tool may name (ADR 0006), and a label that is a Kind's own name.
 */
internal fun recordFields(
    events: List<Event>,
    claims: List<Claim>,
): Set<String> {
    val displayNames = (events.flatMap { it.entities } + claims.flatMap { it.entities }).map { it.displayName }
    val kindNames =
        EventKind.entries.map { it.name } + ClaimKind.entries.map { it.name } + EntityKind.entries.map { it.name }
    val attributes = events.flatMap { it.attributes.entries } + claims.flatMap { it.attributes.entries }
    return (attributes.map { it.key } + attributes.map { it.value })
        .filter { field -> field.isNotBlank() && displayNames.none { it.contains(field) } && field !in kindNames }
        .toSet()
}

/** Fails if any rendered request names one of [words] as a whole word. */
internal fun List<String>.assertNoneNames(
    words: Set<String>,
    what: String,
) {
    for ((index, text) in withIndex()) {
        for (word in words) {
            val named = Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(text)
            assertTrue(!named, "request $index names the $what “$word”")
        }
    }
}

/** Fails if any rendered request contains one of [pieces] anywhere. */
internal fun List<String>.assertNoneContains(
    pieces: Set<String>,
    what: String,
) {
    for ((index, text) in withIndex()) {
        for (piece in pieces) {
            assertTrue(!text.contains(piece), "request $index contains the $what “$piece”")
        }
    }
}

/**
 * Drops the fields that are the agent's own vocabulary: words its instructions and Tool descriptions use before any
 * Tool output exists, found in [staticRequest], the request of a question that carries no Tool output yet. A record
 * label that is also such a word, like "Source", cannot be told from the instructions; its value still can.
 */
internal fun Set<String>.notInVocabulary(staticRequest: String): Set<String> =
    filterNot { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(staticRequest) }.toSet()
