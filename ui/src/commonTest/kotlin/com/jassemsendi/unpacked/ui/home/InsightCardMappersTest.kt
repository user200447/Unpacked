package com.jassemsendi.unpacked.ui.home

import com.jassemsendi.unpacked.insights.Figure
import com.jassemsendi.unpacked.insights.InsightCard
import com.jassemsendi.unpacked.insights.Opens
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.EventKind
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals

class InsightCardMappersTest {
    @Test
    fun everyNumberOpensWhereItCameFrom() {
        val actions = mutableListOf<HomeAction>()
        val visited = mutableListOf<String>()
        val destinations =
            HomeDestinations(
                onImport = { visited += "import" },
                onSearch = { visited += "search" },
                onClaims = { visited += "claims:$it" },
                onEntities = { visited += "entities" },
                onEntity = { visited += "entity:$it" },
            )

        listOf<Opens>(
            Opens.Timeline(EventKind.AdShown),
            Opens.Claims(ClaimKind.AdvertiserHoldsContact),
            Opens.Entity("instagram:device:phone"),
        ).forEach { it.follow({ action -> actions.add(action) }, destinations) }

        assertEquals(listOf<HomeAction>(HomeAction.Filter(EventKind.AdShown)), actions)
        assertEquals(listOf("claims:AdvertiserHoldsContact", "entity:instagram:device:phone"), visited)
    }

    @Test
    fun contentViewedReadsItsRateHourAndWeekdayAsText() {
        val card =
            InsightCard
                .ContentViewed(
                    views = Figure(3, Opens.Timeline(EventKind.ContentViewed)),
                    activeDays = 2,
                    perDay = 1.5,
                    busiestHour = 9,
                    busiestWeekday = DayOfWeek.MONDAY,
                ).toUi()

        assertEquals("3" to "posts, videos and stories viewed", card.headline.value to card.headline.caption)
        assertEquals(listOf("2", "1.5", "09:00", "Monday"), card.details.map { it.value })
        assertEquals(
            listOf("days with views", "a day", "busiest hour", "busiest weekday"),
            card.details.map { it.caption },
        )
        assertEquals(
            setOf(Opens.Timeline(EventKind.ContentViewed)),
            (card.details.map { it.opens } + card.headline.opens).toSet(),
            "every number on the card opens the same Events",
        )
    }

    @Test
    fun aWholeRateReadsWithoutADecimalAndOneLoginIsSingular() {
        val viewed =
            InsightCard
                .ContentViewed(
                    views = Figure(6, Opens.Timeline(EventKind.ContentViewed)),
                    activeDays = 2,
                    perDay = 3.0,
                    busiestHour = 23,
                    busiestWeekday = DayOfWeek.SUNDAY,
                ).toUi()
        assertEquals("3", viewed.details[1].value)

        val logins =
            InsightCard
                .Logins(
                    logins = Figure(1, Opens.Timeline(EventKind.Login)),
                    devices = Figure(1, Opens.Timeline(EventKind.Login)),
                    topDevices = emptyList(),
                    networks = Figure(0, Opens.Timeline(EventKind.Login)),
                    topNetworks = emptyList(),
                ).toUi()
        assertEquals("login", logins.headline.caption)
        assertEquals(listOf("device", "networks"), logins.details.map { it.caption })
    }
}
