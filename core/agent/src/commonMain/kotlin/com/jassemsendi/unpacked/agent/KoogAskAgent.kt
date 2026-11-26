package com.jassemsendi.unpacked.agent

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.ToolCalls
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.andThen
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The Ask agent on Koog (spec stories 34, 35, 43, 44): one graph strategy per question that classifies the question
 * to choose its Tools, lets the model call them under a step cap, then validates that every number in the answer
 * appears in some Tool output, retrying once before the answer is flagged. The model comes from the [provider] at each
 * question, so a model that stopped is noticed and one that started is used.
 */
public class KoogAskAgent(
    private val tools: LibraryTools,
    private val provider: ModelProvider,
    private val timeZone: TimeZone,
    private val stepCap: Int = DEFAULT_STEP_CAP,
    private val clock: Clock = Clock.System,
) : AskAgent {
    override suspend fun available(): Result<ModelInfo, AgentUnavailable> =
        when (val connected = provider.connect()) {
            is Result.Success -> Result.Success(connected.data.info)
            is Result.Error -> Result.Error(connected.error)
        }

    override suspend fun ask(
        question: String,
        inspector: PayloadInspector,
    ): Result<AskOutcome, AskError> {
        val asked = question.trim()
        if (asked.isEmpty()) return Result.Error(AskError.BlankQuestion)
        return when (val connected = provider.connect()) {
            is Result.Error -> Result.Error(AskError.Unavailable(connected.error))
            is Result.Success -> run(asked, connected.data, inspector)
        }
    }

    private suspend fun run(
        question: String,
        connected: ConnectedModel,
        inspector: PayloadInspector,
    ): Result<AskOutcome, AskError> {
        val state = RunState(stepCap, timeZone)
        val wrapped = agentTools(tools, state)
        val isCloud = connected.info.locality == ModelLocality.Cloud
        // A cloud model's first request is shown before it leaves the device (ADR 0006); a local one just runs.
        val executor = if (isCloud) InspectedExecutor(connected.executor, inspector) else connected.executor
        val agent =
            GraphAIAgent(
                promptExecutor = executor,
                agentConfig =
                    AIAgentConfig(
                        prompt = prompt("unpacked-ask") { system(systemPrompt(clock, timeZone)) },
                        model = connected.model,
                        maxAgentIterations = stepCap * NODES_PER_STEP + FIXED_NODES,
                    ),
                strategy = askStrategy(state, wrapped),
                toolRegistry = ToolRegistry { wrapped.forEach { tool(it) } },
            )
        val ending: Result<Ending, AskError> =
            try {
                Result.Success(agent.run(question))
            } catch (e: CancellationException) {
                throw e
            } catch (_: PayloadRefusedException) {
                Result.Error(AskError.PayloadRefused)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // A cloud that refuses the key or the quota mid-question is the agent going away, not a broken answer.
                val refusal = if (isCloud) cloudFailure(e) else null
                if (refusal != null) {
                    Result.Error(AskError.Unavailable(refusal))
                } else {
                    Result.Error(AskError.ModelFailed(e.message ?: "The model stopped without a reason."))
                }
            }
        return ending.andThen { outcome(it, state, connected.info) }
    }

    private fun outcome(
        ending: Ending,
        state: RunState,
        model: ModelInfo,
    ): Result<AskOutcome, AskError> =
        when (ending) {
            Ending.Capped -> {
                Result.Success(AskOutcome.StepsExhausted(stepCap, state.trace.toList(), model))
            }

            is Ending.Text -> {
                if (ending.text.isBlank()) {
                    Result.Error(AskError.ModelFailed("The model answered with nothing."))
                } else {
                    Result.Success(AskOutcome.Answered(answer(ending, state, model)))
                }
            }
        }

    private fun answer(
        ending: Ending.Text,
        state: RunState,
        model: ModelInfo,
    ): Answer =
        Answer(
            text = ending.text,
            citations = state.citations.citationsIn(ending.text),
            trace = state.trace.toList(),
            unverifiedNumbers = ending.unverified,
            chart = state.chart,
            model = model,
        )

    public companion object {
        /** How many Tool calls one question may spend (spec story 43). */
        public const val DEFAULT_STEP_CAP: Int = 8

        // Koog counts node visits: each Tool round visits the execute and the send node, and a question also visits
        // classify, ask, check and chart, plus the retry's own request. The Koog limit stays a backstop behind the
        // step cap.
        private const val NODES_PER_STEP = 2
        private const val FIXED_NODES = 9
    }
}

/** How a run of the strategy ends: with text to validate and show, or stopped for asking for Tools past the cap. */
internal sealed interface Ending {
    data class Text(
        val text: String,
        val unverified: List<String>,
    ) : Ending

    data object Capped : Ending
}

/** What the check node decided: the answer stands (verified or not), or the retry asked for more Tool calls. */
internal sealed interface Checked {
    data class Done(
        val text: String,
        val unverified: List<String>,
    ) : Checked

    data class MoreTools(
        val calls: List<MessagePart.Tool.Call>,
    ) : Checked
}

/**
 * The Tools a question may use once it is classified: the ones its kind of answer is built from, plus the ones a
 * follow-up in the same breath needs ("how many, and from whom"); `Any` is every Tool.
 */
