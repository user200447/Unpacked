package com.jassemsendi.unpacked.agent

import kotlin.test.Test
import kotlin.test.assertEquals

/** Seam 3: the citation validator, pure over an answer and the Tool outputs the model saw. */
class CitationValidatorTest {
    private val countOutput =
        """{"step":1,"tool":"countEvents","kind":"AdShown","from":"2026-10-01","to":null,"total":4,""" +
            """"cited":[{"cite":"[1]","file":"ads_information/ads_viewed.json","record":2}]}"""

    @Test
    fun citationMarkersAreListedOnceEachInOrder() {
        assertEquals(listOf(3, 1), citationMarkersIn("Four ads [3], two of them [1] in October [3]."))
        assertEquals(emptyList(), citationMarkersIn("No citations here."))
    }

    @Test
    fun numbersAreReadWithoutTheCitationMarkers() {
        assertEquals(listOf("4", "2"), numbersIn("You were shown 4 ads [1], 2 of them [12] in October."))
    }

    @Test
    fun numbersAreCanonicalSoThatFormattingDoesNotFailAnAnswer() {
        assertEquals("1200", canonicalNumber("1,200"))
        assertEquals("3", canonicalNumber("3.0"))
        assertEquals("2.5", canonicalNumber("2.50"))
        assertEquals("7", canonicalNumber("007"))
        assertEquals("0", canonicalNumber("00"))
        assertEquals("450.0.0.50.77", canonicalNumber("450.0.0.50.77"), "a version is left alone")
    }

    @Test
    fun anAnswerWhoseEveryNumberIsInSomeToolOutputIsVerified() {
        val unverified =
            unverifiedNumbers(
                "You were shown 4 ads [1] since 2026-10-01, the last one at record 2.",
                listOf(countOutput),
            )

        assertEquals(emptyList(), unverified)
    }

    @Test
    fun aNumberAbsentFromEveryToolOutputIsReportedOnceAsWritten() {
        val unverified =
            unverifiedNumbers(
                "You were shown 99 ads [1], about 1,200 a year, or 99 again.",
                listOf(countOutput, """{"step":2,"total":12}"""),
            )

        assertEquals(listOf("99", "1,200"), unverified)
    }

    @Test
    fun aNumberInsideATimeOrDateCountsPartByPart() {
        val output = """{"time":"2026-10-27T10:00:00Z","cite":"[1]"}"""

        assertEquals(emptyList(), unverifiedNumbers("At 10:00 on 27 October 2026 [1].", listOf(output)))
        assertEquals(listOf("11"), unverifiedNumbers("At 11:00 on 27 October 2026 [1].", listOf(output)))
    }
}
