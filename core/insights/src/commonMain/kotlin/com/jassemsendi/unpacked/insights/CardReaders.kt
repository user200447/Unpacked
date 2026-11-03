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
import kotlinx.datetime.toLocalDateTime

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
                    advertisers = Figure(named.count, Opens.Entities(EntityKind.Advertiser)),
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
    /** The label a Login's Record gives its network address under: Instagram's wording, until a second Source needs its own. */
    private val networkLabel: String = IP_ADDRESS,
) : CardReader<InsightCard.Logins> {
    override suspend fun read(): Result<InsightCard.Logins, DataError.Local> =
        events.countByKind().andThen { counts ->
            events.topEntities(EventKind.Login, EntityKind.Device, topCount).andThen { devices ->
                events.valueCounts(EventKind.Login, networkLabel).map { networks ->
                    InsightCard.Logins(
                        logins = Figure(counts[EventKind.Login] ?: 0, Opens.Timeline(EventKind.Login)),
                        devices = Figure(devices.count, Opens.Entities(EntityKind.Device)),
                        topDevices = devices.top,
                        networks = Figure(networks.size, Opens.Timeline(EventKind.Login)),
                        topNetworks = networks.take(topCount),
                    )
                }
            }
        }

    public companion object {
        public const val IP_ADDRESS: String = "IP Address"
    }
}
