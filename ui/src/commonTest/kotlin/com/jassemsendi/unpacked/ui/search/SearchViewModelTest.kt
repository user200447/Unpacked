package com.jassemsendi.unpacked.ui.search

import app.cash.turbine.test
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.ui.home.FIRST_EVENT_MILLIS
import com.jassemsendi.unpacked.ui.home.FakeClaimRepository
import com.jassemsendi.unpacked.ui.home.FakeEventRepository
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.RecordTimeUi
import com.jassemsendi.unpacked.ui.home.advertiser
import com.jassemsendi.unpacked.ui.home.event
import com.jassemsendi.unpacked.ui.home.provenanceOf
import com.jassemsendi.unpacked.ui.home.toDetail
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

/** Seam 5: the Search screen's state transitions, driven through actions against fake repositories. */
class SearchViewModelTest {
    private val bakery = advertiser("Example Bakery")
    private val bakeryAd =
        event(
            EventKind.AdShown,
            minutesAfterFirst = 0,
            attributes = mapOf("Caption" to "Fresh sourdough daily", "URL" to "https://example.com/p/1"),
            entities = listOf(bakery),
        )
    private val likedBread =
        event(
            EventKind.ContentLiked,
            minutesAfterFirst = 1,
            path = "your_instagram_activity/likes/liked_posts.json",
            attributes = mapOf("Href" to "https://example.com/p/sourdough-tips"),
        )
    private val login =
        event(
            EventKind.Login,
            minutesAfterFirst = 2,
            path = "security_and_login_information/login_activity.json",
            attributes = mapOf("Device" to "Pixel 9"),
        )
    private val datedInterest =
        Claim(
            kind = ClaimKind.InferredInterest,
            rawKind = RawKind("ads_interests"),
            provenance = provenanceOf("ads_information/ads_interests.json", 0),
            asOf = Instant.fromEpochMilliseconds(FIRST_EVENT_MILLIS) + 5.minutes,
            attributes = mapOf("Interests" to "Sourdough baking"),
        )
    private val undatedInterest =
        Claim(
            kind = ClaimKind.InferredInterest,
            rawKind = RawKind("your_topics"),
            provenance = provenanceOf("preferences/your_topics/your_topics.json", 0),
            attributes = mapOf("Name" to "Sourdough"),
        )
    private val events = FakeEventRepository(bakeryAd, likedBread, login)
    private val claims = FakeClaimRepository(datedInterest, undatedInterest)
    private val search = FakeSearchRepository(events, claims)

    private val sourdoughHits =
        listOf(
            SearchHitUi(
                RecordRef.Claim(datedInterest.provenance),
                "Inferred interest",
                RecordTimeUi.LastUpdated("2026-10-27 10:17"),
                "Sourdough baking",
            ),
            SearchHitUi(
                RecordRef.Event(likedBread.provenance),
                "Content liked",
                RecordTimeUi.Happened("2026-10-27 10:13"),
                "https://example.com/p/sourdough-tips",
            ),
            SearchHitUi(
                RecordRef.Event(bakeryAd.provenance),
                "Ad shown",
                RecordTimeUi.Happened("2026-10-27 10:12"),
                "Example Bakery",
            ),
            SearchHitUi(
                RecordRef.Claim(undatedInterest.provenance),
                "Inferred interest",
                RecordTimeUi.Undated,
                "Sourdough",
            ),
        )

    private fun viewModel(
        timeZone: TimeZone = TimeZone.UTC,
        pageSize: Int = 10,
    ) = SearchViewModel(search, events, claims, timeZone, pageSize)

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun nothingIsSearchedUntilAWordIsTyped() =
        runTest {
            val viewModel = viewModel()

            assertEquals(SearchState(), viewModel.state.value)

            viewModel.onAction(SearchAction.TextChanged("   "))

            assertEquals(SearchState(text = "   "), viewModel.state.value)
            assertEquals(emptyList(), search.searches, "blank text never reaches the library")
        }

    @Test
    fun typingListsTheEventsAndClaimsThatMentionTheWordNewestFirstWithUndatedClaimsLast() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(SearchAction.TextChanged("sourdough"))

