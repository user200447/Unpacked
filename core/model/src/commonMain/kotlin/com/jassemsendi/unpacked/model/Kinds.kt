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
    Other,
}

/** The categories of named things worth counting across Events and Claims. */
public enum class EntityKind {
    Advertiser,
    Account,
    Device,
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
