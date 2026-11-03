package com.jassemsendi.unpacked.ui.home

import com.jassemsendi.unpacked.data.EntityTally
import com.jassemsendi.unpacked.insights.Figure
import com.jassemsendi.unpacked.insights.InsightCard
import com.jassemsendi.unpacked.insights.MonthOfFollows
import com.jassemsendi.unpacked.insights.MonthOfMessages
import com.jassemsendi.unpacked.insights.Opens
import com.jassemsendi.unpacked.model.EventKind
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth
import kotlin.math.roundToInt

/** How a card reads on Home; the numbers keep what they open, so the screen only has to follow them. */
internal fun InsightCard.toUi(): InsightCardUi =
    when (this) {
        is InsightCard.AdvertisersHoldingContact -> {
            InsightCardUi(
                id = "contact",
                title = "Who has your contact details",
                headline = advertisers.toUi(noun(advertisers.value, "advertiser") + " uploaded a list with you on it"),
                details = emptyList(),
                rows = top.map { it.toRow("Claim") },
                months = emptyList(),
                emptyText =
                    "No advertiser in your library has uploaded a list with your contact details.".onlyIf(
                        isEmpty,
                    ),
            )
        }

        is InsightCard.AdsShown -> {
            InsightCardUi(
                id = "ads",
                title = "Ads shown to you",
                headline = ads.toUi(noun(ads.value, "ad") + " shown"),
                details = listOf(advertisers.toUi("from " + noun(advertisers.value, "advertiser"))),
                rows = top.map { it.toRow("ad") },
                months = emptyList(),
                emptyText = "No ads in your library yet.".onlyIf(isEmpty),
            )
        }

        is InsightCard.ContentViewed -> {
            InsightCardUi(
                id = "viewed",
                title = "What you viewed",
                headline = views.toUi("posts, videos and stories viewed"),
                details =
                    listOfNotNull(
                        FigureUi(activeDays.toString(), noun(activeDays, "day") + " with views", views.opens),
                        FigureUi(perDay.toOneDecimal(), "a day", views.opens),
                        busiestHour?.let { FigureUi(it.toHourText(), "busiest hour", views.opens) },
                        busiestWeekday?.let { FigureUi(it.toText(), "busiest weekday", views.opens) },
                    ),
                rows = emptyList(),
                months = emptyList(),
                emptyText = "Nothing viewed in your library yet.".onlyIf(isEmpty),
            )
        }

        is InsightCard.Logins -> {
            InsightCardUi(
                id = "logins",
                title = "Your logins",
                headline = logins.toUi(noun(logins.value, "login")),
                details =
                    listOf(
                        devices.toUi(noun(devices.value, "device")),
                        networks.toUi(noun(networks.value, "network")),
                    ),
                rows = (topDevices + topNetworks).map { it.toRow("login") },
                months = emptyList(),
                emptyText = "No logins in your library yet.".onlyIf(isEmpty),
            )
        }

        is InsightCard.SocialGraph -> {
            InsightCardUi(
                id = "social",
                title = "Follows and unfollows",
                headline = follows.toUi(noun(follows.value, "follow")),
                details = listOf(unfollows.toUi(noun(unfollows.value, "unfollow"))),
                rows = emptyList(),
                months = byMonth.takeLast(MONTHS_SHOWN).map { it.toRow() },
                emptyText = "No follows or unfollows in your library yet.".onlyIf(isEmpty),
            )
        }

        is InsightCard.Inferred -> {
            InsightCardUi(
                id = "inferred",
                title = "What was inferred about you",
                headline = locations.toUi(noun(locations.value, "location") + " inferred"),
                details = listOf(interests.toUi(noun(interests.value, "interest") + " inferred")),
                rows = (topPlaces + topTopics).map { it.toRow("Claim") },
                months = emptyList(),
                emptyText = "Nothing inferred about you in your library yet.".onlyIf(isEmpty),
            )
        }

        is InsightCard.OffPlatform -> {
            InsightCardUi(
                id = "off-platform",
                title = "Beyond the app",
                headline = linksVisited.toUi(noun(linksVisited.value, "link") + " visited"),
                details = listOf(settings.toUi(noun(settings.value, "off-platform setting"))),
                rows = emptyList(),
                months = emptyList(),
                emptyText = "No links visited or off-platform settings in your library yet.".onlyIf(isEmpty),
            )
        }

        is InsightCard.Messages -> {
            InsightCardUi(
                id = "messages",
                title = "Your messages",
                headline = sent.toUi(noun(sent.value, "message") + " sent"),
                details = listOf(received.toUi(noun(received.value, "message") + " received")),
                rows = emptyList(),
                months = byMonth.takeLast(MONTHS_SHOWN).map { it.toRow() },
                emptyText = "No messages in your library yet.".onlyIf(isEmpty),
            )
        }
    }

/** Goes where a number on a card points: the timeline is this screen, narrowed; the rest are other screens. */
internal fun Opens.follow(
    onAction: (HomeAction) -> Unit,
    destinations: HomeDestinations,
) {
    when (this) {
        is Opens.Timeline -> onAction(HomeAction.Filter(kind))
        is Opens.Claims -> destinations.onClaims(kind)
        is Opens.Entity -> destinations.onEntity(id)
    }
}

private fun Figure.toUi(caption: String) = FigureUi(value.toString(), caption, opens)

private fun EntityTally.toRow(noun: String) =
    CardRowUi(entity.displayName, counted(count, noun), Opens.Entity(entity.id))

private fun MonthOfFollows.toRow() =
    MonthRowUi(
        month.toText(),
        listOf(
            FigureUi(follows.toString(), "follows", Opens.Timeline(EventKind.Follow)),
            FigureUi(unfollows.toString(), "unfollows", Opens.Timeline(EventKind.Unfollow)),
        ),
    )

private fun MonthOfMessages.toRow() =
    MonthRowUi(
        month.toText(),
        listOf(
            FigureUi(sent.toString(), "sent", Opens.Timeline(EventKind.MessageSent)),
            FigureUi(received.toString(), "received", Opens.Timeline(EventKind.MessageReceived)),
        ),
    )

private fun String.onlyIf(condition: Boolean): String? = takeIf { condition }

/** The noun alone, in the number's grammatical number: the figure in front of it is shown large. */
private fun noun(
    count: Int,
    singular: String,
): String = if (count == 1) singular else "${singular}s"

/** "1.5", or "3" when there is nothing after the point. */
private fun Double.toOneDecimal(): String {
    val tenths = (this * TENTHS).roundToInt()
    return if (tenths % TENTHS == 0) "${tenths / TENTHS}" else "${tenths / TENTHS}.${tenths % TENTHS}"
}

/** The hour as the start of its range, "23:00" for 23. */
private fun Int.toHourText(): String = toString().padStart(2, '0') + ":00"

private fun DayOfWeek.toText(): String = name.lowercase().replaceFirstChar { it.titlecase() }

/** "Oct 2026": the month's short name and its year. */
private fun YearMonth.toText(): String {
    val shortName = month.name.take(MONTH_ABBREVIATION)
    return shortName.lowercase().replaceFirstChar { it.titlecase() } + " $year"
}

private const val TENTHS = 10
private const val MONTH_ABBREVIATION = 3

/** How many of a series' latest months a card lists. */
private const val MONTHS_SHOWN = 6
