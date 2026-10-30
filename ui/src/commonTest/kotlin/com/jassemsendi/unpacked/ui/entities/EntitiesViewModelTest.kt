package com.jassemsendi.unpacked.ui.entities

import app.cash.turbine.test
import com.jassemsendi.unpacked.data.EntityOrder
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Source
import com.jassemsendi.unpacked.ui.home.AttributeUi
import com.jassemsendi.unpacked.ui.home.FakeClaimRepository
import com.jassemsendi.unpacked.ui.home.FakeEventRepository
import com.jassemsendi.unpacked.ui.home.KindCountUi
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.RecordTimeUi
import com.jassemsendi.unpacked.ui.home.advertiser
import com.jassemsendi.unpacked.ui.home.event
import com.jassemsendi.unpacked.ui.home.provenanceOf
import com.jassemsendi.unpacked.ui.home.toDetail
import com.jassemsendi.unpacked.ui.home.toRow
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

/** Seam 5: the Entities screen's state transitions, driven through actions against fake repositories. */
class EntitiesViewModelTest {
    private val courier =
        Entity(
            Source.Instagram,
            EntityKind.Advertiser,
            "examplecourier",
            "Example Courier",
            mapOf("username" to "examplecourier"),
        )
    private val bakery = advertiser("Example Bakery")
    private val cycling = Entity(Source.Instagram, EntityKind.Topic, "cycling", "#cycling")
    private val pixel = Entity(Source.Instagram, EntityKind.Device, "pixel 9", "Pixel 9")
    private val courierAds =
        List(3) { index ->
            event(
                EventKind.AdShown,
                minutesAfterFirst = index,
                attributes = mapOf("Caption" to "Ship $index"),
                entities = listOf(courier),
            )
        }
    private val bakeryAd = event(EventKind.AdShown, minutesAfterFirst = 3, entities = listOf(bakery, cycling))
    private val login =
        event(
            EventKind.Login,
            minutesAfterFirst = 4,
            path = "security_and_login_information/login_activity.json",
            entities = listOf(pixel),
        )
    private val holdsContact =
        Claim(
            kind = ClaimKind.AdvertiserHoldsContact,
            rawKind = RawKind("advertisers_using_your_activity_or_information"),
            provenance = provenanceOf("ads_information/advertisers.json", 0, itemIndex = 1),
            attributes = mapOf("Advertiser" to "Example Courier"),
            entities = listOf(courier),
        )
    private val events = FakeEventRepository(*(courierAds + bakeryAd + login).toTypedArray())
    private val claims = FakeClaimRepository(holdsContact)
    private val entities = FakeEntityRepository(events, claims)

    private val courierRow =
        EntityRowUi(courier.id, "Example Courier", "Advertiser", "Instagram", eventCount = 3, claimCount = 1)
    private val bakeryRow =
        EntityRowUi(bakery.id, "Example Bakery", "Advertiser", "Instagram", eventCount = 1, claimCount = 0)
    private val cyclingRow = EntityRowUi(cycling.id, "#cycling", "Topic", "Instagram", eventCount = 1, claimCount = 0)
    private val pixelRow = EntityRowUi(pixel.id, "Pixel 9", "Device", "Instagram", eventCount = 1, claimCount = 0)

    private fun viewModel(
        timeZone: TimeZone = TimeZone.UTC,
        pageSize: Int = 2,
    ) = EntitiesViewModel(entities, events, claims, timeZone, pageSize).also { it.onAction(EntitiesAction.Refresh) }

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun nothingIsReadUntilTheScreenAsksForARefresh() =
        runTest {
            assertEquals(EntitiesState(), EntitiesViewModel(entities, events, claims, TimeZone.UTC).state.value)
        }

