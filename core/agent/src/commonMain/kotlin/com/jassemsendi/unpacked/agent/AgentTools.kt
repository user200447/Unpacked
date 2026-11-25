package com.jassemsendi.unpacked.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.TypeToken
import ai.koog.serialization.typeToken
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.andThen
import kotlinx.datetime.TimeZone
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The eight Tools as the model calls them: thin Koog wrappers over LibraryTools that render each output as JSON with
// citation markers, and record every call in the Tool trace of the question being answered. The wrappers decide
// nothing; the Tools do (ADR 0006).

/** What one question accumulates while the model works: its Tool trace, its citations, and the retry flag. */
internal class RunState(
    val stepCap: Int,
    timeZone: TimeZone,
) {
    val citations = CitationIndex()
    private val json = ToolOutputJson(citations, timeZone)
    private val steps = mutableListOf<TraceStep>()
    val latest = mutableMapOf<String, ToolOutput>()
    var retried = false

    val trace: List<TraceStep> get() = steps

    /** Every text the model saw come back from a Tool, a refusal included: what an answer's numbers are checked against. */
    val outputs: List<String> get() = steps.map { it.output }

    val chart: ChartSpec? get() = latest.values.filterIsInstance<ChartSpec>().lastOrNull()

    /** True once the question has spent its Tool calls; the model is then asked to answer with what it has. */
    val capReached: Boolean get() = steps.size >= stepCap

    /** Records one call in the trace and returns the text the model sees; a call past the cap is refused unrecorded. */
    fun record(
        tool: String,
        input: String,
        result: Result<ToolOutput, ToolError>,
    ): String {
        if (capReached) return "No more Tool calls: this question has spent its $stepCap. Answer with what you have."
        val (output, failed) =
            when (result) {
                is Result.Success -> {
                    latest[tool] = result.data
                    json.render(result.data).toString() to false
                }

                is Result.Error -> {
                    result.error.describe() to true
                }
            }
        steps += TraceStep(steps.size + 1, tool, input, output, failed)
        return output
    }
}

/** Arguments as the Tool read them: only what the model gave, so the trace does not show defaults it never wrote. */
private val argsJson = Json { encodeDefaults = false }

/** A Tool the model can call: the typed call goes to [LibraryTools], the rendered output goes to the trace and back. */
internal abstract class RecordedTool<Args>(
    argsType: TypeToken,
    name: String,
    description: String,
    private val serializer: KSerializer<Args>,
    protected val state: RunState,
) : SimpleTool<Args>(argsType, name, description) {
    abstract suspend fun call(args: Args): Result<ToolOutput, ToolError>

    final override suspend fun execute(args: Args): String {
        if (state.capReached) return state.record(name, "", Result.Error(ToolError.BlankText))
        return state.record(name, argsJson.encodeToString(serializer, args), call(args))
    }
}

// Annotation arguments must be constants, so the kind names are spelled out; AgentToolsTest keeps them in step.
internal const val KIND_HINT =
    "An Event kind, one of: ContentViewed, AdShown, ContentLiked, Login, Logout, Follow, Unfollow, Search, " +
        "MessageSent, MessageReceived, LinkVisited, Other."
private const val DATE_HINT = "A date as YYYY-MM-DD, inclusive."
internal const val ENTITY_KIND_HINT =
    "An Entity kind, one of: Advertiser, Account, Device, Network, Topic, Place, Thread."

@Serializable
internal class NoArgs

@Serializable
internal data class CountArgs(
    @LLMDescription("$KIND_HINT Omit to count Events of every kind.")
    val kind: String? = null,
    @LLMDescription("$DATE_HINT Omit for no lower bound.")
    val from: String? = null,
    @LLMDescription("$DATE_HINT Omit for no upper bound.")
    val to: String? = null,
    @LLMDescription("A Source, Instagram or GoogleTakeout. Omit for every Source.")
    val source: String? = null,
    @LLMDescription("Day or Month, to get a series of counts per period as well as the total.")
    val groupBy: String? = null,
)

@Serializable
internal data class TopArgs(
    @LLMDescription(KIND_HINT)
    val kind: String,
    @LLMDescription(ENTITY_KIND_HINT)
    val entityKind: String,
    @LLMDescription("How many to rank, 1 to 50; 5 when omitted.")
    val count: Int = LibraryTools.TOP_COUNT,
    @LLMDescription(DATE_HINT)
    val from: String? = null,
    @LLMDescription(DATE_HINT)
    val to: String? = null,
)

@Serializable
internal data class TimelineArgs(
    @LLMDescription("An Entity id as returned by topEntities, searchText or findMatches.")
    val entityId: String,
    @LLMDescription("How many newest Events to list, 1 to 100; 20 when omitted.")
    val limit: Int = LibraryTools.PAGE_SIZE,
)

