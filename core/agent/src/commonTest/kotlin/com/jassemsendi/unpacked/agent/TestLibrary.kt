package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.ImportSummary
import com.jassemsendi.unpacked.model.Inventory
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Source
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A small Library by hand, two Sources in it, with every value a test asserts worked out from what is here.
 * Tunis is an hour east of UTC with no summer time, so a day boundary is where a Tool must read it, not UTC's.
 */
class TestLibrary {
    val zone: TimeZone = TimeZone.of("Africa/Tunis")

    val instagramExport = ExportId("instagram-me-2026-10-20")
    val googleExport = ExportId("takeout-2026-10-21")

    val courier = Entity(Source.Instagram, EntityKind.Advertiser, "examplecourier", "Example Courier")
    val hotels = Entity(Source.Instagram, EntityKind.Advertiser, "examplehotels", "Example Hotels")

    /** Named as Google wrote it: another case and a doubled space, so only the normalised names agree. */
    val googleCourier =
        Entity(Source.GoogleTakeout, EntityKind.Advertiser, Entity.normalizeKey("EXAMPLE  Courier"), "EXAMPLE  Courier")
    val phone = Entity(Source.Instagram, EntityKind.Device, "instagram 450 android", "Instagram 450.0.0.50.77 Android")
    val cooking = Entity(Source.Instagram, EntityKind.Topic, "cooking", "#Cooking")

    val september: Instant = Instant.parse("2026-09-05T12:00:00Z")

    /** Still the 26th in UTC; already the 27th in Tunis. */
    val lateOctober26: Instant = Instant.parse("2026-10-26T23:30:00Z")
    val october27: Instant = Instant.parse("2026-10-27T10:00:00Z")

    val hotelsAd = event(EventKind.AdShown, september, 0, entities = listOf(hotels), caption = "Rooms by the sea")
    val courierAdAtMidnight =
        event(EventKind.AdShown, lateOctober26, 1, entities = listOf(courier), caption = "Parcels overnight")
    val courierAd = event(EventKind.AdShown, october27, 2, entities = listOf(courier))
    val login =
        event(
            EventKind.Login,
            october27 + 5.minutes,
            0,
            path = "security_and_login_information/login_activity.json",
            entities = listOf(phone),
        )
    val googleAd =
        event(
            EventKind.AdShown,
            october27 + 60.minutes,
            0,
            path = "Takeout/My Activity/Ads/MyActivity.json",
            export = googleExport,
            entities = listOf(googleCourier),
        )

    val holdsContact =
        Claim(
            ClaimKind.AdvertiserHoldsContact,
            RawKind("advertisers"),
            Provenance(instagramExport, "ads_information/advertisers.json", 0),
            entities = listOf(courier),
            attributes = mapOf("Advertiser" to "Example Courier"),
        )
    val likesCooking =
        Claim(
            ClaimKind.InferredInterest,
            RawKind("your_topics"),
            Provenance(instagramExport, "preferences/your_topics/your_topics.json", 0),
            asOf = Instant.parse("2026-10-20T00:00:00Z"),
            entities = listOf(cooking),
            attributes = mapOf("Name" to "Cooking"),
        )

    val startedAt: Instant = Instant.parse("2026-10-27T10:12:00Z")
    val instagramImport =
        Import(
            ImportId(1),
            startedAt,
            startedAt + 1.minutes,
            ImportSummary(
                instagramExport,
                Source.Instagram,
                files =
                    listOf(
                        FileResult.Parsed("ads_information/ads_viewed.json", RawKind("ads_viewed"), 3, 3, 0),
                        FileResult.Skipped("personal_information/notes.txt", RawKind("notes"), "not JSON"),
                        FileResult.Failed("ads_information/broken.json", RawKind("broken"), "unexpected end"),
                    ),
                Inventory(jsonFiles = 2, mediaFiles = 4, otherFiles = 1),
            ),
            ExportLocation.Zip("/Downloads/instagram-me-2026-10-20.zip"),
        )
    val googleImport =
        Import(
            ImportId(2),
            startedAt + 10.minutes,
            null,
            ImportSummary(googleExport, Source.GoogleTakeout, files = emptyList(), Inventory(1, 0, 0)),
            ExportLocation.Folder("/Downloads/takeout-2026-10-21"),
        )

    val imports = FakeImportRepository(instagramImport, googleImport)
    val events = FakeEventRepository(imports, hotelsAd, courierAdAtMidnight, courierAd, login, googleAd)
    val claims = FakeClaimRepository(imports, holdsContact, likesCooking)
    val entities = FakeEntityRepository(events, claims)
    val search = FakeSearchRepository(events, claims)

    private fun event(
        kind: EventKind,
        time: Instant,
        recordIndex: Int,
        path: String = "ads_information/ads_viewed.json",
        export: ExportId = instagramExport,
        entities: List<Entity> = emptyList(),
        caption: String? = null,
    ): Event =
        Event(
            kind = kind,
            rawKind = RawKind(path.substringAfterLast('/').removeSuffix(".json")),
            time = time,
            provenance = Provenance(export, path, recordIndex),
            entities = entities,
            attributes = caption?.let { mapOf("Caption" to it) } ?: emptyMap(),
        )
}
