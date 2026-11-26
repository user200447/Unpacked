package com.jassemsendi.unpacked.agent

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Koog wrappers: eight Tools with the names the glossary gives them, hints in step with the Kinds, outputs marked. */
class AgentToolsTest {
    private val library = TestLibrary()
    private val tools = with(library) { LibraryTools(imports, events, claims, entities, search, zone) }
    private val state = RunState(stepCap = 8, timeZone = library.zone)
    private val wrapped = agentTools(tools, state)

    @Test
    fun theEightToolsAreOfferedUnderTheirGlossaryNames() {
        assertEquals(
            listOf(
                LIST_IMPORTS,
                COUNT_EVENTS,
                TOP_ENTITIES,
                ENTITY_TIMELINE,
                SEARCH_TEXT,
                FIND_MATCHES,
                GAP_REPORT,
                CHART_SPEC,
            ),
            wrapped.map { it.name },
        )
        assertTrue(wrapped.all { it.descriptor.description.isNotBlank() })
        val count = wrapped.first { it.name == COUNT_EVENTS }.descriptor
        assertEquals(listOf("kind", "from", "to", "source", "groupBy"), count.optionalParameters.map { it.name })
        assertEquals(emptyList(), count.requiredParameters)
        val top = wrapped.first { it.name == TOP_ENTITIES }.descriptor
        assertEquals(listOf("kind", "entityKind"), top.requiredParameters.map { it.name })
    }

    @Test
    fun theKindHintsNameEveryKind() {
        EVENT_KIND_NAMES.forEach { assertTrue(it in KIND_HINT, it) }
        ENTITY_KIND_NAMES.forEach { assertTrue(it in ENTITY_KIND_HINT, it) }
    }

    @Test
    fun anOutputIsRenderedWithCitationMarkersAndLocalTimes() =
        runTest {
            val timeline = wrapped.first { it.name == ENTITY_TIMELINE }

            @Suppress("UNCHECKED_CAST")
            val output = (timeline as RecordedTool<TimelineArgs>).execute(TimelineArgs(library.courier.id))

            assertTrue(output.contains("\"cite\":\"[1]\""), output)
            assertTrue(output.contains("\"time\":\"2026-10-27T11:00\""), "10:00Z is 11:00 in Tunis: $output")
            assertTrue(!output.contains("ads_viewed.json"), "the model never sees a file path")
            assertEquals(library.courierAd.provenance, state.citations.citationOf(1)?.provenance)
            assertEquals(1, state.trace.size)
            assertEquals(
                """{"entityId":"${library.courier.id}"}""",
                state.trace[0].input,
                "no defaults the model never wrote",
            )
        }

    @Test
    fun aChartBeforeAnyOutputFailsWithAReasonAndAfterOneSucceeds() =
        runTest {
            @Suppress("UNCHECKED_CAST")
            val chart = wrapped.first { it.name == CHART_SPEC } as RecordedTool<ChartArgs>

            @Suppress("UNCHECKED_CAST")
            val count = wrapped.first { it.name == COUNT_EVENTS } as RecordedTool<CountArgs>

            val refused = chart.execute(ChartArgs(COUNT_EVENTS))
            assertEquals(ToolError.NoOutputToChart(COUNT_EVENTS).describe(), refused)
            assertTrue(state.trace[0].failed)

            count.execute(CountArgs(kind = "AdShown", groupBy = "Month"))
            chart.execute(ChartArgs(COUNT_EVENTS))
            assertEquals(ChartKind.TimeSeries, state.chart?.kind)
            assertEquals(listOf(1, 2, 3), state.trace.map { it.number })
            assertEquals(3, state.outputs.size, "a refusal is a Tool output too; its numbers may be repeated")
        }
}
