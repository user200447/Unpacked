package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.fixtures.InstagramClaimFixtures
import com.jassemsendi.unpacked.fixtures.writeText
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The Importer over hand-written untimed files: what each becomes as Claims, and never as Events. */
class InstagramClaimMappersTest {
    private val fs = FakeFileSystem()
    private val root = "/exports/instagram-synthetic".toPath()

    private suspend fun importFile(
        path: String,
        json: String,
    ): Pair<FileResult, Output> {
        fs.writeText(root / path.toPath(), json)
        val steps = Importer(InstagramAdapter).run(Export.directory(fs, root)).toList()
        val finished = steps.filterIsInstance<ImportStep.FileFinished>().single { it.outcome.result.path == path }
        return finished.outcome.result to finished.outcome.output
    }

    @Test
    fun oneRecordListingAdvertisersBecomesOneClaimPerAdvertiserNamingItAsAnEntity() =
        runTest {
            val (result, output) =
                importFile(InstagramClaimFixtures.ADVERTISERS_PATH, InstagramClaimFixtures.ADVERTISERS_JSON)

            assertEquals(
                FileResult.Parsed(
                    InstagramClaimFixtures.ADVERTISERS_PATH,
                    RawKind("advertisers_using_your_activity_or_information"),
                    recordCount = 1,
                    eventCount = 0,
                    claimCount = 3,
                ),
                result,
            )
            assertEquals(emptyList(), output.events)
            val claims = output.claims
            assertEquals(List(3) { ClaimKind.AdvertiserHoldsContact }, claims.map { it.kind })
            assertEquals(List(3) { 0 }, claims.map { it.provenance.recordIndex }, "all from the one record")
            assertEquals(listOf(0, 1, 2), claims.map { it.provenance.itemIndex })
            assertEquals(3, claims.map { it.id }.distinct().size, "each item has its own identity")
            assertEquals(
                listOf("Example Hotels", "Smith, Jones & Co", "Example Fashion"),
                claims.map { it.attributes.getValue("Advertisers") },
                "a name with a comma stays whole",
            )
            assertEquals(
                Entity(Source.Instagram, EntityKind.Advertiser, "smith, jones & co", "Smith, Jones & Co"),
                claims[1].entities.single(),
            )
            assertEquals("instagram:advertiser:example hotels", claims[0].entities.single().id)
            assertTrue(claims.all { it.asOf == null }, "the file gives no time")
        }

    @Test
    fun aConsentBecomesAConsentGivenClaimAsOfItsTimeAndNoEvent() =
        runTest {
            val (result, output) =
                importFile(
                    InstagramClaimFixtures.CONSENTS_PATH,
                    InstagramClaimFixtures.CONSENTS_JSON,
                )

            assertEquals(1, (result as FileResult.Parsed).claimCount)
            assertEquals(0, result.eventCount)
            assertEquals(emptyList(), output.events, "an as-of time does not make a Claim an Event")
            val claim = output.claims.single()
            assertEquals(ClaimKind.ConsentGiven, claim.kind)
            assertEquals(RawKind("consents"), claim.rawKind)
            assertEquals(Instant.fromEpochSeconds(InstagramClaimFixtures.CONSENT_TIME_SECONDS), claim.asOf)
            assertEquals(
                mapOf(
                    InstagramClaimFixtures.CONSENT_LABEL to "",
                    "Update time" to InstagramClaimFixtures.CONSENT_TIME_ISO,
                ),
                claim.attributes,
                "a label that is the whole statement is kept, with nothing beside it",
            )
            assertEquals(0, claim.provenance.recordIndex)
            assertEquals(0, claim.provenance.itemIndex)
        }

    @Test
    fun syncedContactsAreContactSyncedClaimsWithMojibakeRepairedAndValidNamesUntouched() =
        runTest {
            val (_, output) =
                importFile(InstagramClaimFixtures.SYNCED_CONTACTS_PATH, InstagramClaimFixtures.SYNCED_CONTACTS_JSON)

            assertEquals(List(3) { ClaimKind.ContactSynced }, output.claims.map { it.kind })
            assertEquals(
                listOf(
                    InstagramClaimFixtures.REPAIRED_NAME,
                    InstagramClaimFixtures.ARABIC_NAME,
                    InstagramClaimFixtures.LATIN1_NAME,
                ),
                output.claims.map { it.attributes.getValue("First Name") },
            )
            assertEquals(
                listOf("20022578", "44340762", "56930764"),
                output.claims.map {
                    it.attributes.getValue("Contact Information")
                },
            )
            assertEquals(emptyList(), output.claims.flatMap { it.entities }, "a contact is not an Entity")
        }

    @Test
    fun profileBasedInIsAnInferredLocationNamingThePlace() =
        runTest {
            val (_, output) =
                importFile(InstagramClaimFixtures.PROFILE_BASED_IN_PATH, InstagramClaimFixtures.PROFILE_BASED_IN_JSON)

            val claim = output.claims.single()
            assertEquals(ClaimKind.InferredLocation, claim.kind)
            assertNull(claim.asOf)
            assertEquals(
                Entity(Source.Instagram, EntityKind.Place, "sousse, sousse governorate", "Sousse, Sousse Governorate"),
                claim.entities.single(),
                "the place is named as the locations-of-interest file names it, so the two meet",
            )
            assertEquals(
                mapOf(
                    "Location / Country" to "Tunisia",
                    "Location / Region" to "Sousse Governorate",
                    "Location / City" to "Sousse",
                ),
                claim.attributes,
            )
        }
}
