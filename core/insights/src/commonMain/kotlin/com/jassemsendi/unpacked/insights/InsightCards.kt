package com.jassemsendi.unpacked.insights

import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Result
import kotlinx.datetime.TimeZone

/** The Insight cards Home shows, in the order it shows them: the first four of the eight (spec stories 23 to 26). */
public class InsightCards(
    events: EventRepository,
    claims: ClaimRepository,
    timeZone: TimeZone,
    topCount: Int = TOP_COUNT,
) {
    public val readers: List<CardReader<InsightCard>> =
        listOf(
            AdvertisersHoldingContactReader(claims, topCount),
            AdsShownReader(events, topCount),
            ContentViewedReader(events, timeZone),
            LoginsReader(events, topCount),
        )

    /** Every card, each read on its own, so that one that cannot be read costs that card alone. */
    public suspend fun readAll(): List<Result<InsightCard, DataError.Local>> = readers.map { it.read() }

    public companion object {
        /** How many names a card lists under its numbers. */
        public const val TOP_COUNT: Int = 5
    }
}
