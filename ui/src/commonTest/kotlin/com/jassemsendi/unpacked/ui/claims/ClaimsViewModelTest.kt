package com.jassemsendi.unpacked.ui.claims

import app.cash.turbine.test
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.ui.home.FIRST_EVENT_MILLIS
import com.jassemsendi.unpacked.ui.home.FakeClaimRepository
import com.jassemsendi.unpacked.ui.home.FakeEventRepository
import com.jassemsendi.unpacked.ui.home.KindCountUi
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.RecordRowUi
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

/** Seam 5: the Claims screen's state transitions, driven through actions against fake repositories. */
class ClaimsViewModelTest {
    private val courier = advertiser("Example Courier")
    private val adShown = event(EventKind.AdShown, minutesAfterFirst = 0, entities = listOf(courier))
    private val holdsContact =
        Claim(
            kind = ClaimKind.AdvertiserHoldsContact,
            rawKind = RawKind("advertisers_using_your_activity_or_information"),
            provenance = provenanceOf("ads_information/advertisers.json", 0, itemIndex = 1),
            attributes = mapOf("Advertiser" to "Example Courier"),
            entities = listOf(courier),
        )
    private val olderInterest = interest("Cycling", minutesAfterFirst = 3, recordIndex = 0)
    private val newerInterest = interest("Sourdough baking", minutesAfterFirst = 5, recordIndex = 1)
    private val undatedInterest =
        Claim(
            kind = ClaimKind.InferredInterest,
            rawKind = RawKind("your_topics"),
            provenance = provenanceOf("preferences/your_topics/your_topics.json", 0),
            attributes = mapOf("Name" to "Sourdough"),
        )
    private val events = FakeEventRepository(adShown)
    private val claims = FakeClaimRepository(holdsContact, olderInterest, undatedInterest, newerInterest)

    private val interestRows =
        listOf(
            RecordRowUi(
                RecordRef.Claim(newerInterest.provenance),
                "Inferred interest",
                RecordTimeUi.LastUpdated("2026-10-27 10:17"),
                "Sourdough baking",
            ),
            RecordRowUi(
                RecordRef.Claim(olderInterest.provenance),
                "Inferred interest",
                RecordTimeUi.LastUpdated("2026-10-27 10:15"),
                "Cycling",
            ),
            RecordRowUi(
                RecordRef.Claim(undatedInterest.provenance),
                "Inferred interest",
                RecordTimeUi.Undated,
                "Sourdough",
            ),
        )

    private fun interest(
        name: String,
        minutesAfterFirst: Int,
        recordIndex: Int,
    ) = Claim(
        kind = ClaimKind.InferredInterest,
        rawKind = RawKind("ads_interests"),
        provenance = provenanceOf("ads_information/ads_interests.json", recordIndex),
        asOf = Instant.fromEpochMilliseconds(FIRST_EVENT_MILLIS) + minutesAfterFirst.minutes,
        attributes = mapOf("Interests" to name),
    )

    private fun viewModel(
        timeZone: TimeZone = TimeZone.UTC,
        pageSize: Int = 2,
    ) = ClaimsViewModel(claims, events, timeZone, pageSize).also { it.onAction(ClaimsAction.Refresh) }

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun nothingIsReadUntilTheScreenAsksForARefresh() =
        runTest {
            assertEquals(ClaimsState(), ClaimsViewModel(claims, events, TimeZone.UTC).state.value)
        }

    @Test
    fun refreshingListsOneGroupPerKindWithItsCountAndNoClaimsUntilAGroupIsOpened() =
        runTest {
            val state = viewModel().state.value

            assertEquals(
                ClaimsState(
                    isLoading = false,
                    totalCount = 4,
                    kinds =
                        listOf(
                            KindCountUi(ClaimKind.AdvertiserHoldsContact, "Advertiser holds contact", 1),
                            KindCountUi(ClaimKind.InferredInterest, "Inferred interest", 3),
                        ),
                ),
                state,
            )
        }

    @Test
    fun openingAGroupListsItsClaimsDatedNewestFirstThenUndatedInPagesAndClosingItClearsThem() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(ClaimsAction.Expand(ClaimKind.InferredInterest))

