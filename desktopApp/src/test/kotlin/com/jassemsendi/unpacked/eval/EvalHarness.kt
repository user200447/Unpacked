package com.jassemsendi.unpacked.eval

import com.jassemsendi.unpacked.OllamaModelProvider
import com.jassemsendi.unpacked.agent.AgentUnavailable
import com.jassemsendi.unpacked.agent.AskError
import com.jassemsendi.unpacked.agent.KoogAskAgent
import com.jassemsendi.unpacked.agent.LibraryTools
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The twenty-question eval: a manually run integration test that asks a live Ollama model the questions over the
 * synthetic Export, scores each answer against the fixture and prints a table with the overall score. The go/no-go
 * signal for Week 5: the test passes at [TARGET_PERCENT] or better. Run with `./gradlew :desktopApp:eval`; `test`
 * leaves it out by this class's name (see desktopApp/build.gradle.kts, to be renamed together), and it is skipped
 * when Ollama is not reachable or has no model that calls Tools. `UNPACKED_OLLAMA_MODEL` picks the model, as in the app.
 */
class EvalHarness {
    @Test
    fun twentyQuestionsOverTheSyntheticExportScoreAtLeastTheTarget(): Unit =
        runBlocking {
            val provider = OllamaModelProvider()
            val connected = provider.connect()
            val model = (connected as? Result.Success)?.data?.info
            val reason = "no model to ask: ${(connected as? Result.Error)?.error}"
            if (model == null) println("Skipping the eval, $reason")
            assumeTrue(reason, model != null)
            checkNotNull(model)
            val zone = TimeZone.currentSystemDefault()

            EvalFixture.seed().use { fixture ->
                val tools = with(fixture.library) { LibraryTools(imports, events, claims, entities, search, zone) }
                val agent = KoogAskAgent(tools, provider, zone)
                val results = mutableListOf<EvalResult>()
                // A model that goes away mid-run (Ollama stopped) ends the run: the questions left are not failures
                // of the model, and scoring them as such would say nothing about it.
                var wentAway: AgentUnavailable? = null
                for ((index, question) in evalQuestions(fixture, zone).withIndex()) {
                    println("${index + 1}. ${question.question}")
                    val outcome = agent.ask(question.question)
                    results += judge(question, outcome).also { println("   ${it.status}: ${it.detail}") }
                    wentAway = ((outcome as? Result.Error)?.error as? AskError.Unavailable)?.reason
                    if (wentAway != null) break
                }
                val report = EvalReport(results)

                println()
                println("Model: ${model.name} (${model.locality})")
                println(report.render())
                assertNull(wentAway, "the model went away after question ${results.size}; the score covers only those")
                assertTrue(
                    report.percent >= TARGET_PERCENT,
                    "scored ${report.percent}%, under the $TARGET_PERCENT% target",
                )
            }
        }
}
