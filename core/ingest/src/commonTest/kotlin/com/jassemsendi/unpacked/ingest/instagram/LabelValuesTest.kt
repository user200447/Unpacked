package com.jassemsendi.unpacked.ingest.instagram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LabelValuesTest {
    @Test
    fun listFileDecodesOneRecordPerElement() {
        val records = LabelValuesDecoder.decode(InstagramFixtures.ADS_VIEWED_JSON).getOrThrow()
        assertEquals(3, records.size)
        assertEquals(InstagramFixtures.FIRST_AD_TIME_SECONDS, records[0].timestamp)
    }

    @Test
    fun singleObjectFileDecodesAsOneRecordAndJoinsVecValues() {
        val record = LabelValuesDecoder.decode(InstagramFixtures.ADVERTISERS_JSON).getOrThrow().single()
        assertEquals(mapOf("Advertisers" to "Example Hotels, Example Fashion"), record.flatten())
        assertNull(record.time())
    }

    @Test
    fun malformedAndForeignShapesFail() {
        assertTrue(LabelValuesDecoder.decode(InstagramFixtures.MALFORMED_JSON).isFailure)
        assertTrue(LabelValuesDecoder.decode("""{"participants": [], "messages": []}""").isFailure)
        assertTrue(LabelValuesDecoder.decode("42").isFailure)
    }

    @Test
    fun flatteningNestsTitlesDropsEmptiesAndKeepsHrefOnlyWithoutValue() {
        val first = LabelValuesDecoder.decode(InstagramFixtures.ADS_VIEWED_JSON).getOrThrow()[0].flatten()
        assertEquals("Example Club", first["Owner / Name"])
        assertEquals("exampleclub", first["Owner / Username"])
        assertEquals("http://example.com/club", first["Owner / URL"])
        assertEquals("https://www.instagram.com/p/AAA/", first["URL"])
        assertEquals("Sign up and come to the match", first["Caption"])
        assertTrue("Title" !in first, "empty values are dropped")
        assertTrue("Hashtags" !in first && "Brand partner" !in first, "empty dict and vec are dropped")

        val hrefOnly =
            LabelValuesDecoder
                .decode("""[{"label_values": [{"label": "Link", "href": "https://x.example"}]}]""")
                .getOrThrow()
                .single()
                .flatten()
        assertEquals(mapOf("Link" to "https://x.example"), hrefOnly)
    }

    @Test
    fun timestampsAreSecondsUnlessTooLargeAndZeroMeansUnknown() {
        assertEquals(Instant.fromEpochSeconds(1_791_445_719), record(1_791_445_719).time())
        assertEquals(Instant.fromEpochMilliseconds(1_791_445_719_000), record(1_791_445_719_000).time())
        assertNull(record(0).time())
        assertNull(record(null).time())
    }

    private fun record(timestamp: Long?) = LabelValueRecord(timestamp = timestamp)
}
