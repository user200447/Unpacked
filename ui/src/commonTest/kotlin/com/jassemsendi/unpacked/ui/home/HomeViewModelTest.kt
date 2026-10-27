package com.jassemsendi.unpacked.ui.home

import app.cash.turbine.test
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.RawKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Seam 5: the Home timeline's state transitions, driven through actions against fake repositories. */
class HomeViewModelTest {
    private val courier = advertiser("Example Courier")
    private val adShown =
        event(
            EventKind.AdShown,
            minutesAfterFirst = 0,
            attributes = mapOf("Caption" to "Ship anything", "URL" to "https://example.com/p/1"),
            entities = listOf(courier),
        )
    private val liked =
        event(
            EventKind.ContentLiked,
            minutesAfterFirst = 1,
            path = "your_instagram_activity/likes/liked_posts.json",
            attributes = mapOf("Href" to "https://example.com/p/2"),
        )
    private val login =
        event(
            EventKind.Login,
            minutesAfterFirst = 2,
            path = "security_and_login_information/login_activity.json",
            attributes = mapOf("Device" to "Pixel 9"),
        )
    private val consent =
        Claim(
            kind = ClaimKind.ConsentGiven,
            rawKind = RawKind("consents"),
            provenance = provenanceOf("personal_information/consents.json", 0),
            asOf = Instant.fromEpochMilliseconds(FIRST_EVENT_MILLIS) + 5.minutes,
            attributes = mapOf("Consent to ads" to "", "Update time" to "2026-10-27T10:17:00"),
        )
    private val interest =
        Claim(
            kind = ClaimKind.InferredInterest,
            rawKind = RawKind("ads_interests"),
            provenance = provenanceOf("ads_information/ads_interests.json", 0, itemIndex = 2),
            attributes = mapOf("Interests" to "Sourdough"),
        )
    private val events = FakeEventRepository(adShown, liked, login)
    private val claims = FakeClaimRepository(consent, interest)

    private fun viewModel(
        timeZone: TimeZone = TimeZone.UTC,
        pageSize: Int = 2,
    ) = HomeViewModel(events, claims, timeZone, pageSize).also { it.onAction(HomeAction.Refresh) }

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun nothingIsReadUntilTheScreenAsksForARefresh() =
        runTest {
            assertEquals(HomeState(), HomeViewModel(events, claims, TimeZone.UTC).state.value)
        }

    @Test
    fun refreshingListsTheFirstPageNewestFirstWithTheKindCountsOfTheRepository() =
        runTest {
            val state = viewModel().state.value

            assertFalse(state.isLoading)
            assertEquals(3, state.totalCount)
            assertEquals(
                listOf(
                    KindCountUi(EventKind.AdShown, "Ad shown", 1),
                    KindCountUi(EventKind.ContentLiked, "Content liked", 1),
                    KindCountUi(EventKind.Login, "Login", 1),
                ),
                state.kinds,
            )
            assertEquals(events.countByKind(), state.kinds.associate { it.kind to it.count })
            assertEquals(
                listOf(
                    TimelineEntryUi(
                        RecordRef.Event(login.provenance),
                        "Login",
                        "2026-10-27 10:14",
                        "Pixel 9",
                        "login_activity",
                    ),
                    TimelineEntryUi(
                        RecordRef.Event(liked.provenance),
                        "Content liked",
                        "2026-10-27 10:13",
                        "https://example.com/p/2",
                        "liked_posts",
                    ),
                ),
                state.entries,
            )
            assertTrue(state.hasMore)
            assertNull(state.filter)
            assertNull(state.detail)
        }

    @Test
    fun refreshingPassesThroughLoadingAndKeepsTheLastReadOnScreenMeanwhile() =
        runTest {
            val viewModel = HomeViewModel(events, claims, TimeZone.UTC, pageSize = 2)

            viewModel.state.test {
                assertEquals(HomeState(isLoading = true), awaitItem(), "loading until the screen asks for a refresh")
                events.gate = CompletableDeferred()
                viewModel.onAction(HomeAction.Refresh)
                expectNoEvents()
                events.gate?.complete(Unit)
                val loaded = awaitItem()
                assertFalse(loaded.isLoading)
                assertEquals(2, loaded.entries.size)

                events.gate = CompletableDeferred()
                viewModel.onAction(HomeAction.Refresh)

                assertEquals(loaded.copy(isLoading = true), awaitItem(), "the list stays while it is re-read")
                events.gate?.complete(Unit)
                assertEquals(loaded, awaitItem())
            }
        }

