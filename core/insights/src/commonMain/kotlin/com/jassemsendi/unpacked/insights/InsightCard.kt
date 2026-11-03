package com.jassemsendi.unpacked.insights

import com.jassemsendi.unpacked.data.EntityTally
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.EventKind
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth

/** Where a number on an Insight card leads: the Events or Claims it was counted from, so that every number is checkable. */
public sealed interface Opens {
    /** The Timeline narrowed to one Kind of Event. */
    public data class Timeline(
        public val kind: EventKind,
    ) : Opens

    /** The Claims screen with one Kind's group open. */
    public data class Claims(
        public val kind: ClaimKind,
    ) : Opens

    /** One Entity's own timeline and Claims. */
    public data class Entity(
        public val id: String,
    ) : Opens
}

/** A number on an Insight card and what it opens. */
public data class Figure(
    public val value: Int,
    public val opens: Opens,
)

/**
 * One of the fixed set of deterministic summaries of the Library (glossary: Insight card), computed through
 * the repository interfaces with no model and no database of its own. Every number on one opens what it was
 * counted from. A card over a Library with nothing of its Kind is [isEmpty], never an error.
 */
public sealed interface InsightCard {
    public val isEmpty: Boolean

    /** How many advertisers uploaded an audience list holding the user's contact details, and the most frequent names. */
    public data class AdvertisersHoldingContact(
        public val advertisers: Figure,
        /** Each with how many Claims name it: one per list the advertiser uploaded, as a rule. */
        public val top: List<EntityTally>,
    ) : InsightCard {
        override val isEmpty: Boolean get() = advertisers.value == 0
    }

    /** How many ads the user was shown, from how many advertisers, and the advertisers shown most. */
    public data class AdsShown(
        public val ads: Figure,
        public val advertisers: Figure,
        public val top: List<EntityTally>,
    ) : InsightCard {
        override val isEmpty: Boolean get() = ads.value == 0
    }

    /** How much content was viewed, on how many days, and the hour and weekday most of it fell on in the device's zone. */
    public data class ContentViewed(
        public val views: Figure,
        public val activeDays: Int,
        /** Views per day with at least one; zero when there is none. */
        public val perDay: Double,
        /** Null when nothing was viewed. */
        public val busiestHour: Int?,
        public val busiestWeekday: DayOfWeek?,
    ) : InsightCard {
        override val isEmpty: Boolean get() = views.value == 0
    }

    /** How many logins, from how many distinct devices and networks, and the most frequent of each. */
    public data class Logins(
        public val logins: Figure,
        public val devices: Figure,
        public val topDevices: List<EntityTally>,
        public val networks: Figure,
        public val topNetworks: List<EntityTally>,
    ) : InsightCard {
        override val isEmpty: Boolean get() = logins.value == 0
    }

    /** How the user's social graph changed: follows and unfollows in all, and month by month. */
    public data class SocialGraph(
        public val follows: Figure,
        public val unfollows: Figure,
        /** Every month from the first with a follow or unfollow to the last, quiet months between included. */
        public val byMonth: List<MonthOfFollows>,
    ) : InsightCard {
        override val isEmpty: Boolean get() = follows.value == 0 && unfollows.value == 0
    }

    /** What the Source inferred about the user: the locations and interests it holds, and the places and topics named most. */
    public data class Inferred(
        public val locations: Figure,
        public val topPlaces: List<EntityTally>,
        public val interests: Figure,
        public val topTopics: List<EntityTally>,
    ) : InsightCard {
        override val isEmpty: Boolean get() = locations.value == 0 && interests.value == 0
    }

    /** How the user is followed beyond the app: the links visited, and the settings over activity off the platform. */
    public data class OffPlatform(
        public val linksVisited: Figure,
        public val settings: Figure,
    ) : InsightCard {
        override val isEmpty: Boolean get() = linksVisited.value == 0 && settings.value == 0
    }

    /** Messages sent and received, in all and month by month: counts only, since no message text is stored (ADR 0003). */
    public data class Messages(
        public val sent: Figure,
        public val received: Figure,
        /** Every month from the first with a message to the last, quiet months between included. */
        public val byMonth: List<MonthOfMessages>,
    ) : InsightCard {
        override val isEmpty: Boolean get() = sent.value == 0 && received.value == 0
    }
}

/** One month of the social graph card's series, in the device's zone. */
public data class MonthOfFollows(
    public val month: YearMonth,
    public val follows: Int,
    public val unfollows: Int,
)

/** One month of the messages card's series, in the device's zone. */
public data class MonthOfMessages(
    public val month: YearMonth,
    public val sent: Int,
    public val received: Int,
)
