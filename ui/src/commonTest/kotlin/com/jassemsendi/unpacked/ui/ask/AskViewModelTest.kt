package com.jassemsendi.unpacked.ui.ask

import app.cash.turbine.test
import com.jassemsendi.unpacked.agent.AgentUnavailable
import com.jassemsendi.unpacked.agent.Answer
import com.jassemsendi.unpacked.agent.AskError
import com.jassemsendi.unpacked.agent.AskOutcome
import com.jassemsendi.unpacked.agent.ChartKind
import com.jassemsendi.unpacked.agent.ChartPoint
import com.jassemsendi.unpacked.agent.ChartSpec
import com.jassemsendi.unpacked.agent.Citation
import com.jassemsendi.unpacked.agent.CloudPayload
import com.jassemsendi.unpacked.agent.ModelInfo
import com.jassemsendi.unpacked.agent.ModelLocality
import com.jassemsendi.unpacked.agent.PayloadMessage
import com.jassemsendi.unpacked.agent.TraceStep
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.ui.home.FakeClaimRepository
import com.jassemsendi.unpacked.ui.home.FakeEventRepository
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.advertiser
import com.jassemsendi.unpacked.ui.home.event
import com.jassemsendi.unpacked.ui.home.provenanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Seam 5: the Ask screen's states, asking, answering, flagged and unavailable, driven against a fake agent. */
class AskViewModelTest {
    private val courier = advertiser("Example Courier")
    private val ad = event(EventKind.AdShown, minutesAfterFirst = 0, entities = listOf(courier))
    private val holdsContact =
        Claim(
            kind = ClaimKind.AdvertiserHoldsContact,
            rawKind = RawKind("advertisers"),
            provenance = provenanceOf("ads_information/advertisers.json", 0),
            entities = listOf(courier),
            attributes = mapOf("Advertiser" to "Example Courier"),
        )
    private val events = FakeEventRepository(ad)
    private val claims = FakeClaimRepository(holdsContact)
    private val local = ModelInfo("qwen3:8b", ModelLocality.Local)
    private val countStep =
        TraceStep(1, "countEvents", """{"kind":"AdShown"}""", """{"total":1,"cited":["[1]"]}""", false)

    private fun answer(
        text: String,
        unverified: List<String> = emptyList(),
        chart: ChartSpec? = null,
    ) = Answer(
        text = text,
        citations = listOf(Citation(1, ad.provenance)),
        trace = listOf(countStep),
        unverifiedNumbers = unverified,
        chart = chart,
        model = local,
    )

    private fun viewModel(agent: FakeAskAgent) = AskViewModel(agent, events, claims, TimeZone.UTC)

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun theScreenOpensReadyWhenAModelAnswersAndMarksItLocal() =
        runTest {
            val viewModel = viewModel(FakeAskAgent())

            assertEquals(ModelUi.Ready("local · qwen3:8b", ModelLocality.Local), viewModel.state.value.model)
        }

    @Test
    fun withNoModelTheScreenSaysWhyAndPointsAtTheCardsAndTheGapReport() =
        runTest {
            val agent = FakeAskAgent(Result.Error(AgentUnavailable.OllamaNotRunning("http://localhost:11434")))
            val viewModel = viewModel(agent)

            val off = assertIs<ModelUi.Off>(viewModel.state.value.model)
            assertTrue(off.message.contains("http://localhost:11434"))
            assertTrue(off.message.contains("Insight cards") && off.message.contains("Gap report"))

            agent.availability = Result.Success(local)
            viewModel.onAction(AskAction.CheckModel)
            assertEquals(ModelUi.Ready("local · qwen3:8b", ModelLocality.Local), viewModel.state.value.model)
        }

    @Test
    fun askingShowsTheAnswerWithItsCitationsTraceAndMarker() =
        runTest {
            val agent =
                FakeAskAgent(answer = { Result.Success(AskOutcome.Answered(answer("You were shown 1 ad [1]."))) })
            val viewModel = viewModel(agent)
            agent.gate = CompletableDeferred()

            viewModel.state.test {
                skipItems(1)
                viewModel.onAction(AskAction.QuestionChanged("How many ads?"))
                assertEquals("How many ads?", awaitItem().question)
                viewModel.onAction(AskAction.Ask)
                assertTrue(awaitItem().isAsking)
                agent.gate?.complete(Unit)
                val answered = awaitItem()
                assertFalse(answered.isAsking)
                val shown = assertIs<OutcomeUi.Answered>(answered.outcome).answer
                assertEquals("You were shown 1 ad [1].", shown.text)
                assertEquals(listOf(CitationUi(1, ad.provenance)), shown.citations)
                assertEquals(
                    listOf(TraceStepUi(1, "countEvents", countStep.input, countStep.output, false)),
                    shown.trace,
                )
                assertEquals("local · qwen3:8b", shown.modelMarker)
                assertTrue(shown.isVerified)
                assertNull(shown.chart)
            }
            assertEquals(listOf("How many ads?"), agent.questions)
        }