            val opened = viewModel.state.value
            assertEquals(ClaimKind.InferredInterest, opened.expanded)
            assertEquals(interestRows.take(2), opened.claims)
            assertTrue(opened.hasMore, "the undated interest is on the next page")

            viewModel.onAction(ClaimsAction.NextPage)

            assertEquals(interestRows, viewModel.state.value.claims)
            assertFalse(viewModel.state.value.hasMore)

            viewModel.onAction(ClaimsAction.Expand(null))

            assertEquals(opened.copy(expanded = null, claims = emptyList(), hasMore = false), viewModel.state.value)
        }

    @Test
    fun openingAnotherGroupReplacesTheListAtOnceAndDropsAPageStillInFlightForTheOldOne() =
        runTest {
            val viewModel = viewModel()

            viewModel.state.test {
                val groups = awaitItem()
                claims.gate = CompletableDeferred()
                viewModel.onAction(ClaimsAction.Expand(ClaimKind.InferredInterest))
                assertEquals(groups.copy(isLoading = true, expanded = ClaimKind.InferredInterest), awaitItem())
                viewModel.onAction(ClaimsAction.Expand(ClaimKind.AdvertiserHoldsContact))
                assertEquals(groups.copy(isLoading = true, expanded = ClaimKind.AdvertiserHoldsContact), awaitItem())
                claims.gate?.complete(Unit)

                val loaded = awaitItem()
                assertEquals(ClaimKind.AdvertiserHoldsContact, loaded.expanded)
                assertEquals(listOf(RecordRef.Claim(holdsContact.provenance)), loaded.claims.map { it.ref })
                assertFalse(loaded.hasMore)
                expectNoEvents()
            }
        }

    @Test
    fun refreshingKeepsTheOpenGroupUnlessTheLibraryNoLongerHoldsIt() =
        runTest {
            val viewModel = viewModel(pageSize = 10)
            viewModel.onAction(ClaimsAction.Expand(ClaimKind.InferredInterest))

            viewModel.onAction(ClaimsAction.Refresh)

            assertEquals(ClaimKind.InferredInterest, viewModel.state.value.expanded)
            assertEquals(interestRows, viewModel.state.value.claims)

            claims.claims.removeAll { it.kind == ClaimKind.InferredInterest }
            viewModel.onAction(ClaimsAction.Refresh)

            val refreshed = viewModel.state.value
            assertNull(refreshed.expanded)
            assertEquals(emptyList(), refreshed.claims)
            assertEquals(
                listOf(KindCountUi(ClaimKind.AdvertiserHoldsContact, "Advertiser holds contact", 1)),
                refreshed.kinds,
            )
            assertEquals(1, refreshed.totalCount)
        }

    @Test
    fun openingAClaimShowsTheSameRecordDetailAsTheTimelineAndClosingHidesIt() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ClaimsAction.Expand(ClaimKind.AdvertiserHoldsContact))

            viewModel.onAction(ClaimsAction.Open(RecordRef.Claim(holdsContact.provenance)))

            assertEquals(holdsContact.toDetail(TimeZone.UTC), viewModel.state.value.detail)

            viewModel.onAction(ClaimsAction.CloseDetail)

            assertNull(viewModel.state.value.detail)
        }

    @Test
    fun aLibraryThatCannotBeReadIsReportedAndTheLastReadStaysOnScreen() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ClaimsAction.Expand(ClaimKind.InferredInterest))
            val before = viewModel.state.value
            claims.failure = DataError.Local.DISK_FULL

            viewModel.onAction(ClaimsAction.NextPage)

            val failed = viewModel.state.value
            assertEquals(before.claims, failed.claims)
            assertFalse(failed.hasMore, "the spinner must not ask again at once")
            assertEquals("The device is out of storage.", failed.error)

            viewModel.onAction(ClaimsAction.Refresh)

            assertEquals(before.kinds, viewModel.state.value.kinds, "the groups stay while the library cannot be read")
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun timesAreShownInTheDeviceTimeZone() =
        runTest {
            val viewModel = viewModel(timeZone = TimeZone.of("Asia/Tokyo"))

            viewModel.onAction(ClaimsAction.Expand(ClaimKind.InferredInterest))

            assertEquals(
                RecordTimeUi.LastUpdated("2026-10-27 19:17"),
                viewModel.state.value.claims
                    .first()
                    .time,
            )
        }
}
