package com.jassemsendi.unpacked.insights

import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.andThen
import com.jassemsendi.unpacked.model.map
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Instant

/**
 * What computes one Insight card: a deterministic read of the Library through its repository interfaces,
 * so that it runs the same against the Room library and an in-memory fake (ADR 0005).
 */
public fun interface CardReader<out T : InsightCard> {
    public suspend fun read(): Result<T, DataError.Local>
}

/** Spec story 23: who has the user's contact details. */
public class AdvertisersHoldingContactReader(
    private val claims: ClaimRepository,
    private val topCount: Int = InsightCards.TOP_COUNT,
) : CardReader<InsightCard.AdvertisersHoldingContact> {
    override suspend fun read(): Result<InsightCard.AdvertisersHoldingContact, DataError.Local> =
        claims.topEntities(ClaimKind.AdvertiserHoldsContact, EntityKind.Advertiser, topCount).map { named ->
            InsightCard.AdvertisersHoldingContact(
                advertisers = Figure(named.count, Opens.Claims(ClaimKind.AdvertiserHoldsContact)),
                top = named.top,
            )
        }
}

/** Spec story 24: who is paying to reach the user. */
public class AdsShownReader(
    private val events: EventRepository,
    private val topCount: Int = InsightCards.TOP_COUNT,
) : CardReader<InsightCard.AdsShown> {
    override suspend fun read(): Result<InsightCard.AdsShown, DataError.Local> =
        events.countByKind().andThen { counts ->
            events.topEntities(EventKind.AdShown, EntityKind.Advertiser, topCount).map { named ->
                InsightCard.AdsShown(
                    ads = Figure(counts[EventKind.AdShown] ?: 0, Opens.Timeline(EventKind.AdShown)),
                    advertisers = Figure(named.count, Opens.Timeline(EventKind.AdShown)),
                    top = named.top,
                )
            }
        }
}

/** Spec stories 21 and 25: the user's viewing pattern, in the device's time zone so that "busiest hour" is their hour. */
public class ContentViewedReader(
    private val events: EventRepository,
    private val timeZone: TimeZone,
) : CardReader<InsightCard.ContentViewed> {
    override suspend fun read(): Result<InsightCard.ContentViewed, DataError.Local> =
        events.timesOf(EventKind.ContentViewed).map { times ->
            val local = times.map { it.toLocalDateTime(timeZone) }
            val activeDays = local.distinctBy { it.date }.size
            InsightCard.ContentViewed(
                views = Figure(times.size, Opens.Timeline(EventKind.ContentViewed)),
                activeDays = activeDays,
                perDay = if (activeDays == 0) 0.0 else times.size.toDouble() / activeDays,
                busiestHour = local.busiest { it.hour },
                busiestWeekday = local.busiest { it.dayOfWeek },
            )
        }

    /** The key most of the times fall on; the earliest of them when two tie, so the answer never changes between reads. */
    private fun <K : Comparable<K>> List<LocalDateTime>.busiest(key: (LocalDateTime) -> K): K? =
        groupingBy(key)
            .eachCount()
            .entries
            .maxWithOrNull(compareBy<Map.Entry<K, Int>> { it.value }.thenByDescending { it.key })
            ?.key
}

/** Spec story 26: where the user logged in from, so that an unfamiliar device or network stands out. */
public class LoginsReader(
    private val events: EventRepository,
    private val topCount: Int = InsightCards.TOP_COUNT,
) : CardReader<InsightCard.Logins> {
    override suspend fun read(): Result<InsightCard.Logins, DataError.Local> =
        events.countByKind().andThen { counts ->
            events.topEntities(EventKind.Login, EntityKind.Device, topCount).andThen { devices ->
                events.topEntities(EventKind.Login, EntityKind.Network, topCount).map { networks ->
                    InsightCard.Logins(
                        logins = Figure(counts[EventKind.Login] ?: 0, Opens.Timeline(EventKind.Login)),
                        devices = Figure(devices.count, Opens.Timeline(EventKind.Login)),
                        topDevices = devices.top,
                        networks = Figure(networks.count, Opens.Timeline(EventKind.Login)),
                        topNetworks = networks.top,
                    )
                }
            }
        }
}

