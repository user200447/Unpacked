package com.jassemsendi.unpacked.ingest.instagram

import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class AdsViewedMapperTest {
    private val provenance = Provenance("export", InstagramFixtures.ADS_VIEWED_PATH, 7)
    private val rawKind = RawKind("ads_viewed")
    private val time = Instant.fromEpochSeconds(InstagramFixtures.FIRST_AD_TIME_SECONDS)

    @Test
    fun undatedRecordsProduceNothing() {
        val mapped =
            AdsViewedMapper.map(
                FlatRecord(mapOf("Owner / Name" to "Someone"), time = null, provenance, rawKind),
            )
        assertTrue(mapped.events.isEmpty() && mapped.claims.isEmpty())
    }

    @Test
    fun advertiserKeyPrefersTheUsernameAndFallsBackToTheNormalizedName() {
        val withUsername = map(mapOf("Owner / Name" to "Example Club", "Owner / Username" to "ExampleClub"))
        assertEquals("instagram:advertiser:exampleclub", withUsername.entities.single().id)
        assertEquals("ExampleClub", withUsername.entities.single().attributes["username"])

        val nameOnly = map(mapOf("Owner / Name" to "  Example   Club "))
        assertEquals("instagram:advertiser:example club", nameOnly.entities.single().id)

        assertTrue(map(mapOf("Caption" to "no owner at all")).entities.isEmpty())
    }

    @Test
    fun eventCarriesKindTimeProvenanceAndAllFields() {
        val fields = mapOf("Owner / Name" to "Example Club", "Caption" to "Hello")
        val event = map(fields)
        assertEquals(EventKind.AdShown, event.kind)
        assertEquals(time, event.time)
        assertEquals(provenance, event.provenance)
        assertEquals(rawKind, event.rawKind)
        assertEquals(fields, event.attributes)
        assertEquals(EntityKind.Advertiser, event.entities.single().kind)
    }

    private fun map(fields: Map<String, String>) =
        AdsViewedMapper.map(FlatRecord(fields, time, provenance, rawKind)).events.single()
}