    @Test
    fun refreshingListsEntitiesMostNamedFirstWithTheirKindSourceAndCountsAndTheKindCounts() =
        runTest {
            val state = viewModel(pageSize = 10).state.value

            assertEquals(
                EntitiesState(
                    isLoading = false,
                    totalCount = 4,
                    kinds =
                        listOf(
                            KindCountUi(EntityKind.Advertiser, "Advertiser", 2),
                            KindCountUi(EntityKind.Device, "Device", 1),
                            KindCountUi(EntityKind.Topic, "Topic", 1),
                        ),
                    entities = listOf(courierRow, cyclingRow, bakeryRow, pixelRow),
                ),
                state,
                "ties are broken by name without regard to case or the # a topic starts with",
            )
        }

    @Test
    fun theNextPageIsAppendedUntilTheRepositoryHasNoMore() =
        runTest {
            val viewModel = viewModel()
            assertEquals(listOf(courierRow, cyclingRow), viewModel.state.value.entities)
            assertTrue(viewModel.state.value.hasMore)

            viewModel.onAction(EntitiesAction.NextPage)

            assertEquals(listOf(courierRow, cyclingRow, bakeryRow, pixelRow), viewModel.state.value.entities)
            assertFalse(viewModel.state.value.hasMore)
            assertFalse(viewModel.state.value.isReadingNextPage)
        }

    @Test
    fun sortingByNameReordersTheListAndFilteringByKindNarrowsIt() =
        runTest {
            val viewModel = viewModel(pageSize = 10)

            viewModel.onAction(EntitiesAction.Sort(EntityOrder.Name))

            assertEquals(EntityOrder.Name, viewModel.state.value.order)
            assertEquals(listOf(cyclingRow, bakeryRow, courierRow, pixelRow), viewModel.state.value.entities)

            viewModel.onAction(EntitiesAction.Filter(EntityKind.Advertiser))

            val filtered = viewModel.state.value
            assertEquals(EntityKind.Advertiser, filtered.filter)
            assertEquals(EntityOrder.Name, filtered.order, "the order outlives a filter")
            assertEquals(listOf(bakeryRow, courierRow), filtered.entities)
            assertEquals(4, filtered.totalCount, "the counts stay those of the whole library")
            assertFalse(filtered.hasMore)

            viewModel.onAction(EntitiesAction.Filter(null))

            assertEquals(4, viewModel.state.value.entities.size)
        }

    @Test
    fun refreshingPassesThroughLoadingAndKeepsTheLastReadOnScreenMeanwhile() =
        runTest {
            val viewModel = viewModel(pageSize = 10)

            viewModel.state.test {
                val loaded = awaitItem()
                entities.gate = CompletableDeferred()
                viewModel.onAction(EntitiesAction.Refresh)
                assertEquals(loaded.copy(isLoading = true), awaitItem(), "the list stays while it is re-read")
                entities.gate?.complete(Unit)
                assertEquals(loaded, awaitItem())
            }
        }

    @Test
    fun openingAnEntityShowsItsSourceAttributesClaimsAndEventsNewestFirstInPagesAndClosingReturnsToTheList() =
        runTest {
            val viewModel = viewModel()
            val list = viewModel.state.value

            viewModel.onAction(EntitiesAction.OpenEntity(courier.id))

            val opened = viewModel.state.value.opened
            assertEquals(
                EntityDetailUi(
                    entity = courierRow,
                    attributes = listOf(AttributeUi("username", "examplecourier")),
                    events = courierAds.reversed().take(2).map { it.toRow(TimeZone.UTC) },
                    hasMoreEvents = true,
                    claims = listOf(holdsContact.toRow(TimeZone.UTC)),
                ),
                opened,
            )
            assertEquals("Instagram", opened?.entity?.sourceName)

            viewModel.onAction(EntitiesAction.MoreEvents)

            val more = viewModel.state.value.opened
            assertEquals(courierAds.reversed().map { it.toRow(TimeZone.UTC) }, more?.events)
            assertEquals(false, more?.hasMoreEvents)
            assertEquals(false, more?.isReadingMoreEvents)

            viewModel.onAction(EntitiesAction.CloseEntity)

            assertEquals(list, viewModel.state.value)
        }