@Serializable
internal data class SearchArgs(
    @LLMDescription("Words to look for in Events and Claims.")
    val text: String,
    @LLMDescription("How many hits to list, 1 to 100; 20 when omitted.")
    val limit: Int = LibraryTools.PAGE_SIZE,
)

@Serializable
internal data class MatchArgs(
    @LLMDescription(ENTITY_KIND_HINT)
    val kind: String,
)

@Serializable
internal data class GapArgs(
    @LLMDescription("The id of an Import, as listed by listImports.")
    val importId: Long,
)

@Serializable
internal data class ChartArgs(
    @LLMDescription("Which Tool's latest output to chart: countEvents (with groupBy) or topEntities.")
    val ofTool: String,
)

internal const val LIST_IMPORTS = "listImports"
internal const val COUNT_EVENTS = "countEvents"
internal const val TOP_ENTITIES = "topEntities"
internal const val ENTITY_TIMELINE = "entityTimeline"
internal const val SEARCH_TEXT = "searchText"
internal const val FIND_MATCHES = "findMatches"
internal const val GAP_REPORT = "gapReport"
internal const val CHART_SPEC = "chartSpec"

/** The eight Tools wrapped for one question, in the glossary's order. */
internal fun agentTools(
    tools: LibraryTools,
    state: RunState,
): List<RecordedTool<*>> = readingTools(tools, state) + lookingTools(tools, state)

/** The Tools that read the Library's shape: what was imported, how many Events, who is named most, one Entity. */
private fun readingTools(
    tools: LibraryTools,
    state: RunState,
): List<RecordedTool<*>> =
    listOf(
        object : RecordedTool<NoArgs>(
            typeToken<NoArgs>(),
            LIST_IMPORTS,
            "Lists every Import of the Library: its id, Source, when it ran and what it produced.",
            NoArgs.serializer(),
            state,
        ) {
            override suspend fun call(args: NoArgs) = tools.listImports()
        },
        object : RecordedTool<CountArgs>(
            typeToken<CountArgs>(),
            COUNT_EVENTS,
            "Counts Events, optionally of one kind, in a date range, from one Source, grouped by Day or Month.",
            CountArgs.serializer(),
            state,
        ) {
            override suspend fun call(args: CountArgs) =
                tools.countEvents(args.kind, args.from, args.to, args.source, args.groupBy)
        },
        object : RecordedTool<TopArgs>(
            typeToken<TopArgs>(),
            TOP_ENTITIES,
            "Ranks the Entities of one kind named by the most Events of one kind, optionally in a date range.",
            TopArgs.serializer(),
            state,
        ) {
            override suspend fun call(args: TopArgs) =
                tools.topEntities(args.kind, args.entityKind, args.count, args.from, args.to)
        },
        object : RecordedTool<TimelineArgs>(
            typeToken<TimelineArgs>(),
            ENTITY_TIMELINE,
            "Everything the Library holds about one Entity: its counts, its newest Events and its Claims.",
            TimelineArgs.serializer(),
            state,
        ) {
            override suspend fun call(args: TimelineArgs) = tools.entityTimeline(args.entityId, args.limit)
        },
    )

/** The Tools that look for something: words, the same name across Sources, a Gap report, a chart. */
private fun lookingTools(
    tools: LibraryTools,
    state: RunState,
): List<RecordedTool<*>> =
    listOf(
        object : RecordedTool<SearchArgs>(
            typeToken<SearchArgs>(),
            SEARCH_TEXT,
            "Finds the Events and Claims whose text contains the given words.",
            SearchArgs.serializer(),
            state,
        ) {
            override suspend fun call(args: SearchArgs) = tools.searchText(args.text, args.limit)
        },
        object : RecordedTool<MatchArgs>(
            typeToken<MatchArgs>(),
            FIND_MATCHES,
            "Finds pairs of Entities of one kind from different Sources that share a normalized name.",
            MatchArgs.serializer(),
            state,
        ) {
            override suspend fun call(args: MatchArgs) = tools.findMatches(args.kind)
        },
        object : RecordedTool<GapArgs>(
            typeToken<GapArgs>(),
            GAP_REPORT,
            "The Gap report of one Import: for each GDPR Article 15 item, whether the Export covers it, and the " +
                "evidence.",
            GapArgs.serializer(),
            state,
        ) {
            override suspend fun call(args: GapArgs) = tools.gapReport(args.importId)
        },
        object : RecordedTool<ChartArgs>(
            typeToken<ChartArgs>(),
            CHART_SPEC,
            "Turns the latest countEvents series or topEntities ranking into a chart the user sees under the answer.",
            ChartArgs.serializer(),
            state,
        ) {
            override suspend fun call(args: ChartArgs): Result<ToolOutput, ToolError> =
                latestOutput(args.ofTool).andThen { output -> tools.chartSpec(output) }

            private fun latestOutput(tool: String): Result<ToolOutput, ToolError> =
                state.latest[tool]?.let { Result.Success(it) } ?: Result.Error(ToolError.NoOutputToChart(tool))
        },
    )
