package com.jassemsendi.unpacked.agent

import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Seam 4: the Koog strategy, step cap, validator and retry against a scripted model; no live model anywhere. */
class KoogAskAgentTest {
    private val library = TestLibrary()
    private val tools = with(library) { LibraryTools(imports, events, claims, entities, search, zone) }
    private val adProvenances =
        listOf(library.hotelsAd, library.courierAdAtMidnight, library.courierAd, library.googleAd)
            .map { it.provenance }
            .toSet()

    private fun agent(
        model: ScriptedModel,
        stepCap: Int = KoogAskAgent.DEFAULT_STEP_CAP,
    ) = KoogAskAgent(tools, FakeModelProvider(model), library.zone, stepCap)

    @Test
    fun aQuestionYieldsAnAnswerWhoseCitationsResolveAndWhoseTraceListsTheCallsInOrder() =
        runTest {
            val model =
                ScriptedModel(
                    says("Counts"),
                    calls(COUNT_EVENTS, """{"kind":"AdShown"}"""),
                    calls(TOP_ENTITIES, """{"kind":"AdShown","entityKind":"Advertiser","count":2}"""),
                    says("You were shown 4 ads [1]; Example Courier placed 2 of them [2]."),
                )

            val outcome = agent(model).ask("How many ads did I see?").orFail()

            val answer = assertIs<AskOutcome.Answered>(outcome).answer
            assertTrue(answer.isVerified, "numbers 4 and 2 are both in the Tool outputs")
            assertEquals(listOf(1, 2), answer.citations.map { it.marker })
            assertTrue(answer.citations.all { it.provenance in adProvenances }, "every citation opens an ad Record")
            assertEquals(listOf(COUNT_EVENTS, TOP_ENTITIES), answer.trace.map { it.tool })
            assertEquals(listOf(1, 2), answer.trace.map { it.number })
            assertEquals("""{"kind":"AdShown"}""", answer.trace[0].input, "the input as the model wrote it")
            assertTrue(answer.trace.none { it.failed })
            assertTrue(answer.trace[0].output.contains("\"total\":4"), answer.trace[0].output)
            assertEquals(ModelInfo("scripted", ModelLocality.Local), answer.model)
            assertEquals(null, answer.chart)
        }

    @Test
    fun theClassificationChoosesTheToolsTheModelIsOffered() =
        runTest {
            val model = ScriptedModel(says("Coverage"), says("There are two Imports."))

            agent(model).ask("What do my exports cover?").orFail()

            assertEquals(emptyList(), model.calls[0].tools, "classification runs without Tools")
            assertTrue(model.calls[0].lastUserText.contains("What do my exports cover?"))
            assertEquals(
                setOf(LIST_IMPORTS, GAP_REPORT, COUNT_EVENTS),
                model.calls[1]
                    .tools
                    .map { it.name }
                    .toSet(),
            )
            assertEquals("What do my exports cover?", model.calls[1].lastUserText, "the classification leaves no trace")
        }

    @Test
    fun anUnclassifiableQuestionGetsEveryTool() =
        runTest {
            val model = ScriptedModel(says("I am not sure."), says("Nothing to say."))

            agent(model).ask("Tell me something.").orFail()

            assertEquals(8, model.calls[1].tools.size)
        }

    @Test
    fun aNumberAbsentFromEveryToolOutputTriggersOneRetryThenFlagsTheAnswer() =
        runTest {
            val model =
                ScriptedModel(
                    says("Counts"),
                    calls(COUNT_EVENTS, """{"kind":"AdShown"}"""),
                    says("You were shown 99 ads [1]."),
                    says("I insist: 99 ads [1], 7 of them at night."),
                )

            val outcome = agent(model).ask("How many ads?").orFail()

            val answer = assertIs<AskOutcome.Answered>(outcome).answer
            assertFalse(answer.isVerified)
            assertEquals(listOf("99", "7"), answer.unverifiedNumbers)
            assertEquals("I insist: 99 ads [1], 7 of them at night.", answer.text)
            assertEquals(4, model.calls.size, "classify, ask, send results, one retry")
            assertEquals(correctionPrompt(listOf("99")), model.calls[3].lastUserText)
        }

    @Test
    fun aRetryThatCorrectsTheNumbersIsVerified() =
        runTest {
            val model =
                ScriptedModel(
                    says("Counts"),
                    calls(COUNT_EVENTS, """{"kind":"AdShown"}"""),
                    says("You were shown 99 ads [1]."),
                    says("You were shown 4 ads [1]."),
                )

            val answer = assertIs<AskOutcome.Answered>(agent(model).ask("How many ads?").orFail()).answer

            assertTrue(answer.isVerified)
            assertEquals("You were shown 4 ads [1].", answer.text)
        }

