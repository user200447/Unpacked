package com.jassemsendi.unpacked.ingest.instagram

import com.jassemsendi.unpacked.fixtures.InstagramDialectEdgeCases
import com.jassemsendi.unpacked.fixtures.InstagramFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class InstagramDecoderTest {
    @Test
    fun listFileDecodesOneRecordPerElement() {
        val records = InstagramDecoder.decode(InstagramFixtures.ADS_VIEWED_JSON).getOrThrow()
        assertEquals(3, records.size)
        assertEquals(Instant.fromEpochSeconds(InstagramFixtures.FIRST_AD_TIME_SECONDS), records[0].time)
    }

    @Test
    fun singleObjectFileDecodesAsOneRecordAndJoinsVecValues() {
        val record = InstagramDecoder.decode(InstagramFixtures.ADVERTISERS_JSON).getOrThrow().single()
        assertEquals(mapOf("Advertisers" to "Example Hotels, Example Fashion"), record.fields)
        assertNull(record.time)
    }

    @Test
    fun malformedAndForeignShapesFail() {
        assertTrue(InstagramDecoder.decode(InstagramFixtures.MALFORMED_JSON).isFailure)
        assertTrue(InstagramDecoder.decode("""{"participants": [], "messages": []}""").isFailure)
        assertTrue(InstagramDecoder.decode("42").isFailure)
    }

    @Test
    fun flatteningNestsTitlesDropsEmptiesAndKeepsHrefOnlyWithoutValue() {
        val first = InstagramDecoder.decode(InstagramFixtures.ADS_VIEWED_JSON).getOrThrow()[0].fields
        assertEquals("Example Club", first["Owner / Name"])
        assertEquals("exampleclub", first["Owner / Username"])
        assertEquals("http://example.com/club", first["Owner / URL"])
        assertEquals("https://www.instagram.com/p/AAA/", first["URL"])
        assertEquals("Sign up and come to the match", first["Caption"])
        assertTrue("Title" !in first, "empty values are dropped")
        assertTrue("Hashtags" !in first && "Brand partner" !in first, "empty dict and vec are dropped")

        val hrefOnly =
            InstagramDecoder
                .decode("""[{"label_values": [{"label": "Link", "href": "https://x.example"}]}]""")
                .getOrThrow()
                .single()
                .fields
        assertEquals(mapOf("Link" to "https://x.example"), hrefOnly)
    }

    @Test
    fun timestampsAreSecondsUnlessTooLargeAndZeroMeansUnknown() {
        assertEquals(Instant.fromEpochSeconds(1_791_445_719), epochToInstant(1_791_445_719))
        assertEquals(Instant.fromEpochMilliseconds(1_791_445_719_000), epochToInstant(1_791_445_719_000))
        assertNull(epochToInstant(0))
        assertNull(epochToInstant(null))
    }

    @Test
    fun labelValuesEdgeCasesFlattenNestedGroupsAndTimedOrMediaLeaves() {
        val record = InstagramDecoder.decode(InstagramDialectEdgeCases.LABEL_VALUES_JSON).getOrThrow().single()

        assertEquals(Instant.fromEpochSeconds(InstagramDialectEdgeCases.TIME_SECONDS), record.time)
        assertEquals(
            mapOf(
                "Survey responses / Question" to "Why?, When?",
                "Survey responses / Answer" to "Because, Now",
                "Update time" to InstagramDialectEdgeCases.UPDATE_TIME_ISO,
                "Count" to "3",
                "Owner / URL" to "https://www.instagram.com/stories/outer/1",
                "Owner / Owner / Username" to "Nested.Owner",
            ),
            record.fields,
            "a zero timestamp leaf and a media leaf are dropped; a dated leaf reads as an instant",
        )
    }

    @Test
    fun stringDataEdgeCasesKeepEveryKindOfEntryAndTakeTheFirstDatedOne() {
        val record = InstagramDecoder.decode(InstagramDialectEdgeCases.STRING_DATA_JSON).getOrThrow().single()

        assertEquals(Instant.fromEpochSeconds(InstagramDialectEdgeCases.TIME_SECONDS), record.time, "map entries first")
        assertEquals(
            mapOf(
                "Title" to "Edge",
                "Count" to "42",
                "Link" to "https://l.example",
                "When" to InstagramDialectEdgeCases.TIME_ISO,
                "Value" to "a, b",
                "Href" to "https://a.example",
            ),
            record.fields,
        )
    }
}
