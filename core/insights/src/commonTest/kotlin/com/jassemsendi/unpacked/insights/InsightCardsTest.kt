package com.jassemsendi.unpacked.insights

import com.jassemsendi.unpacked.data.EntityTally
import com.jassemsendi.unpacked.fixtures.orFail
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
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    private val homeNetwork = network("203.0.113.7")
    private val cafeNetwork = network("198.51.100.4")

    private val follower = account("Example Follower")
    private val sousse = place("Sousse, Sousse Governorate")
    private val tunis = place("Tunis, Tunis Governorate")
    private val cooking = topic("Cooking")
    private val sourdough = topic("Sourdough")

    private val july = Instant.parse("2026-07-05T12:00:00Z")
    private val september = Instant.parse("2026-09-05T12:00:00Z")
    private val monday = Instant.parse("2026-10-26T23:30:00Z")
    private val tuesday = Instant.parse("2026-10-27T10:00:00Z")

    /** Still October in UTC; already November three hours east. */
    private val octoberEnd = Instant.parse("2026-10-31T23:30:00Z")

    private val events =
        FakeEventRepository(
            event(EventKind.AdShown, monday, 0, entities = listOf(courier)),
            event(EventKind.AdShown, monday + 1.minutes, 1, entities = listOf(courier)),
            event(EventKind.AdShown, monday + 2.minutes, 2, entities = listOf(hotels)),
            event(EventKind.ContentViewed, monday, 0, path = "posts_viewed.json"),
            event(EventKind.ContentViewed, monday + 15.minutes, 1, path = "posts_viewed.json"),
            event(EventKind.ContentViewed, tuesday, 0, path = "videos_watched.json"),
            login(monday, 0, phone, homeNetwork),
            login(monday + 1.minutes, 1, phone, homeNetwork),
            login(tuesday, 2, laptop, cafeNetwork),
            // A login whose Record left the address blank names no network; it is still a login.
            login(tuesday + 1.minutes, 3, phone, null),
            event(EventKind.Follow, july, 0, path = "connections/followers_1.json", entities = listOf(follower)),
            event(EventKind.Follow, monday, 1, path = "connections/followers_1.json", entities = listOf(follower)),
            event(EventKind.Follow, octoberEnd, 2, path = "connections/followers_1.json"),
            event(EventKind.Unfollow, monday, 0, path = "connections/recently_unfollowed_profiles.json"),
            event(EventKind.MessageSent, september, 0, path = "messages/inbox/a_1/message_1.json"),
            event(EventKind.MessageSent, octoberEnd, 1, path = "messages/inbox/a_1/message_1.json"),
            event(EventKind.MessageReceived, monday, 2, path = "messages/inbox/a_1/message_1.json"),
            event(EventKind.MessageReceived, octoberEnd, 3, path = "messages/inbox/a_1/message_1.json"),
            event(EventKind.MessageReceived, octoberEnd + 1.minutes, 4, path = "messages/inbox/a_1/message_1.json"),
            event(EventKind.LinkVisited, monday, 0, path = "logged_information/link_history/link_history.json"),
            event(EventKind.LinkVisited, tuesday, 1, path = "logged_information/link_history/link_history.json"),
        )
    private val claims =
        FakeClaimRepository(
            holdsContact(courier, "ads_information/advertisers.json", 0),
            holdsContact(courier, "ads_information/advertisers_part_2.json", 0),
            holdsContact(hotels, "ads_information/advertisers.json", 1),
            claim(ClaimKind.InferredInterest, "preferences/your_topics/your_topics.json", 0, cooking),
            claim(ClaimKind.InferredInterest, "preferences/your_topics/your_topics.json", 1, sourdough),
            claim(ClaimKind.InferredLocation, "information_about_you/locations_of_interest.json", 0, sousse, item = 0),
            claim(ClaimKind.InferredLocation, "information_about_you/locations_of_interest.json", 0, tunis, item = 1),
            claim(ClaimKind.InferredLocation, "information_about_you/profile_based_in.json", 0, sousse),
            claim(ClaimKind.OffPlatformSetting, "apps_and_websites/$OFF_META_SETTINGS", 0),
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
            assertEquals(Figure(2, Opens.Timeline(EventKind.AdShown)), card.advertisers)
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
    fun loginsCountDistinctDevicesAndNetworks() =
        runTest {
            val card = LoginsReader(events).read().orFail()

            assertEquals(Figure(4, Opens.Timeline(EventKind.Login)), card.logins)
            assertEquals(Figure(2, Opens.Timeline(EventKind.Login)), card.devices)
            assertEquals(listOf(EntityTally(phone, 3), EntityTally(laptop, 1)), card.topDevices)
            assertEquals(Figure(2, Opens.Timeline(EventKind.Login)), card.networks)
            assertEquals(listOf(EntityTally(homeNetwork, 2), EntityTally(cafeNetwork, 1)), card.topNetworks)
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

            assertEquals(8, cards.size)
            assertTrue(cards.all { it.isEmpty })
            val viewed = assertIs<InsightCard.ContentViewed>(cards[2])
            assertEquals(0, viewed.activeDays)
            assertEquals(0.0, viewed.perDay)
            assertNull(viewed.busiestHour)
            assertNull(viewed.busiestWeekday)
            val logins = assertIs<InsightCard.Logins>(cards[3])
            assertEquals(0, logins.networks.value)
            assertEquals(emptyList(), assertIs<InsightCard.SocialGraph>(cards[4]).byMonth)
            assertEquals(emptyList(), assertIs<InsightCard.Messages>(cards[7]).byMonth)
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
                    InsightCard.SocialGraph::class,
                    InsightCard.Inferred::class,
                    InsightCard.OffPlatform::class,
                    InsightCard.Messages::class,
                ),
                cards.readAll().map { it.orFail()::class },
            )

            events.failure = DataError.Local.UNKNOWN

            val reads = cards.readAll()
            val readFromClaimsAlone = setOf(0, 5)
            reads.forEachIndexed { index, read ->
                if (index in readFromClaimsAlone) {
                    assertIs<Result.Success<InsightCard>>(read)
                } else {
                    assertEquals(Result.Error(DataError.Local.UNKNOWN), read, "card $index")
                }
            }
        }

    @Test
    fun socialGraphCountsFollowsAndUnfollowsByMonthInTheDeviceZoneWithTheQuietMonthsBetween() =
        runTest {
            val card = SocialGraphReader(events, TimeZone.UTC).read().orFail()

            assertEquals(Figure(3, Opens.Timeline(EventKind.Follow)), card.follows)
            assertEquals(Figure(1, Opens.Timeline(EventKind.Unfollow)), card.unfollows)
            assertEquals(
                listOf(
                    MonthOfFollows(YearMonth(2026, 7), follows = 1, unfollows = 0),
                    MonthOfFollows(YearMonth(2026, 8), follows = 0, unfollows = 0),
                    MonthOfFollows(YearMonth(2026, 9), follows = 0, unfollows = 0),
                    MonthOfFollows(YearMonth(2026, 10), follows = 2, unfollows = 1),
                ),
                card.byMonth,
            )

            // Three hours east, the follow at the end of October is a November one.
            val east = SocialGraphReader(events, TimeZone.of("UTC+03:00")).read().orFail()
            assertEquals(card.follows, east.follows)
            assertEquals(
                listOf(
                    MonthOfFollows(YearMonth(2026, 10), follows = 1, unfollows = 1),
                    MonthOfFollows(YearMonth(2026, 11), follows = 1, unfollows = 0),
                ),
                east.byMonth.takeLast(2),
            )
        }

    @Test
    fun inferredCountsTheLocationAndInterestClaimsAndNamesThePlacesAndTopicsBehindThem() =
        runTest {
            val card = InferredReader(claims).read().orFail()

            assertEquals(Figure(3, Opens.Claims(ClaimKind.InferredLocation)), card.locations)
            assertEquals(listOf(EntityTally(sousse, 2), EntityTally(tunis, 1)), card.topPlaces)
            assertEquals(Figure(2, Opens.Claims(ClaimKind.InferredInterest)), card.interests)
            assertEquals(listOf(EntityTally(cooking, 1), EntityTally(sourdough, 1)), card.topTopics)
        }

    @Test
    fun offPlatformCountsTheLinksVisitedAndTheOffPlatformSettings() =
        runTest {
            val card = OffPlatformReader(events, claims).read().orFail()

            assertEquals(Figure(2, Opens.Timeline(EventKind.LinkVisited)), card.linksVisited)
            assertEquals(Figure(1, Opens.Claims(ClaimKind.OffPlatformSetting)), card.settings)
        }

    @Test
    fun messagesCountsSentAndReceivedByMonthAndNothingElse() =
        runTest {
            val card = MessagesReader(events, TimeZone.UTC).read().orFail()

            assertEquals(Figure(2, Opens.Timeline(EventKind.MessageSent)), card.sent)
            assertEquals(Figure(3, Opens.Timeline(EventKind.MessageReceived)), card.received)
            assertEquals(
                listOf(
                    MonthOfMessages(YearMonth(2026, 9), sent = 1, received = 0),
                    MonthOfMessages(YearMonth(2026, 10), sent = 1, received = 3),
                ),
                card.byMonth,
            )

            val east = MessagesReader(events, TimeZone.of("UTC+03:00")).read().orFail()
            assertEquals(
                listOf(
                    MonthOfMessages(YearMonth(2026, 9), sent = 1, received = 0),
                    MonthOfMessages(YearMonth(2026, 10), sent = 0, received = 1),
                    MonthOfMessages(YearMonth(2026, 11), sent = 1, received = 2),
                ),
                east.byMonth,
            )
        }

    @Test
    fun aCardWithTwoKindsIsEmptyOnlyWhenBothAreAbsent() =
        runTest {
            val onlyUnfollows = FakeEventRepository(event(EventKind.Unfollow, monday, 0))
            val social = SocialGraphReader(onlyUnfollows, TimeZone.UTC).read().orFail()
            assertFalse(social.isEmpty)
            assertEquals(listOf(MonthOfFollows(YearMonth(2026, 10), follows = 0, unfollows = 1)), social.byMonth)

            val onlySettings = FakeClaimRepository(claim(ClaimKind.OffPlatformSetting, "settings.json", 0))
            assertFalse(OffPlatformReader(FakeEventRepository(), onlySettings).read().orFail().isEmpty)
            assertTrue(OffPlatformReader(FakeEventRepository(), FakeClaimRepository()).read().orFail().isEmpty)
        }

    private companion object {
        val export = ExportId("instagram-me-2026-10-20")
        const val OFF_META_SETTINGS = "your_activity_off_meta_technologies_settings.json"

        fun advertiser(name: String) = Entity(Source.Instagram, EntityKind.Advertiser, Entity.normalizeKey(name), name)

        fun device(userAgent: String) =
            Entity(Source.Instagram, EntityKind.Device, Entity.normalizeKey(userAgent), userAgent)

        fun network(address: String) =
            Entity(Source.Instagram, EntityKind.Network, Entity.normalizeKey(address), address)

        fun account(name: String) = Entity(Source.Instagram, EntityKind.Account, Entity.normalizeKey(name), name)

        fun place(name: String) = Entity(Source.Instagram, EntityKind.Place, Entity.normalizeKey(name), name)

        fun topic(name: String) = Entity(Source.Instagram, EntityKind.Topic, Entity.normalizeKey(name), name)

        fun claim(
            kind: ClaimKind,
            path: String,
            recordIndex: Int,
            entity: Entity? = null,
            item: Int = 0,
        ) = Claim(
            kind = kind,
            rawKind = RawKind(path.substringAfterLast('/').removeSuffix(".json")),
            provenance = Provenance(export, path, recordIndex, item),
            attributes = entity?.let { mapOf("Name" to it.displayName) }.orEmpty(),
            entities = listOfNotNull(entity),
        )

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
            network: Entity?,
        ) = event(
            EventKind.Login,
            time,
            recordIndex,
            path = "security_and_login_information/login_activity.json",
            attributes = mapOf("IP Address" to network?.displayName.orEmpty(), "User Agent" to device.displayName),
            entities = listOfNotNull(device, network),
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