    @Test
    fun aRetryMayCallToolsOnceMoreButIsNotRetriedAgain() =
        runTest {
            val model =
                ScriptedModel(
                    says("Counts"),
                    calls(COUNT_EVENTS, """{"kind":"AdShown"}"""),
                    says("You were shown 99 ads [1]."),
                    calls(COUNT_EVENTS, """{"kind":"Login"}"""),
                    says("4 ads [1] and 55 logins."),
                )

            val answer = assertIs<AskOutcome.Answered>(agent(model).ask("How many ads?").orFail()).answer

            assertEquals(listOf(COUNT_EVENTS, COUNT_EVENTS), answer.trace.map { it.tool })
            assertEquals(listOf("55"), answer.unverifiedNumbers, "the second wrong answer is flagged, not retried")
            assertEquals(5, model.calls.size)
        }

    @Test
    fun aModelThatKeepsCallingToolsStopsAtTheStepCap() =
        runTest {
            val model = ScriptedModel(says("Counts"), calls(COUNT_EVENTS, """{"kind":"AdShown"}"""))

            val outcome = agent(model, stepCap = 3).ask("Count forever").orFail()

            val stopped = assertIs<AskOutcome.StepsExhausted>(outcome)
            assertEquals(3, stopped.stepCap)
            assertEquals(listOf(1, 2, 3), stopped.trace.map { it.number })
            assertEquals(ModelInfo("scripted", ModelLocality.Local), stopped.model)
            assertEquals(1 + 1 + 3, model.calls.size, "classify, ask, a send per step, the last one offering no Tool")
            assertEquals(emptyList(), model.calls.last().tools, "at the cap the model is asked to answer, not to call")
        }

    @Test
    fun aModelThatSpendsExactlyTheCapAndThenAnswersIsAnswered() =
        runTest {
            val model =
                ScriptedModel(
                    says("Counts"),
                    calls(COUNT_EVENTS, """{"kind":"AdShown"}"""),
                    calls(COUNT_EVENTS, """{"kind":"Login"}"""),
                    says("4 ads [1] and 1 login."),
                )

            val outcome = agent(model, stepCap = 2).ask("Ads and logins?").orFail()

            val answer = assertIs<AskOutcome.Answered>(outcome).answer
            assertEquals(2, answer.trace.size)
            assertTrue(answer.isVerified)
        }

    @Test
    fun aTurnWithSeveralCallsNeverTakesTheTraceOverTheCap() =
        runTest {
            val twoCalls =
                Message.Assistant(
                    listOf(
                        MessagePart.Tool.Call(id = "a", tool = COUNT_EVENTS, args = """{"kind":"AdShown"}"""),
                        MessagePart.Tool.Call(id = "b", tool = COUNT_EVENTS, args = """{"kind":"Login"}"""),
                    ),
                    ResponseMetaInfo(Instant.DISTANT_PAST),
                )
            val model = ScriptedModel(says("Counts"), twoCalls, twoCalls, says("4 ads [1]."))

            val answer = assertIs<AskOutcome.Answered>(agent(model, stepCap = 3).ask("Both?").orFail()).answer

            assertEquals(listOf(1, 2, 3), answer.trace.map { it.number }, "the fourth call was refused unrecorded")
        }

    @Test
    fun aNumberInARefusedCallIsStillBackedByTheTrace() =
        runTest {
            val model =
                ScriptedModel(
                    says("Coverage"),
                    calls(GAP_REPORT, """{"importId":5}"""),
                    says("There is no Import with the id 5."),
                )

            val answer = assertIs<AskOutcome.Answered>(agent(model).ask("Gaps of import 5?").orFail()).answer

            assertTrue(answer.trace.single().failed)
            assertTrue(answer.isVerified, "the model repeats what the Tool said, refusal included")
        }

    @Test
    fun aModelThatAnswersWithNothingIsReportedAsFailed() =
        runTest {
            val model = ScriptedModel(says("Counts"), says("   "))

            val result = agent(model).ask("How many ads?")

            assertEquals(Result.Error(AskError.ModelFailed("The model answered with nothing.")), result)
        }

    @Test
    fun aFailedToolCallIsInTheTraceAndTheModelHearsWhy() =
        runTest {
            val model =
                ScriptedModel(
                    says("Entities"),
                    calls(TOP_ENTITIES, """{"kind":"Adverts","entityKind":"Advertiser"}"""),
                    says("I could not find a kind called Adverts."),
                )

            val answer = assertIs<AskOutcome.Answered>(agent(model).ask("Top advertisers?").orFail()).answer

            val step = answer.trace.single()
            assertTrue(step.failed)
            assertEquals(ToolError.UnknownKind("Adverts").describe(), step.output)
            assertTrue(answer.isVerified, "an answer with no numbers has nothing to verify")
        }

