package com.jassemsendi.unpacked.eval

import com.jassemsendi.unpacked.agent.Answer
import com.jassemsendi.unpacked.agent.AskError
import com.jassemsendi.unpacked.agent.AskOutcome
import com.jassemsendi.unpacked.agent.ModelInfo
import com.jassemsendi.unpacked.agent.ModelLocality
import com.jassemsendi.unpacked.model.Result
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How one answer is scored against the value the fixture says it should hold, and how twenty add up. */
class EvalScoreTest {
    private val model = ModelInfo("scripted", ModelLocality.Local)
    private val ads = EvalQuestion(EvalTopic.Counts, "How many ads was I shown?", Expected.Count(5))

    private fun answered(
        text: String,
        unverified: List<String> = emptyList(),
    ): Result<AskOutcome, AskError> =
        Result.Success(AskOutcome.Answered(Answer(text, emptyList(), emptyList(), unverified, null, model)))

    @Test
    fun aVerifiedAnswerThatStatesTheExpectedCountPasses() {
        assertEquals(EvalStatus.Pass, judge(ads, answered("You were shown 5 ads [1].")).status)
        assertEquals(EvalStatus.Pass, judge(ads, answered("Five ads: 5.0 in all [1].")).status, "compared canonically")
    }

    @Test
    fun aVerifiedAnswerWithAnotherCountFails() {
        val result = judge(ads, answered("You were shown 4 ads [1]."))

        assertEquals(EvalStatus.Fail, result.status)
        assertTrue(result.detail.contains("5"), result.detail)
    }

    @Test
    fun aDateIsNotMistakenForTheCountItContains() {
        val nine = EvalQuestion(EvalTopic.Counts, "How many views?", Expected.Count(9))

        assertEquals(EvalStatus.Fail, judge(nine, answered("Nothing on 2026-09-22 [1].")).status)
        assertEquals(EvalStatus.Fail, judge(nine, answered("Nothing at 09:15 [1].")).status, "nor a clock time")
    }

    @Test
    fun aFlaggedAnswerIsFlaggedEvenWhenItStatesTheExpectedCount() {
        val result = judge(ads, answered("You were shown 5 ads, 3 of them twice.", unverified = listOf("3")))

        assertEquals(EvalStatus.Flagged, result.status)
        assertTrue(result.detail.contains("3"), result.detail)
    }

    @Test
    fun wordsMatchWithoutRegardToCaseMarkersOrPunctuation() {
        val who = EvalQuestion(EvalTopic.TopEntities, "Who?", Expected.Words("Example Courier", "Example Club"))

        assertEquals(EvalStatus.Pass, judge(who, answered("It was example-courier [2], most often.")).status)
        assertEquals(EvalStatus.Pass, judge(who, answered("Example Club [1].")).status, "any alternative")
        assertEquals(EvalStatus.Fail, judge(who, answered("Example Hotels [1].")).status)
    }

    @Test
    fun allOfNeedsEveryPart() {
        val all =
            EvalQuestion(
                EvalTopic.TopEntities,
                "Which?",
                Expected.AllOf(listOf(Expected.Words("Example Club"), Expected.Words("Example Hotels"))),
            )

        assertEquals(EvalStatus.Pass, judge(all, answered("Example Club [1] and Example Hotels [2].")).status)
        assertEquals(EvalStatus.Fail, judge(all, answered("Example Club [1].")).status)
    }

    @Test
    fun aDayIsAcceptedInItsUsualSpellings() {
        val day = EvalQuestion(EvalTopic.TimeRanges, "When?", Expected.Day(LocalDate(2026, 9, 22)))

        assertEquals(EvalStatus.Pass, judge(day, answered("On 2026-09-22 [1].")).status)
        assertEquals(EvalStatus.Pass, judge(day, answered("On 22 September 2026 [1].")).status)
        assertEquals(EvalStatus.Pass, judge(day, answered("September 22, 2026 was busiest [1].")).status)
        assertEquals(EvalStatus.Pass, judge(day, answered("Sep 22, 2026 [1].")).status, "abbreviated month")
        assertEquals(EvalStatus.Pass, judge(day, answered("the 22nd September 2026 [1].")).status, "ordinal")
        assertEquals(EvalStatus.Fail, judge(day, answered("On 2026-09-21 [1].")).status)
    }

    @Test
    fun aQuestionWithNoAnswerFails() {
        val capped: Result<AskOutcome, AskError> = Result.Success(AskOutcome.StepsExhausted(8, emptyList(), model))
        val failed: Result<AskOutcome, AskError> = Result.Error(AskError.ModelFailed("stopped"))

        assertEquals(EvalStatus.Fail, judge(ads, capped).status)
        assertEquals(EvalStatus.Fail, judge(ads, failed).status)
        assertTrue(judge(ads, failed).detail.contains("stopped"))
    }

    @Test
    fun theScoreCountsOnlyPassesAndTheReportListsEveryQuestion() {
        val results =
            listOf(
                judge(ads, answered("5 ads [1].")),
                judge(ads, answered("4 ads [1].")),
                judge(ads, answered("5 ads [1].", unverified = listOf("5"))),
                judge(ads, answered("5 ads [1].")),
            )
        val report = EvalReport(results)

        assertEquals(2, report.passed)
        assertEquals(50, report.percent)
        val rendered = report.render()
        assertTrue(rendered.contains("2/4"), rendered)
        assertTrue(rendered.contains("50%"), rendered)
        assertTrue(rendered.lines().count { it.contains("How many ads was I shown?") } == 4, rendered)
        assertTrue(rendered.lines().any { it.trimStart().startsWith("4  Pass") }, "numbered by position: $rendered")
        assertTrue(rendered.contains("Flagged"), rendered)
    }
}
