package com.jassemsendi.unpacked.insights

import com.jassemsendi.unpacked.data.EntityTally
import com.jassemsendi.unpacked.data.ValueTally
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Seam 6: each card against fake repositories, with values worked out by hand from what they hold. */
class InsightCardsTest {
    private val courier = advertiser("Example Courier")
    private val hotels = advertiser("Example Hotels")
    private val phone = device("Instagram 450.0.0.50.77 Android")
    private val laptop = device("Mozilla/5.0 (Macintosh)")

    private val monday = Instant.parse("2026-10-26T23:30:00Z")
    private val tuesday = Instant.parse("2026-10-27T10:00:00Z")

    private val events =
        FakeEventRepository(
            event(EventKind.AdShown, monday, 0, entities = listOf(courier)),
            event(EventKind.AdShown, monday + 1.minutes, 1, entities = listOf(courier)),
            event(EventKind.AdShown, monday + 2.minutes, 2, entities = listOf(hotels)),
            event(EventKind.ContentViewed, monday, 0, path = "posts_viewed.json"),
            event(EventKind.ContentViewed, monday + 15.minutes, 1, path = "posts_viewed.json"),
            event(EventKind.ContentViewed, tuesday, 0, path = "videos_watched.json"),
            login(monday, 0, phone, "203.0.113.7"),
            login(monday + 1.minutes, 1, phone, "203.0.113.7"),
            login(tuesday, 2, laptop, "198.51.100.4"),
            // A login whose Record leaves the address blank is a login, not a network.
            login(tuesday + 1.minutes, 3, phone, ""),
        )
    private val claims =
        FakeClaimRepository(
            holdsContact(courier, "ads_information/advertisers.json", 0),
            holdsContact(courier, "ads_information/advertisers_part_2.json", 0),
            holdsContact(hotels, "ads_information/advertisers.json", 1),
            Claim(
                kind = ClaimKind.InferredInterest,
                rawKind = RawKind("your_topics"),
                provenance = Provenance(export, "preferences/your_topics/your_topics.json", 0),
                attributes = mapOf("Name" to "Cooking"),
            ),
        )

    @Test
    fun advertisersHoldingContactCountsEachAdvertiserOnceHoweverManyListsItUploaded() =
        runTest {
            val card = AdvertisersHoldingContactReader(claims).read().orFail()

            assertEquals(Figure(2, Opens.Claims(ClaimKind.AdvertiserHoldsContact)), card.advertisers)
            assertEquals(listOf(EntityTally(courier, 2), EntityTally(hotels, 1)), card.top)
            assertEquals(
                listOf(
                    courier,
                ),
                AdvertisersHoldingContactReader(claims, topCount = 1).read().orFail().top.map {
                    it.entity
                },
            )
        }

    @Test
    fun adsShownCountsTheAdsAndTheAdvertisersBehindThem() =
        runTest {
            val card = AdsShownReader(events).read().orFail()

            assertEquals(Figure(3, Opens.Timeline(EventKind.AdShown)), card.ads)
            assertEquals(Figure(2, Opens.Entities(EntityKind.Advertiser)), card.advertisers)
            assertEquals(listOf(EntityTally(courier, 2), EntityTally(hotels, 1)), card.top)
        }

    @Test
    fun contentViewedBusiestHourAndWeekdayFollowTheDeviceZone() =
        runTest {
            val utc = ContentViewedReader(events, TimeZone.UTC).read().orFail()
            assertEquals(Figure(3, Opens.Timeline(EventKind.ContentViewed)), utc.views)
            assertEquals(2, utc.activeDays)
            assertEquals(1.5, utc.perDay)
            assertEquals(23, utc.busiestHour)
            assertEquals(DayOfWeek.MONDAY, utc.busiestWeekday)

            // Three hours east, the two late Monday views are early Tuesday ones.
            val east = ContentViewedReader(events, TimeZone.of("UTC+03:00")).read().orFail()
            assertEquals(utc.views, east.views)
            assertEquals(1, east.activeDays)
            assertEquals(3.0, east.perDay)
            assertEquals(2, east.busiestHour)
            assertEquals(DayOfWeek.TUESDAY, east.busiestWeekday)
        }

