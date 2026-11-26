package com.jassemsendi.unpacked.eval

import com.jassemsendi.unpacked.fixtures.SyntheticInstagramExport
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The twenty questions and their expected values, worked out from the synthetic Export imported into an in-memory
 * Library: what the fixture is known to hold, the way the seam tests state it by hand.
 */
class EvalQuestionsTest {
    private val zone = TimeZone.of("Africa/Tunis")

    @Test
    fun thereAreTwentyDistinctQuestionsCoveringEveryTopic() =
        runTest {
            EvalFixture.seed().use { fixture ->
                val questions = evalQuestions(fixture, zone)

                assertEquals(QUESTION_COUNT, questions.size)
                assertEquals(QUESTION_COUNT, questions.map { it.question }.distinct().size)
                assertEquals(EvalTopic.entries.toSet(), questions.map { it.topic }.toSet())
            }
        }

    @Test
    fun theExpectedValuesAreTheFixturesKnownCounts() =
        runTest {
            EvalFixture.seed().use { fixture ->
                val expected = evalQuestions(fixture, zone).associate { it.question to it.expected }

                assertEquals(Expected.Count(5), expected.getValue("How many ads was I shown?"))
                assertEquals(Expected.Count(3), expected.getValue("How many times did I log in to Instagram?"))
                assertEquals(Expected.Count(5), expected.getValue("How many messages did I send?"))
                assertEquals(Expected.Count(8), expected.getValue("How many messages did I receive?"))
                assertEquals(Expected.Count(9), expected.getValue("How many pieces of content did I view?"))
                // 5 ads, 52 timed Events and 13 messages.
                assertEquals(Expected.Count(70), expected.getValue("How many events are in my library in total?"))
                assertEquals(Expected.Count(2), expected.getValue("How many different devices did I log in from?"))
                assertEquals(
                    Expected.Words("Example Player"),
                    expected.getValue("In which message thread did I send the most messages?"),
                )
                // The ads are an hour apart from the first time, the timed files a day later.
                assertEquals(Expected.Count(5), expected.getValue("How many ads was I shown on 2026-09-21?"))
                assertEquals(Expected.Count(52), expected.getValue("How many events happened on 2026-09-22?"))
                assertEquals(Expected.Day(LocalDate(2026, 9, 22)), expected.getValue("On which day was I most active?"))
                assertEquals(Expected.Count(5), expected.getValue("How many messages did I send in September 2026?"))
                // Two searches for the word, one in each searches file.
                assertEquals(Expected.Count(2), expected.getValue("How many times does sourdough appear in my data?"))
            }
        }

    /** Seed 42 deals the five ads as Club, Fashion, Courier, Fashion, Club: two leaders tied at two ads each. */
    @Test
    fun theAdvertiserQuestionsFollowTheFixturesOwnTally() =
        runTest {
            EvalFixture.seed().use { fixture ->
                val expected = evalQuestions(fixture, zone).associate { it.question to it.expected }

                assertEquals(
                    Expected.Words("Example Club", "Example Fashion"),
                    expected.getValue("Which advertiser showed me the most ads?"),
                )
                assertEquals(Expected.Count(2), expected.getValue("How many ads did Example Club show me?"))
                assertEquals(
                    Expected.AllOf(
                        listOf("Example Club", "Example Courier", "Example Fashion").map { Expected.Words(it) },
                    ),
                    expected.getValue("Which advertisers showed me ads? Name them all."),
                )
                assertEquals(
                    Expected.Words("Example Fashion"),
                    expected.getValue(
                        "Find the ad whose caption says \"Synthetic ad 3\". Which advertiser was it from?",
                    ),
                )
            }
        }

    @Test
    fun theGapReportQuestionsReadTheInstagramRulesOverTheFixture() =
        runTest {
            EvalFixture.seed().use { fixture ->
                val expected = evalQuestions(fixture, zone).associate { it.question to it.expected }

                val missing =
                    expected.getValue("How many of the nine Article 15 items are missing from my Instagram export?")
                assertTrue(missing is Expected.Count && missing.value in 1..8, "$missing")
                assertEquals(
                    Expected.Words("Partial"),
                    expected.getValue("In the Gap report, is the purposes item covered, partial or missing?"),
                )
            }
        }

    @Test
    fun theFixtureIsTheFullSyntheticExportImportedOnce() =
        runTest {
            EvalFixture.seed().use { fixture ->
                assertTrue(fixture.export.includeTimedFiles && fixture.export.includeClaimFiles)
                assertTrue(fixture.export.includeMessageThreads)
                assertEquals(emptyList(), fixture.import.summary.failed)
                assertEquals(13, SyntheticInstagramExport.MESSAGE_THREADS.sumOf { it.sent + it.received })
            }
        }
}