            val state = viewModel.state.value
            assertFalse(state.isSearching)
            assertEquals("sourdough", state.text)
            assertEquals(sourdoughHits, state.hits)
            assertFalse(state.hasMore)
            assertNull(state.error)
            assertNull(state.detail)
        }

    @Test
    fun typingPassesThroughSearchingAndKeepsTheLastHitsOnScreenMeanwhile() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(SearchAction.TextChanged("sourdough"))

            viewModel.state.test {
                val before = awaitItem()
                search.gate = CompletableDeferred()
                viewModel.onAction(SearchAction.TextChanged("pixel"))
                assertEquals(before.copy(text = "pixel", isSearching = true), awaitItem())
                search.gate?.complete(Unit)
                val after = awaitItem()
                assertFalse(after.isSearching)
                assertEquals(listOf(RecordRef.Event(login.provenance)), after.hits.map { it.ref })
            }
        }

    @Test
    fun clearingTheTextClearsTheHitsWithoutAskingTheLibrary() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(SearchAction.TextChanged("sourdough"))

            viewModel.onAction(SearchAction.TextChanged(""))

            assertEquals(SearchState(), viewModel.state.value)
            assertEquals(listOf("sourdough"), search.searches)
        }

    @Test
    fun aSearchThatLandsAfterTheTextChangedAgainIsDropped() =
        runTest {
            val viewModel = viewModel()
            search.gate = CompletableDeferred()

            viewModel.onAction(SearchAction.TextChanged("sourdough"))
            viewModel.onAction(SearchAction.TextChanged("pixel"))
            search.gate?.complete(Unit)

            assertEquals("pixel", viewModel.state.value.text)
            assertEquals(
                listOf(RecordRef.Event(login.provenance)),
                viewModel.state.value.hits
                    .map { it.ref },
            )
            assertFalse(viewModel.state.value.isSearching)
        }

    @Test
    fun theNextPageIsAppendedWhileThePagesComeBackFull() =
        runTest {
            val viewModel = viewModel(pageSize = 2)
            viewModel.onAction(SearchAction.TextChanged("sourdough"))
            assertEquals(sourdoughHits.take(2), viewModel.state.value.hits)
            assertTrue(viewModel.state.value.hasMore)

            viewModel.state.test {
                val first = awaitItem()
                search.gate = CompletableDeferred()
                viewModel.onAction(SearchAction.NextPage)
                assertEquals(first.copy(isReadingNextPage = true), awaitItem())
                search.gate?.complete(Unit)
                val second = awaitItem()
                assertEquals(sourdoughHits, second.hits)
                assertTrue(second.hasMore, "a full page may be followed by another")
                assertFalse(second.isReadingNextPage)
                search.gate = null

                viewModel.onAction(SearchAction.NextPage)

                skipItems(1)
                val last = awaitItem()
                assertEquals(sourdoughHits, last.hits)
                assertFalse(last.hasMore, "an empty page is the end")
            }

            viewModel.onAction(SearchAction.NextPage)

            assertEquals(
                listOf("sourdough", "sourdough", "sourdough"),
                search.searches,
                "nothing more to read asks nothing",
            )
        }

    @Test
    fun openingAHitShowsTheSameRecordDetailAsTheTimelineAndClosingHidesIt() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(SearchAction.TextChanged("sourdough"))

            viewModel.onAction(SearchAction.Open(RecordRef.Event(bakeryAd.provenance)))

            assertEquals(bakeryAd.toDetail(TimeZone.UTC), viewModel.state.value.detail)

            viewModel.onAction(SearchAction.Open(RecordRef.Claim(undatedInterest.provenance)))

            assertEquals(undatedInterest.toDetail(TimeZone.UTC), viewModel.state.value.detail)
            assertEquals(
                RecordTimeUi.Undated,
                viewModel.state.value.detail
                    ?.time,
            )

            viewModel.onAction(SearchAction.CloseDetail)

            assertNull(viewModel.state.value.detail)
        }

    @Test
    fun openingAHitThatIsNoLongerInTheLibrarySaysSoAndShowsNoDetail() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(SearchAction.TextChanged("sourdough"))

            viewModel.onAction(SearchAction.Open(RecordRef.Event(login.provenance.copy(recordIndex = 99))))

            assertNull(viewModel.state.value.detail)
            assertEquals("That is no longer in your library.", viewModel.state.value.error)
            assertEquals(sourdoughHits, viewModel.state.value.hits, "the hits stay")
        }

    @Test
    fun aLibraryThatWillNotSearchKeepsTheLastHitsAndSaysWhy() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(SearchAction.TextChanged("sourdough"))
            search.failure = DataError.Local.UNKNOWN

            viewModel.onAction(SearchAction.TextChanged("sourdough b"))

            val state = viewModel.state.value
            assertFalse(state.isSearching)
            assertEquals("Your library could not be read or written.", state.error)
            assertEquals(sourdoughHits, state.hits)

            search.failure = null
            viewModel.onAction(SearchAction.TextChanged("sourdough daily"))

            assertNull(viewModel.state.value.error, "a successful search clears the message")
            assertEquals(
                listOf(RecordRef.Event(bakeryAd.provenance)),
                viewModel.state.value.hits
                    .map { it.ref },
            )
        }

    @Test
    fun hitTimesReadInTheDeviceZone() =
        runTest {
            val tokyo = viewModel(TimeZone.of("Asia/Tokyo"))

            tokyo.onAction(SearchAction.TextChanged("sourdough"))

            assertEquals(
                listOf(
                    RecordTimeUi.LastUpdated("2026-10-27 19:17"),
                    RecordTimeUi.Happened("2026-10-27 19:13"),
                    RecordTimeUi.Happened("2026-10-27 19:12"),
                    RecordTimeUi.Undated,
                ),
                tokyo.state.value.hits
                    .map { it.time },
            )
        }
}
