package com.jassemsendi.unpacked.ui.home

import com.jassemsendi.unpacked.insights.Figure
import com.jassemsendi.unpacked.insights.InsightCard
import com.jassemsendi.unpacked.insights.MonthCounts
import com.jassemsendi.unpacked.insights.Opens
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.EventKind
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
                onGapReport = { visited += "gap-report" },
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

    @Test
    fun socialGraphListsItsLatestMonthsWithEachNumberOpeningItsOwnKind() {
        val card =
            InsightCard
                .SocialGraph(
                    follows = Figure(9, Opens.Timeline(EventKind.Follow)),
                    unfollows = Figure(1, Opens.Timeline(EventKind.Unfollow)),
                    byMonth = (1..8).map { month(YearMonth(2026, it), it, EventKind.Follow, 0, EventKind.Unfollow) },
                ).toUi()

        assertEquals("9" to "follows", card.headline.value to card.headline.caption)
        assertEquals(listOf("1" to "unfollow"), card.details.map { it.value to it.caption })
        assertEquals(
            listOf("Mar 2026", "Apr 2026", "May 2026", "Jun 2026", "Jul 2026", "Aug 2026"),
            card.months.map { it.label },
            "the latest six months only",
        )
        val august = card.months.last()
        assertEquals(listOf("8" to "follows", "0" to "unfollows"), august.figures.map { it.value to it.caption })
        assertEquals(
            listOf(Opens.Timeline(EventKind.Follow), Opens.Timeline(EventKind.Unfollow)),
            august.figures.map { it.opens },
        )
        assertNull(card.emptyText)
    }

    @Test
    fun messagesReadsAsCountsOnlyAndIsEmptyWhenNoneWereSentOrReceived() {
        val card =
            InsightCard
                .Messages(
                    sent = Figure(1, Opens.Timeline(EventKind.MessageSent)),
                    received = Figure(3, Opens.Timeline(EventKind.MessageReceived)),
                    byMonth =
                        listOf(
                            month(YearMonth(2026, 10), 1, EventKind.MessageSent, 3, EventKind.MessageReceived),
                        ),
                ).toUi()

        assertEquals("1" to "message sent", card.headline.value to card.headline.caption)
        assertEquals(listOf("3" to "messages received"), card.details.map { it.value to it.caption })
        assertEquals(emptyList(), card.rows, "no thread is named: the card is counts only")
        assertEquals(listOf("Oct 2026"), card.months.map { it.label })
        val october = card.months.single()
        assertEquals(listOf("1" to "sent", "3" to "received"), october.figures.map { it.value to it.caption })

        val empty =
            InsightCard
                .Messages(
                    sent = Figure(0, Opens.Timeline(EventKind.MessageSent)),
                    received = Figure(0, Opens.Timeline(EventKind.MessageReceived)),
                    byMonth = emptyList(),
                ).toUi()
        assertEquals("No messages in your library yet.", empty.emptyText)
    }

    @Test
    fun inferredAndOffPlatformNameWhatTheyCountAndOpenTheirClaims() {
        val inferred =
            InsightCard
                .Inferred(
                    locations = Figure(3, Opens.Claims(ClaimKind.InferredLocation)),
                    topPlaces = emptyList(),
                    interests = Figure(1, Opens.Claims(ClaimKind.InferredInterest)),
                    topTopics = emptyList(),
                ).toUi()
        assertEquals("3" to "locations inferred", inferred.headline.value to inferred.headline.caption)
        assertEquals(listOf("1" to "interest inferred"), inferred.details.map { it.value to it.caption })
        assertEquals(Opens.Claims(ClaimKind.InferredInterest), inferred.details.single().opens)

        val offPlatform =
            InsightCard
                .OffPlatform(
                    linksVisited = Figure(2, Opens.Timeline(EventKind.LinkVisited)),
                    settings = Figure(0, Opens.Claims(ClaimKind.OffPlatformSetting)),
                ).toUi()
        assertEquals("2" to "links visited", offPlatform.headline.value to offPlatform.headline.caption)
        assertEquals(listOf("0" to "off-platform settings"), offPlatform.details.map { it.value to it.caption })
        assertEquals(Opens.Claims(ClaimKind.OffPlatformSetting), offPlatform.details.single().opens)
        assertNull(offPlatform.emptyText, "links were visited, so the card has something to show")
    }

    private fun month(
        month: YearMonth,
        first: Int,
        firstKind: EventKind,
        second: Int,
        secondKind: EventKind,
    ) = MonthCounts(month, listOf(Figure(first, Opens.Timeline(firstKind)), Figure(second, Opens.Timeline(secondKind))))
}