    @Test
    fun readingTheNextPagePassesThroughAReadingStateAndThenAppendsIt() =
        runTest {
            val viewModel = viewModel()

            viewModel.state.test {
                val first = awaitItem()
                events.gate = CompletableDeferred()
                viewModel.onAction(HomeAction.NextPage)
                assertEquals(first.copy(isReadingNextPage = true), awaitItem())
                events.gate?.complete(Unit)
                val appended = awaitItem()
                assertFalse(appended.isReadingNextPage)
                assertEquals(3, appended.entries.size)
                assertFalse(appended.hasMore)
            }
        }

    @Test
    fun theNextPageIsAppendedUntilTheRepositoryHasNoMore() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(HomeAction.NextPage)

            assertEquals(
                listOf(login, liked, adShown).map { RecordRef.Event(it.provenance) },
                viewModel.state.value.entries
                    .map { it.ref },
            )
            assertEquals(
                TimelineEntryUi(
                    RecordRef.Event(adShown.provenance),
                    "Ad shown",
                    "2026-10-27 10:12",
                    "Example Courier",
                    "ads_viewed",
                ),
                viewModel.state.value.entries
                    .last(),
                "an Event that names Entities is summarised by them",
            )
            assertFalse(viewModel.state.value.hasMore)
            assertFalse(viewModel.state.value.isReadingNextPage)

            viewModel.onAction(HomeAction.NextPage)

