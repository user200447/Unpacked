package com.jassemsendi.unpacked.model

/** Canonical category of an Event, shared by every Source (ADR 0001). */
public enum class EventKind {
    ContentViewed,
    AdShown,
    ContentLiked,
    Login,
    Logout,
    Follow,
    Unfollow,
    Search,
    MessageSent,
    MessageReceived,
    LinkVisited,
    Other,
}

/** Canonical category of a Claim, shared by every Source (ADR 0001). */
public enum class ClaimKind {
    AdvertiserHoldsContact,
    InferredInterest,
    InferredLocation,
    ReachCategory,
    ConsentGiven,
    ProfileField,
    ContactSynced,

    /**
     * A setting that governs what the Source may do with the user's activity off its own platform, such as
     * whether that activity is tied to the profile. Its own Kind because the off-platform Insight card counts it.
     */
    OffPlatformSetting,
    Other,
}

/** The categories of named things worth counting across Events and Claims. */
public enum class EntityKind {
    Advertiser,
    Account,
    Device,

    /** The network a login or logout came from, named by the address the Source recorded. */
    Network,
    Topic,
    Place,

    /** One direct-message conversation, named by its title and nothing more (ADR 0003). */
    Thread,
}

/**
 * The Source's own name for where a record came from, typically the Export file's name
 * without its extension. Kept beside the Kind for provenance and the Explorer.
 */
public data class RawKind(
    val value: String,
)