    @Test
    fun loginsCountDistinctDevicesAndNetworksAndSkipABlankAddress() =
        runTest {
            val card = LoginsReader(events).read().orFail()

            assertEquals(Figure(4, Opens.Timeline(EventKind.Login)), card.logins)
            assertEquals(Figure(2, Opens.Entities(EntityKind.Device)), card.devices)
            assertEquals(listOf(EntityTally(phone, 3), EntityTally(laptop, 1)), card.topDevices)
            assertEquals(Figure(2, Opens.Timeline(EventKind.Login)), card.networks)
            assertEquals(listOf(ValueTally("203.0.113.7", 2), ValueTally("198.51.100.4", 1)), card.topNetworks)
        }

    @Test
    fun aCardOverALibraryWithoutItsKindIsEmptyNotAnError() =
        runTest {
            val cards =
                InsightCards(
                    FakeEventRepository(),
                    FakeClaimRepository(),
                    TimeZone.UTC,
                ).readAll().map { it.orFail() }

            assertEquals(4, cards.size)
            assertTrue(cards.all { it.isEmpty })
            val viewed = assertIs<InsightCard.ContentViewed>(cards[2])
            assertEquals(0, viewed.activeDays)
            assertEquals(0.0, viewed.perDay)
            assertNull(viewed.busiestHour)
            assertNull(viewed.busiestWeekday)
            val logins = assertIs<InsightCard.Logins>(cards[3])
            assertEquals(0, logins.networks.value)
        }

    @Test
    fun theCardsComeInHomeOrderAndALibraryThatWillNotReadFailsOnlyTheCardsThatNeedIt() =
        runTest {
            val cards = InsightCards(events, claims, TimeZone.UTC)
            assertEquals(
                listOf(
                    InsightCard.AdvertisersHoldingContact::class,
                    InsightCard.AdsShown::class,
                    InsightCard.ContentViewed::class,
                    InsightCard.Logins::class,
                ),
                cards.readAll().map { it.orFail()::class },
            )

            events.failure = DataError.Local.UNKNOWN

            val reads = cards.readAll()
            assertIs<Result.Success<InsightCard>>(reads[0])
            reads.drop(1).forEach { assertEquals(Result.Error(DataError.Local.UNKNOWN), it) }
        }

    private fun <T> Result<T, DataError.Local>.orFail(): T =
        when (this) {
            is Result.Success -> data
            is Result.Error -> error("Expected a card, got $error")
        }

    private companion object {
        val export = ExportId("instagram-me-2026-10-20")

        fun advertiser(name: String) = Entity(Source.Instagram, EntityKind.Advertiser, Entity.normalizeKey(name), name)

        fun device(userAgent: String) =
            Entity(Source.Instagram, EntityKind.Device, Entity.normalizeKey(userAgent), userAgent)

        fun event(
            kind: EventKind,
            time: Instant,
            recordIndex: Int,
            path: String = "ads_information/ads_viewed.json",
            attributes: Map<String, String> = emptyMap(),
            entities: List<Entity> = emptyList(),
        ) = Event(
            kind = kind,
            rawKind = RawKind(path.substringAfterLast('/').removeSuffix(".json")),
            time = time,
            provenance = Provenance(export, path, recordIndex),
            entities = entities,
            attributes = attributes,
        )

        fun login(
            time: Instant,
            recordIndex: Int,
            device: Entity,
            address: String,
        ) = event(
            EventKind.Login,
            time,
            recordIndex,
            path = "security_and_login_information/login_activity.json",
            attributes = mapOf(LoginsReader.IP_ADDRESS to address, "User Agent" to device.displayName),
            entities = listOf(device),
        )

        fun holdsContact(
            advertiser: Entity,
            path: String,
            itemIndex: Int,
        ) = Claim(
            kind = ClaimKind.AdvertiserHoldsContact,
            rawKind = RawKind("advertisers_using_your_activity_or_information"),
            provenance = Provenance(export, path, 0, itemIndex),
            attributes = mapOf("Advertiser" to advertiser.displayName),
            entities = listOf(advertiser),
        )
    }
}