    @Test
    fun aChartAskedForComesBackWithTheAnswer() =
        runTest {
            val model =
                ScriptedModel(
                    says("Counts"),
                    calls(COUNT_EVENTS, """{"kind":"AdShown","groupBy":"Month"}"""),
                    calls(CHART_SPEC, """{"ofTool":"countEvents"}"""),
                    says("Ads per month: 1 in September and 3 in October [1]."),
                )

            val answer = assertIs<AskOutcome.Answered>(agent(model).ask("Ads per month?").orFail()).answer

            assertEquals(ChartKind.TimeSeries, answer.chart?.kind)
            assertEquals(listOf("2026-09" to 1, "2026-10" to 3), answer.chart?.points?.map { it.label to it.value })
            assertTrue(answer.isVerified)
        }

    @Test
    fun aBlankQuestionIsRefusedBeforeAnyModelIsAsked() =
        runTest {
            val provider = FakeModelProvider(ScriptedModel(says("x")))

            val result = KoogAskAgent(tools, provider, library.zone).ask("   ")

            assertEquals(Result.Error(AskError.BlankQuestion), result)
            assertEquals(0, provider.connections)
        }

    @Test
    fun withNoModelTheQuestionIsAnsweredWithWhyTheAgentIsOff() =
        runTest {
            val agent = KoogAskAgent(tools, NoModelProvider, library.zone)

            assertEquals(Result.Error(AskError.Unavailable(AgentUnavailable.NoProvider)), agent.ask("Anything?"))
            assertEquals(Result.Error(AgentUnavailable.NoProvider), agent.available())
        }

    @Test
    fun aModelThatStopsMidWayIsReportedAsFailed() =
        runTest {
            val agent = KoogAskAgent(tools, FakeModelProvider(FailingModel("connection reset")), library.zone)

            assertEquals(Result.Error(AskError.ModelFailed("connection reset")), agent.ask("How many ads?"))
        }

    @Test
    fun availabilityNamesTheModel() =
        runTest {
            val agent = agent(ScriptedModel(says("x")))

            assertEquals(Result.Success(ModelInfo("scripted", ModelLocality.Local)), agent.available())
        }

    @Test
    fun aCloudModelGetsNothingWhenTheUserRefusesThePayload() =
        runTest {
            val model = ScriptedModel(says("Counts"), says("4 ads."))
            val agent = KoogAskAgent(tools, FakeModelProvider(model, locality = ModelLocality.Cloud), library.zone)

            val result = agent.ask("How many ads?") { false }

            assertEquals(Result.Error(AskError.PayloadRefused), result)
            assertEquals(0, model.calls.size, "nothing left the device")
        }

    @Test
    fun theInspectorIsAskedOnceAndApprovalCoversTheWholeQuestion() =
        runTest {
            val model =
                ScriptedModel(
                    says("Counts"),
                    calls(COUNT_EVENTS, """{"kind":"AdShown"}"""),
                    says("You were shown 4 ads [1]."),
                )
            val agent = KoogAskAgent(tools, FakeModelProvider(model, locality = ModelLocality.Cloud), library.zone)
            val shown = mutableListOf<CloudPayload>()

            val outcome =
                agent
                    .ask("How many ads?") { payload ->
                        shown += payload
                        true
                    }.orFail()

            assertEquals(1, shown.size)
            assertEquals(3, model.calls.size)
            val answered = assertIs<AskOutcome.Answered>(outcome)
            assertEquals(ModelInfo("scripted", ModelLocality.Cloud), answered.answer.model)
        }

    @Test
    fun aLocalModelNeverAsksTheInspector() =
        runTest {
            val model = ScriptedModel(says("Counts"), says("Nothing to count."))

            agent(model).ask("How many ads?") { error("a local question has no payload to inspect") }.orFail()

            assertEquals(2, model.calls.size)
        }

    @Test
    fun aCloudRefusalMidQuestionIsReportedAsTheAgentBeingUnavailable() =
        runTest {
            val quota =
                LLMClientException(
                    "Google",
                    "Error from client",
                    KoogHttpClientException("google", 429, "", "", null),
                )
            val provider = FakeModelProvider(ThrowingModel(quota), locality = ModelLocality.Cloud)

            val result = KoogAskAgent(tools, provider, library.zone).ask("How many ads?")

            assertEquals(Result.Error(AskError.Unavailable(AgentUnavailable.QuotaExceeded)), result)
        }

    @Test
    fun theSystemPromptNamesTodayAndTheZone() {
        val fixed = kotlin.time.Clock.System
        val prompt = systemPrompt(fixed, library.zone)

        assertTrue(prompt.contains("Africa/Tunis"))
        assertTrue(prompt.contains(EventKind.AdShown.name).not(), "the prompt teaches citing, the Tools teach kinds")
    }
}