internal enum class QuestionKind(
    val tools: Set<String>?,
) {
    Counts(setOf(COUNT_EVENTS, TOP_ENTITIES, CHART_SPEC, LIST_IMPORTS)),
    Entities(setOf(TOP_ENTITIES, ENTITY_TIMELINE, SEARCH_TEXT, FIND_MATCHES, CHART_SPEC)),
    Search(setOf(SEARCH_TEXT, ENTITY_TIMELINE, TOP_ENTITIES)),
    Matches(setOf(FIND_MATCHES, ENTITY_TIMELINE, SEARCH_TEXT)),
    Coverage(setOf(LIST_IMPORTS, GAP_REPORT, COUNT_EVENTS)),
    Any(null),
    ;

    companion object {
        /** The kind a classification reply names, [Any] when the reply names none. */
        fun of(reply: String): QuestionKind {
            val words = reply.lowercase()
            return entries.firstOrNull { it != Any && it.name.lowercase() in words } ?: Any
        }
    }
}

internal fun askStrategy(
    state: RunState,
    tools: List<RecordedTool<*>>,
): AIAgentGraphStrategy<String, Ending> =
    strategy("unpacked-ask") {
        val classify by node<String, String>("classify") { question ->
            llm.writeSession {
                val before = prompt
                appendPrompt { user(classificationPrompt(question)) }
                val reply = requestLLMWithoutTools()
                rewritePrompt { before }
                val kind = QuestionKind.of(reply.parts.filterIsInstance<MessagePart.Text>().joinToString { it.text })
                this.tools = tools.filter { kind.tools == null || it.name in kind.tools }.map { it.descriptor }
            }
            question
        }
        val ask by nodeLLMRequest("ask")
        val execute by nodeExecuteTools("execute")
        // Tool results go back to the model with the Tools still offered until the cap is spent, then without, so a
        // model that has what it needs answers, and one that still asks for a Tool is stopped (spec story 43).
        val send by node<ReceivedToolResults, Message.Assistant>("send") { results ->
            llm.writeSession {
                appendPrompt { user { results.toolResults.forEach { toolResult(it.toMessagePart()) } } }
                if (state.capReached) requestLLMWithoutTools() else requestLLM()
            }
        }
        val check by node<Message.Assistant, Checked>("check") { response -> checkAnswer(state, response, llm) }
        // An answer whose Tool outputs include a count over time or a ranking gets a chart of the newest one, unless
        // the model drew its own (spec story 36); the numbers are the Tool's, so nothing new needs validating.
        val chart by node<Checked.Done, Ending>("chart") { done ->
            state.chartLatest()
            Ending.Text(done.text, done.unverified)
        }

        edge(nodeStart forwardTo classify)
        edge(classify forwardTo ask)
        edge(ask forwardTo execute onToolCalls { true })
        edge(ask forwardTo check)
        edge(execute forwardTo send)
        edge(
            send forwardTo nodeFinish onCondition { reply -> state.capReached && reply.callsTools() } transformed { _ ->
                Ending.Capped
            },
        )
        edge(send forwardTo execute onToolCalls { true })
        edge(send forwardTo check)
        edge(
            check forwardTo execute onCondition { checked -> checked is Checked.MoreTools } transformed { checked ->
                ToolCalls((checked as Checked.MoreTools).calls)
            },
        )
        edge(
            check forwardTo chart onCondition { checked -> checked is Checked.Done } transformed { checked ->
                checked as Checked.Done
            },
        )
        edge(chart forwardTo nodeFinish)
    }

/**
 * The citation validator applied to one reply: a verified answer, or a first failure sent back to the model once with
 * the numbers it could not find; the second reply stands, flagged if it still fails, unless it asks for Tools.
 */
private suspend fun checkAnswer(
    state: RunState,
    response: Message.Assistant,
    llm: AIAgentLLMContext,
): Checked {
    val text = response.text()
    val unverified = unverifiedNumbers(text, state.outputs)
    if (unverified.isEmpty() || state.retried) return Checked.Done(text, unverified)
    state.retried = true
    val again =
        llm.writeSession {
            appendPrompt { user(correctionPrompt(unverified)) }
            requestLLM()
        }
    val calls = again.parts.filterIsInstance<MessagePart.Tool.Call>()
    return if (calls.isNotEmpty()) {
        Checked.MoreTools(calls)
    } else {
        again.text().let { retried -> Checked.Done(retried, unverifiedNumbers(retried, state.outputs)) }
    }
}

private fun Message.Assistant.callsTools(): Boolean = parts.any { it is MessagePart.Tool.Call }

private fun Message.Assistant.text(): String =
    parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }.trim()

internal fun systemPrompt(
    clock: Clock,
    timeZone: TimeZone,
): String {
    val today = clock.now().toLocalDateTime(timeZone).date
    return """
        You answer questions about one person's own data: the Library that Unpacked built from the Exports they
        downloaded from Instagram and Google. Today is $today. Times are in the person's zone, $timeZone.

        You know nothing about the Library except what the Tools return. Call Tools to find out; never guess.
        Every Tool output marks the records it cites with markers such as [3]. When you state a number or a fact,
        put the marker of the output or record it comes from right after it, like "4 ads [1]". Use only numbers
        that appear in Tool outputs, written as they appear. If a Tool reports an error, say what could not be
        found instead of inventing an answer. Answer in plain language, in a few sentences, without headings.
        A count grouped by day or month, or a ranking, is also shown to the person as a chart under your answer,
        so you need not list every point of it.
        """.trimIndent()
}

internal fun classificationPrompt(question: String): String =
    """
    Classify this question with one word, nothing else:
    Counts (how many, how often, per day or month), Entities (who, which advertisers, accounts, devices, places),
    Search (find a word or phrase), Matches (the same name across Instagram and Google), Coverage (what the Export
    holds or lacks, Imports, the Gap report), or Any when unsure.

    Question: $question
    """.trimIndent()

internal fun correctionPrompt(unverified: List<String>): String =
    "Your answer states numbers that appear in no Tool output: ${unverified.joinToString()}. Answer again using " +
        "only numbers from the Tool outputs, or call a Tool to get the number you need."