            assertEquals(3, viewModel.state.value.entries.size, "nothing more to load changes nothing")
        }

    @Test
    fun filteringByKindNarrowsTheTimelineToThatKindAndClearingTheFilterRestoresIt() =
        runTest {
            events.events +=
                event(EventKind.Login, minutesAfterFirst = 3, path = login.provenance.path, recordIndex = 3)
            events.events +=
                event(EventKind.Login, minutesAfterFirst = 4, path = login.provenance.path, recordIndex = 4)
            val viewModel = viewModel()

            viewModel.onAction(HomeAction.Filter(EventKind.Login))

            val filtered = viewModel.state.value
            assertEquals(EventKind.Login, filtered.filter)
            assertEquals(listOf("2026-10-27 10:16", "2026-10-27 10:15"), filtered.entries.map { it.time })
            assertTrue(filtered.entries.all { it.kindLabel == "Login" })
            assertTrue(filtered.hasMore, "the third Login is on the next page")
            assertEquals(5, filtered.totalCount, "the counts stay those of the whole library")
            viewModel.onAction(HomeAction.NextPage)
            assertEquals(3, viewModel.state.value.entries.size)
            assertFalse(viewModel.state.value.hasMore)

            viewModel.onAction(HomeAction.Filter(null))

            assertNull(viewModel.state.value.filter)
            assertEquals(
                listOf("Login", "Login"),
                viewModel.state.value.entries
                    .map { it.kindLabel },
            )
            assertTrue(viewModel.state.value.hasMore)
        }

    @Test
    fun openingAnEventShowsItsLabelsAndValuesRawKindAndProvenanceAndClosingHidesIt() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(HomeAction.Open(RecordRef.Event(adShown.provenance)))

            assertEquals(
                RecordDetailUi(
                    ref = RecordRef.Event(adShown.provenance),
                    kindLabel = "Ad shown",
                    time = RecordTimeUi.Happened("2026-10-27 10:12:00"),
                    rawKind = "ads_viewed",
                    attributes =
                        listOf(
                            AttributeUi("Caption", "Ship anything"),
                            AttributeUi("URL", "https://example.com/p/1"),
                        ),
                    entities = listOf("Example Courier"),
                ),
                viewModel.state.value.detail,
            )

            viewModel.onAction(HomeAction.CloseDetail)

            assertNull(viewModel.state.value.detail)
        }

    @Test
    fun openingAClaimShowsItsAsOfTimeAsLastUpdatedAndAnUndatedOneAsUndated() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(HomeAction.Open(RecordRef.Claim(consent.provenance)))

            assertEquals(
                RecordDetailUi(
                    ref = RecordRef.Claim(consent.provenance),
                    kindLabel = "Consent given",
                    time = RecordTimeUi.LastUpdated("2026-10-27 10:17:00"),
                    rawKind = "consents",
                    attributes =
                        listOf(
                            AttributeUi("Consent to ads", ""),
                            AttributeUi("Update time", "2026-10-27T10:17:00"),
                        ),
                    entities = emptyList(),
                ),
                viewModel.state.value.detail,
            )

            viewModel.onAction(HomeAction.Open(RecordRef.Claim(interest.provenance)))

            val undated = viewModel.state.value.detail
            assertEquals(RecordTimeUi.Undated, undated?.time)
            assertEquals(interest.provenance, undated?.ref?.provenance, "the item index travels with the Provenance")
        }

    @Test
    fun claimsNeverAppearInTheTimelineEvenWhenTheirAsOfTimeIsTheNewest() =
        runTest {
            val viewModel = viewModel(pageSize = 50)

            val refs =
                viewModel.state.value.entries
                    .map { it.ref.provenance }

            assertEquals(3, viewModel.state.value.totalCount)
            assertTrue(claims.claims.none { it.provenance in refs })
            assertTrue(
                viewModel.state.value.kinds
                    .none { it.label == "Consent given" },
            )
        }

    @Test
    fun openingARecordThatIsNoLongerInTheLibraryShowsNothing() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(HomeAction.Open(RecordRef.Event(adShown.provenance.copy(recordIndex = 99))))

            assertNull(viewModel.state.value.detail)
        }

    @Test
    fun theSameInstantRendersInTheDeviceZoneSoTwoZonesReadDifferently() =
        runTest {
            val utc = viewModel(TimeZone.UTC)
            val tokyo = viewModel(TimeZone.of("Asia/Tokyo"))

            assertEquals(
                "2026-10-27 10:14",
                utc.state.value.entries
                    .first()
                    .time,
            )
            assertEquals(
                "2026-10-27 19:14",
                tokyo.state.value.entries
                    .first()
                    .time,
            )
            utc.onAction(HomeAction.Open(RecordRef.Claim(consent.provenance)))
            tokyo.onAction(HomeAction.Open(RecordRef.Claim(consent.provenance)))
            assertEquals(
                RecordTimeUi.LastUpdated("2026-10-27 10:17:00"),
                utc.state.value.detail
                    ?.time,
            )
            assertEquals(
                RecordTimeUi.LastUpdated("2026-10-27 19:17:00"),
                tokyo.state.value.detail
                    ?.time,
            )
        }

    @Test
    fun refreshingAfterTheLibraryChangedReadsTheNewCountsAndFirstPage() =
        runTest {
            val viewModel = viewModel(pageSize = 50)
            viewModel.onAction(HomeAction.Filter(EventKind.Login))
            events.events +=
                event(EventKind.Login, minutesAfterFirst = 9, path = login.provenance.path, recordIndex = 1)

            viewModel.onAction(HomeAction.Refresh)

            assertEquals(EventKind.Login, viewModel.state.value.filter, "a refresh keeps the filter")
            assertEquals(4, viewModel.state.value.totalCount)
            assertEquals(
                listOf("2026-10-27 10:21", "2026-10-27 10:14"),
                viewModel.state.value.entries
                    .map { it.time },
            )
        }

    @Test
    fun refreshingWhenTheFilteredKindIsGoneFromTheLibraryShowsEveryKindAgain() =
        runTest {
            val viewModel = viewModel(pageSize = 50)
            viewModel.onAction(HomeAction.Filter(EventKind.Login))
            events.events.remove(login)

            viewModel.onAction(HomeAction.Refresh)

            assertNull(viewModel.state.value.filter)
            assertEquals(
                listOf("Content liked", "Ad shown"),
                viewModel.state.value.entries
                    .map { it.kindLabel },
            )
        }

    @Test
    fun anEmptyLibraryListsNothingAndOffersNoKinds() =
        runTest {
            val viewModel = HomeViewModel(FakeEventRepository(), claims, TimeZone.UTC)

            viewModel.onAction(HomeAction.Refresh)

            assertEquals(HomeState(isLoading = false), viewModel.state.value)
        }
}