    @Test
    fun openingARecordFromAnEntityShowsTheSameDetailAsTheTimelineAndClosingItKeepsTheEntityOpen() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(EntitiesAction.OpenEntity(courier.id))

            viewModel.onAction(EntitiesAction.Open(RecordRef.Claim(holdsContact.provenance)))

            assertEquals(holdsContact.toDetail(TimeZone.UTC), viewModel.state.value.detail)

            viewModel.onAction(EntitiesAction.Open(RecordRef.Event(courierAds[2].provenance)))

            assertEquals(courierAds[2].toDetail(TimeZone.UTC), viewModel.state.value.detail)

            viewModel.onAction(EntitiesAction.CloseDetail)

            assertNull(viewModel.state.value.detail)
            assertEquals(
                courierRow,
                viewModel.state.value.opened
                    ?.entity,
            )
        }

    @Test
    fun openingAnotherEntityDropsAReadStillInFlightForTheFirst() =
        runTest {
            val viewModel = viewModel()

            viewModel.state.test {
                val list = awaitItem()
                entities.gate = CompletableDeferred()
                viewModel.onAction(EntitiesAction.OpenEntity(courier.id))
                viewModel.onAction(EntitiesAction.OpenEntity(bakery.id))
                expectNoEvents()
                entities.gate?.complete(Unit)

                val opened = awaitItem()
                assertEquals(bakeryRow, opened.opened?.entity)
                assertEquals(list.entities, opened.entities)
                expectNoEvents()
            }
        }

    @Test
    fun theNextPageOfAnEntitysEventsStartsWhereTheRepositoryLeftOffEvenWhenOneWasListedAlready() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(EntitiesAction.OpenEntity(courier.id))
            // An Import lands meanwhile with a newer ad, so what was the third Event is now the second page's first.
            events.events += event(EventKind.AdShown, minutesAfterFirst = 9, entities = listOf(courier))

            viewModel.onAction(EntitiesAction.MoreEvents)

            val listed =
                viewModel.state.value.opened
                    ?.events
                    ?.map { it.ref }
            assertEquals(
                courierAds.reversed().map { RecordRef.Event(it.provenance) },
                listed,
                "listed once, none twice",
            )
            assertEquals(
                false,
                viewModel.state.value.opened
                    ?.hasMoreEvents,
            )
        }

    @Test
    fun anEntityTheLibraryNoLongerHoldsIsReportedAndClosed() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(EntitiesAction.OpenEntity(bakery.id))
            assertEquals(
                bakeryRow,
                viewModel.state.value.opened
                    ?.entity,
            )

            events.events.remove(bakeryAd)
            viewModel.onAction(EntitiesAction.Refresh)

            val refreshed = viewModel.state.value
            assertNull(refreshed.opened)
            assertEquals("That is no longer in your library.", refreshed.error)
            assertEquals(2, refreshed.totalCount, "the list is re-read as well; the ad named a topic too")
        }

    @Test
    fun aLibraryThatCannotBeReadIsReportedAndTheLastReadStaysOnScreen() =
        runTest {
            val viewModel = viewModel()
            val before = viewModel.state.value
            entities.failure = DataError.Local.DISK_FULL

            viewModel.onAction(EntitiesAction.NextPage)

            val failed = viewModel.state.value
            assertEquals(before.entities, failed.entities)
            assertFalse(failed.hasMore, "the spinner must not ask again at once")
            assertEquals("The device is out of storage.", failed.error)

            viewModel.onAction(EntitiesAction.OpenEntity(courier.id))

            assertNull(viewModel.state.value.opened)
        }

    @Test
    fun timesAreShownInTheDeviceTimeZone() =
        runTest {
            val viewModel = viewModel(timeZone = TimeZone.of("Asia/Tokyo"))

            viewModel.onAction(EntitiesAction.OpenEntity(pixel.id))

            assertEquals(
                RecordTimeUi.Happened("2026-10-27 19:16"),
                viewModel.state.value.opened
                    ?.events
                    ?.single()
                    ?.time,
            )
        }
}