    @Test
    fun aBlankQuestionIsNotAsked() =
        runTest {
            val agent = FakeAskAgent()
            val viewModel = viewModel(agent)

            viewModel.onAction(AskAction.QuestionChanged("   "))
            viewModel.onAction(AskAction.Ask)

            assertEquals(emptyList(), agent.questions)
            assertFalse(viewModel.state.value.isAsking)
        }

    @Test
    fun anAnswerWithUnverifiedNumbersIsShownFlagged() =
        runTest {
            val agent =
                FakeAskAgent(answer = {
                    Result.Success(AskOutcome.Answered(answer("You were shown 99 ads [1].", unverified = listOf("99"))))
                })
            val viewModel = viewModel(agent)

            viewModel.onAction(AskAction.QuestionChanged("How many ads?"))
            viewModel.onAction(AskAction.Ask)

            val shown = assertIs<OutcomeUi.Answered>(viewModel.state.value.outcome).answer
            assertFalse(shown.isVerified)
            assertEquals(listOf("99"), shown.unverifiedNumbers)
        }

    @Test
    fun aChartComesBackWithItsAxesAndTheToolsOwnNumbers() =
        runTest {
            val points = listOf(ChartPoint("A", 4), ChartPoint("B", 1))
            val chart = ChartSpec(ChartKind.Bars, "Advertisers", "AdShown Events", "Advertiser", points, emptyList())
            val agent =
                FakeAskAgent(answer = { Result.Success(AskOutcome.Answered(answer("A led [1].", chart = chart))) })
            val viewModel = viewModel(agent)

            viewModel.onAction(AskAction.QuestionChanged("Who?"))
            viewModel.onAction(AskAction.Ask)

            val shown = assertIs<OutcomeUi.Answered>(viewModel.state.value.outcome).answer
            val rows = listOf(ChartRowUi("A", 4), ChartRowUi("B", 1))
            assertEquals(
                ChartUi(ChartKind.Bars, "Advertisers", "AdShown Events", "Advertiser", rows, top = 4),
                shown.chart,
            )
        }

    @Test
    fun aChartOfNothingButZerosHasNoTopToItsValueAxis() {
        val points = listOf(ChartPoint("2026-09", 0), ChartPoint("2026-10", 0))
        val chart = ChartSpec(ChartKind.TimeSeries, "Logins per month", "Login", "Month", points, emptyList())

        val shown = chart.toUi()

        assertEquals(0, shown.top, "no number the Tool never gave is drawn")
        assertEquals(ChartKind.TimeSeries, shown.kind)
    }

    @Test
    fun aModelStoppedAtTheStepCapShowsTheBoundedStepsMessageWithItsTrace() =
        runTest {
            val agent =
                FakeAskAgent(answer = { Result.Success(AskOutcome.StepsExhausted(8, listOf(countStep), local)) })
            val viewModel = viewModel(agent)

            viewModel.onAction(AskAction.QuestionChanged("Everything?"))
            viewModel.onAction(AskAction.Ask)

            val stopped = assertIs<OutcomeUi.Stopped>(viewModel.state.value.outcome)
            assertTrue(stopped.message.contains("8 calls"))
            assertEquals(listOf(1), stopped.trace.map { it.number })
        }

    @Test
    fun aCloudModelIsMarkedCloudAndSaidToLeaveTheDevice() =
        runTest {
            val viewModel = viewModel(FakeAskAgent(Result.Success(ModelInfo("gemini-2.5-flash", ModelLocality.Cloud))))

            assertEquals(ModelUi.Ready("cloud · gemini-2.5-flash", ModelLocality.Cloud), viewModel.state.value.model)
        }

    @Test
    fun aCloudQuestionWaitsInThePayloadInspectorAndGoesOnWhenSent() =
        runTest {
            val cloud = ModelInfo("gemini-2.5-flash", ModelLocality.Cloud)
            val agent =
                FakeAskAgent(
                    availability = Result.Success(cloud),
                    answer = { Result.Success(AskOutcome.Answered(answer("1 ad [1].").copy(model = cloud))) },
                )
            val question = PayloadMessage("user", "How many ads?")
            agent.payload = CloudPayload("gemini-2.5-flash", listOf(question), emptyList())
            val viewModel = viewModel(agent)

            viewModel.onAction(AskAction.QuestionChanged("How many ads?"))
            viewModel.onAction(AskAction.Ask)

            val waiting = viewModel.state.value
            assertTrue(waiting.isAsking)
            assertEquals("gemini-2.5-flash", waiting.payload?.model)
            assertTrue(
                waiting.payload
                    ?.text
                    .orEmpty()
                    .contains("How many ads?"),
                "the exact request is shown",
            )
            assertNull(waiting.outcome)

            viewModel.onAction(AskAction.SendPayload)

            val answered = viewModel.state.value
            assertNull(answered.payload)
            assertFalse(answered.isAsking)
            assertEquals("cloud · gemini-2.5-flash", assertIs<OutcomeUi.Answered>(answered.outcome).answer.modelMarker)
        }