/** Spec story 27: how the user's social graph changed, month by month in the device's zone. */
public class SocialGraphReader(
    private val events: EventRepository,
    private val timeZone: TimeZone,
) : CardReader<InsightCard.SocialGraph> {
    override suspend fun read(): Result<InsightCard.SocialGraph, DataError.Local> =
        events.overTime(EventKind.Follow, EventKind.Unfollow, timeZone).map { (follows, unfollows, byMonth) ->
            InsightCard.SocialGraph(follows, unfollows, byMonth)
        }
}

/** Spec story 28: the profiling, as the Source's own location and interest Claims show it. */
public class InferredReader(
    private val claims: ClaimRepository,
    private val topCount: Int = InsightCards.TOP_COUNT,
) : CardReader<InsightCard.Inferred> {
    override suspend fun read(): Result<InsightCard.Inferred, DataError.Local> =
        claims.countByKind().andThen { counts ->
            claims.topEntities(ClaimKind.InferredLocation, EntityKind.Place, topCount).andThen { places ->
                claims.topEntities(ClaimKind.InferredInterest, EntityKind.Topic, topCount).map { topics ->
                    InsightCard.Inferred(
                        locations =
                            Figure(counts[ClaimKind.InferredLocation] ?: 0, Opens.Claims(ClaimKind.InferredLocation)),
                        topPlaces = places.top,
                        interests =
                            Figure(counts[ClaimKind.InferredInterest] ?: 0, Opens.Claims(ClaimKind.InferredInterest)),
                        topTopics = topics.top,
                    )
                }
            }
        }
}

/** Spec story 29: how the user is tracked beyond the app. */
public class OffPlatformReader(
    private val events: EventRepository,
    private val claims: ClaimRepository,
) : CardReader<InsightCard.OffPlatform> {
    override suspend fun read(): Result<InsightCard.OffPlatform, DataError.Local> =
        events.countByKind().andThen { eventCounts ->
            claims.countByKind().map { claimCounts ->
                InsightCard.OffPlatform(
                    linksVisited =
                        Figure(eventCounts[EventKind.LinkVisited] ?: 0, Opens.Timeline(EventKind.LinkVisited)),
                    settings =
                        Figure(
                            claimCounts[ClaimKind.OffPlatformSetting] ?: 0,
                            Opens.Claims(ClaimKind.OffPlatformSetting),
                        ),
                )
            }
        }
}

/** Spec story 30: messaging volume, month by month in the device's zone; counts are all the Library holds (ADR 0003). */
public class MessagesReader(
    private val events: EventRepository,
    private val timeZone: TimeZone,
) : CardReader<InsightCard.Messages> {
    override suspend fun read(): Result<InsightCard.Messages, DataError.Local> =
        events.overTime(EventKind.MessageSent, EventKind.MessageReceived, timeZone).map { (sent, received, byMonth) ->
            InsightCard.Messages(sent, received, byMonth)
        }
}

/** What a card that counts two Kinds over time reads: each Kind in all, and both by month. */
private data class OverTime(
    val first: Figure,
    val second: Figure,
    val byMonth: List<MonthCounts>,
)

/**
 * Two Kinds of Event counted in all and per calendar month of [timeZone], from the first month either has an Event
 * in to the last, so that a quiet month between shows as zero rather than vanishing. No months when neither has any.
 */
private suspend fun EventRepository.overTime(
    first: EventKind,
    second: EventKind,
    timeZone: TimeZone,
): Result<OverTime, DataError.Local> =
    timesOf(first).andThen { firstTimes ->
        timesOf(second).map { secondTimes ->
            val firstByMonth = firstTimes.countByMonth(timeZone)
            val secondByMonth = secondTimes.countByMonth(timeZone)
            val months = firstByMonth.keys + secondByMonth.keys
            OverTime(
                first = Figure(firstTimes.size, Opens.Timeline(first)),
                second = Figure(secondTimes.size, Opens.Timeline(second)),
                byMonth =
                    if (months.isEmpty()) {
                        emptyList()
                    } else {
                        (months.min()..months.max()).map { month ->
                            MonthCounts(
                                month,
                                listOf(
                                    Figure(firstByMonth[month] ?: 0, Opens.Timeline(first)),
                                    Figure(secondByMonth[month] ?: 0, Opens.Timeline(second)),
                                ),
                            )
                        }
                    },
            )
        }
    }

private fun List<Instant>.countByMonth(timeZone: TimeZone): Map<YearMonth, Int> =
    groupingBy { it.toLocalDateTime(timeZone).date.yearMonth }.eachCount()
