package com.jassemsendi.unpacked.ui.ask

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import com.jassemsendi.unpacked.agent.ChartKind
import com.jassemsendi.unpacked.agent.ModelLocality
import kotlin.test.Test

/** The Ask screen on the desktop: a charted answer shows its chart with both axes labelled, an uncharted one none. */
@OptIn(ExperimentalTestApi::class)
class AskScreenChartTest {
    private val trace =
        listOf(
            TraceStepUi(
                number = 1,
                tool = "countEvents",
                input = """{"kind":"AdShown","groupBy":"Month"}""",
                output = """{"total":4,"series":[{"start":"2026-09-01","count":1},{"start":"2026-10-01","count":3}]}""",
                failed = false,
            ),
        )

    private fun answered(chart: ChartUi?) =
        AskState(
            question = "Ads per month?",
            model = ModelUi.Ready("local · qwen3:8b", ModelLocality.Local),
            outcome =
                OutcomeUi.Answered(
                    AnswerUi(
                        text = "1 in September and 3 in October [1].",
                        citations = emptyList(),
                        trace = trace,
                        modelMarker = "local · qwen3:8b",
                        unverifiedNumbers = emptyList(),
                        chart = chart,
                    ),
                ),
        )

    private val perMonth =
        ChartUi(
            kind = ChartKind.TimeSeries,
            title = "AdShown per month",
            valueLabel = "AdShown",
            pointLabel = "Month",
            rows = listOf(ChartRowUi("2026-09", 1), ChartRowUi("2026-10", 3)),
            top = 3,
        )

    private val topAdvertisers =
        ChartUi(
            kind = ChartKind.Bars,
            title = "Advertiser named most by AdShown Events",
            valueLabel = "AdShown Events",
            pointLabel = "Advertiser",
            rows = listOf(ChartRowUi("Example Courier", 2), ChartRowUi("Example Hotels", 1)),
            top = 2,
        )

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: AskState) {
        setContent {
            MaterialTheme {
                AskScreen(
                    state = state,
                    onAction = {},
                    destinations = AskDestinations(onBack = {}, onGapReport = {}, onSettings = {}),
                )
            }
        }
        onNodeWithTag(ASK_LIST_TAG).performScrollToNode(hasTestTag(TRACE_TAG))
    }

    @Test
    fun aPerMonthAnswerShowsALineChartWithItsAxesAndEveryPoint() =
        runComposeUiTest {
            show(answered(perMonth))

            onNodeWithTag(CHART_TAG).assertIsDisplayed()
            onNodeWithText("AdShown per month").assertIsDisplayed()
            onNodeWithText("AdShown").assertIsDisplayed()
            onNodeWithText("Month").assertIsDisplayed()
            onNodeWithText("2026-09").assertIsDisplayed()
            onNodeWithText("2026-10").assertIsDisplayed()
            onNodeWithText("3").assertIsDisplayed()
            onNodeWithContentDescription("Line chart: 2026-09: 1, 2026-10: 3").assertIsDisplayed()
        }

    @Test
    fun aTopAdvertisersAnswerShowsBarsNamedAndCounted() =
        runComposeUiTest {
            show(answered(topAdvertisers))

            onNodeWithTag(CHART_TAG).assertIsDisplayed()
            onNodeWithText("Advertiser").assertIsDisplayed()
            onNodeWithText("AdShown Events").assertIsDisplayed()
            onNodeWithText("Example Courier").assertIsDisplayed()
            onNodeWithText("Example Hotels").assertIsDisplayed()
            onNodeWithText("1").assertIsDisplayed()
            onAllNodesWithText("2").assertCountEquals(2) // the longest bar's count, and the top of the value axis
            onNodeWithContentDescription("Bar chart: Example Courier: 2, Example Hotels: 1").assertIsDisplayed()
        }

    @Test
    fun anAnswerWithoutAChartShowsNone() =
        runComposeUiTest {
            show(answered(chart = null))

            onNodeWithTag(CHART_TAG).assertDoesNotExist()
            onNodeWithTag(TRACE_TAG).assertIsDisplayed()
        }
}
