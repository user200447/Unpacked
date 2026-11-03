package com.jassemsendi.unpacked.ui.home

import com.jassemsendi.unpacked.insights.InsightCard

/** How a card reads on Home; the numbers keep what they open, so the screen only has to follow them. */
internal fun InsightCard.toUi(): InsightCardUi =
    when (this) {
        is InsightCard.AdvertisersHoldingContact -> toUi()
        is InsightCard.AdsShown -> toUi()
        is InsightCard.ContentViewed -> toUi()
        is InsightCard.Logins -> toUi()
        is InsightCard.SocialGraph -> toUi()
        is InsightCard.Inferred -> toUi()
        is InsightCard.OffPlatform -> toUi()
        is InsightCard.Messages -> toUi()
    }

private fun InsightCard.AdvertisersHoldingContact.toUi() =
    InsightCardUi(
        id = "contact",
        title = "Who has your contact details",
        headline = advertisers.toUi(noun(advertisers.value, "advertiser") + " uploaded a list with you on it"),
        details = emptyList(),
        rows = top.map { it.toRow("Claim") },
        months = emptyList(),
        emptyText = "No advertiser in your library has uploaded a list with your contact details.".onlyIf(isEmpty),
    )

private fun InsightCard.AdsShown.toUi() =
    InsightCardUi(
        id = "ads",
        title = "Ads shown to you",
        headline = ads.toUi(noun(ads.value, "ad") + " shown"),
        details = listOf(advertisers.toUi("from " + noun(advertisers.value, "advertiser"))),
        rows = top.map { it.toRow("ad") },
        months = emptyList(),
        emptyText = "No ads in your library yet.".onlyIf(isEmpty),
    )

private fun InsightCard.ContentViewed.toUi() =
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

private fun InsightCard.Logins.toUi() =
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

private fun InsightCard.SocialGraph.toUi() =
    InsightCardUi(
        id = "social",
        title = "Follows and unfollows",
        headline = follows.toUi(noun(follows.value, "follow")),
        details = listOf(unfollows.toUi(noun(unfollows.value, "unfollow"))),
        rows = emptyList(),
        months = byMonth.toRows("follows", "unfollows"),
        emptyText = "No follows or unfollows in your library yet.".onlyIf(isEmpty),
    )

private fun InsightCard.Inferred.toUi() =
    InsightCardUi(
        id = "inferred",
        title = "What was inferred about you",
        headline = locations.toUi(noun(locations.value, "location") + " inferred"),
        details = listOf(interests.toUi(noun(interests.value, "interest") + " inferred")),
        rows = (topPlaces + topTopics).map { it.toRow("Claim") },
        months = emptyList(),
        emptyText = "Nothing inferred about you in your library yet.".onlyIf(isEmpty),
    )

private fun InsightCard.OffPlatform.toUi() =
    InsightCardUi(
        id = "off-platform",
        title = "Beyond the app",
        headline = linksVisited.toUi(noun(linksVisited.value, "link") + " visited"),
        details = listOf(settings.toUi(noun(settings.value, "off-platform setting"))),
        rows = emptyList(),
        months = emptyList(),
        emptyText = "No links visited or off-platform settings in your library yet.".onlyIf(isEmpty),
    )

private fun InsightCard.Messages.toUi() =
    InsightCardUi(
        id = "messages",
        title = "Your messages",
        headline = sent.toUi(noun(sent.value, "message") + " sent"),
        details = listOf(received.toUi(noun(received.value, "message") + " received")),
        rows = emptyList(),
        months = byMonth.toRows("sent", "received"),
        emptyText = "No messages in your library yet.".onlyIf(isEmpty),
    )
