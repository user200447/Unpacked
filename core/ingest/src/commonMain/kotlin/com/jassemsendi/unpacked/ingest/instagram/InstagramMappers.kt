package com.jassemsendi.unpacked.ingest.instagram

import com.jassemsendi.unpacked.ingest.Output
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind
import kotlin.time.Instant

/** One record after decoding and flattening, with everything a mapper needs to build Events and Claims. */
internal data class FlatRecord(
    val fields: Map<String, String>,
    val time: Instant?,
    val provenance: Provenance,
    val rawKind: RawKind,
)

/** Turns one flattened record of a known file into Events and Claims. One mapper per registered file. */
internal fun interface InstagramMapper {
    fun map(record: FlatRecord): Output
}

/**
 * A timed file: every dated record is one Event of [kind] naming the Entities [entities] finds;
 * an undated record is dropped and counted, never an Event.
 */
internal class EventMapper(
    private val kind: EventKind,
    private val entities: EntityRule = { emptyList() },
) : InstagramMapper {
    override fun map(record: FlatRecord): Output {
        val time = record.time ?: return Output.NONE
        val event =
            Event(
                kind = kind,
                rawKind = record.rawKind,
                time = time,
                provenance = record.provenance,
                entities = entities(record.fields),
                attributes = record.fields,
            )
        return Output(events = listOf(event))
    }
}

/**
 * The registry of mappers for recognised Instagram files, keyed by file name regardless of case
 * and of the `_1`, `_2` part numbers a large file is split into. The only place a file name is
 * bound to a Kind.
 */
internal object InstagramMappers {
    private val byFileName: Map<String, InstagramMapper> =
        mapOf(
            // Ads and content shown to the user
            "ads_viewed.json" to events(EventKind.AdShown, owner(EntityKind.Advertiser) + hashtags()),
            "posts_viewed.json" to events(EventKind.ContentViewed, owner(EntityKind.Account) + hashtags()),
            "videos_watched.json" to events(EventKind.ContentViewed, owner(EntityKind.Account) + hashtags()),
            "stories_viewed.json" to events(EventKind.ContentViewed, owner(EntityKind.Account) + hashtags()),
            "suggested_profiles_viewed.json" to events(EventKind.ContentViewed, profile()),
            "threads_viewed.json" to events(EventKind.ContentViewed, account("Author", urlField = "URL")),
            // Likes and reactions
            "liked_posts.json" to events(EventKind.ContentLiked, owner(EntityKind.Account) + hashtags()),
            "liked_comments.json" to events(EventKind.ContentLiked, account("Title")),
            "liked_threads.json" to events(EventKind.ContentLiked, account("Title")),
            "story_likes.json" to events(EventKind.ContentLiked, owner(EntityKind.Account) + hashtags()),
            "emoji_story_reactions.json" to events(EventKind.ContentLiked, owner(EntityKind.Account) + hashtags()),
            "story_reaction_sticker_reactions.json" to
                events(EventKind.ContentLiked, owner(EntityKind.Account, prefix = "Owner / Owner")),
            // Sessions
            "login_activity.json" to events(EventKind.Login, device()),
            "logout_activity.json" to events(EventKind.Logout, device()),
            // Social graph
            "followers.json" to events(EventKind.Follow, account("Value", nameField = "Title")),
            "following.json" to events(EventKind.Follow, account("Title")),
            "recent_follow_requests.json" to events(EventKind.Follow, profile()),
            "pending_follow_requests.json" to events(EventKind.Follow, profile()),
            "recently_unfollowed_profiles.json" to events(EventKind.Unfollow, profile()),
            // Searches and links
            "recent_searches.json" to events(EventKind.Search),
            "profile_searches.json" to events(EventKind.Search, account("Title")),
            "word_or_phrase_searches.json" to events(EventKind.Search),
            "link_history.json" to events(EventKind.LinkVisited),
            // Timed files the vocabulary does not distinguish (ADR 0001)
            "post_comments.json" to events(EventKind.Other, account("Media Owner")),
            "posts.json" to events(EventKind.Other),
            "stories.json" to events(EventKind.Other),
            "saved_posts.json" to events(EventKind.Other, owner(EntityKind.Account) + hashtags()),
            "polls.json" to events(EventKind.Other, owner(EntityKind.Account) + hashtags()),
            "quizzes.json" to events(EventKind.Other, owner(EntityKind.Account, prefix = "Media / Owner")),
            "password_change_activity.json" to events(EventKind.Other),
            "profile_changes.json" to events(EventKind.Other),
        )

    private fun events(
        kind: EventKind,
        entities: EntityRule = { emptyList() },
    ): InstagramMapper = EventMapper(kind, entities)

    private val partSuffix = Regex("_\\d+(?=\\.json$)")

    fun forFile(fileName: String): InstagramMapper? = byFileName[fileName.lowercase().replace(partSuffix, "")]
}