    @Test
    fun refusingThePayloadEndsTheQuestionWithNothingSent() =
        runTest {
            val agent = FakeAskAgent(answer = { error("the answer must not be reached") })
            agent.payload = CloudPayload("gemini-2.5-flash", emptyList(), emptyList())
            val viewModel = viewModel(agent)

            viewModel.onAction(AskAction.QuestionChanged("How many ads?"))
            viewModel.onAction(AskAction.Ask)
            viewModel.onAction(AskAction.RefusePayload)

            val state = viewModel.state.value
            assertNull(state.payload)
            assertFalse(state.isAsking)
            assertNull(state.outcome)
            assertEquals(AskError.PayloadRefused.toMessage(), state.error)
        }

    @Test
    fun everyUnavailableReasonHasItsOwnExplanationThatPointsAtTheCardsAndTheGapReport() {
        val reasons =
            listOf(
                AgentUnavailable.NoProvider,
                AgentUnavailable.OllamaNotRunning("http://localhost:11434"),
                AgentUnavailable.NoToolModel("http://localhost:11434", emptyList()),
                AgentUnavailable.Off,
                AgentUnavailable.NoKey,
                AgentUnavailable.KeyUnreadable,
                AgentUnavailable.KeyRejected,
                AgentUnavailable.QuotaExceeded,
                AgentUnavailable.NoCloudModel("Google lists no Gemini Flash model: gemini-3.1-pro"),
                AgentUnavailable.CloudUnreachable("no route to host"),
            )

        val messages = reasons.map { it.toMessage() }

        assertEquals(messages.size, messages.toSet().size, "each reason reads differently")
        assertTrue(messages.all { it.contains("Insight cards") && it.contains("Gap report") })
        assertTrue(AgentUnavailable.NoKey.toMessage().contains("Settings"))
        assertTrue(AgentUnavailable.QuotaExceeded.toMessage().contains("quota"))
    }

    @Test
    fun aModelThatWentAwayIsReportedWhereTheModelCheckIs() =
        runTest {
            val agent =
                FakeAskAgent(answer = {
                    val none = AgentUnavailable.NoToolModel("http://localhost:11434", listOf("gemma3:4b"))
                    Result.Error(AskError.Unavailable(none))
                })
            val viewModel = viewModel(agent)

            viewModel.onAction(AskAction.QuestionChanged("How many ads?"))
            viewModel.onAction(AskAction.Ask)

            val off = assertIs<ModelUi.Off>(viewModel.state.value.model)
            assertTrue(off.message.contains("gemma3:4b"))
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun aModelThatFailsMidWayIsAnErrorUnderTheQuestion() =
        runTest {
            val agent = FakeAskAgent(answer = { Result.Error(AskError.ModelFailed("connection reset")) })
            val viewModel = viewModel(agent)

            viewModel.onAction(AskAction.QuestionChanged("How many ads?"))
            viewModel.onAction(AskAction.Ask)

            assertEquals("The model stopped before answering: connection reset", viewModel.state.value.error)
            assertFalse(viewModel.state.value.isAsking)
        }

    @Test
    fun aCitationOpensItsRecordWhetherEventOrClaim() =
        runTest {
            val viewModel = viewModel(FakeAskAgent())

            viewModel.onAction(AskAction.OpenCitation(ad.provenance))
            assertEquals(
                RecordRef.Event(ad.provenance),
                viewModel.state.value.detail
                    ?.ref,
            )

            viewModel.onAction(AskAction.OpenCitation(holdsContact.provenance))
            assertEquals(
                RecordRef.Claim(holdsContact.provenance),
                viewModel.state.value.detail
                    ?.ref,
            )

            viewModel.onAction(AskAction.CloseDetail)
            assertNull(viewModel.state.value.detail)
        }

    @Test
    fun aCitationToNothingSaysSo() =
        runTest {
            val viewModel = viewModel(FakeAskAgent())

            viewModel.onAction(AskAction.OpenCitation(provenanceOf("gone.json", 9)))

            assertNull(viewModel.state.value.detail)
            assertEquals("That is no longer in your library.", viewModel.state.value.error)
        }
}
