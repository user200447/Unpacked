package com.jassemsendi.unpacked.ingest.instagram

import com.jassemsendi.unpacked.ingest.Output
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind
import kotlin.time.Instant

/**
 * One record after decoding and flattening, with everything a mapper needs to build Events and
 * Claims. [lists] keeps the record's lists of plain values apart from their joined text in [fields].
 */
internal data class FlatRecord(
    val fields: Map<String, String>,
    val time: Instant?,
    val provenance: Provenance,
    val rawKind: RawKind,
    val lists: Map<String, List<String>> = emptyMap(),
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
 * An untimed file: every record is one Claim of [kind] naming the Entities [entities] finds, "as of"
 * the record's time when the file gives one. A Claim is never dropped for lacking a time.
 */
internal class ClaimMapper(
    private val kind: ClaimKind,
    private val entities: EntityRule = { emptyList() },
) : InstagramMapper {
    override fun map(record: FlatRecord): Output =
        Output(
            claims =
                listOf(
                    Claim(
                        kind = kind,
                        rawKind = record.rawKind,
                        provenance = record.provenance,
                        asOf = record.time,
                        entities = entities(record.fields),
                        attributes = record.fields,
                    ),
                ),
        )
}

/**
 * A file whose record lists several things, such as every advertiser holding the user's contact
 * details: each item of the record's list is one Claim of [kind], naming the Entity [entity] makes
 * of it and told apart by the item index of its Provenance (ADR 0007). The Claim's attributes are
 * the record's, with the list's label carrying that one item.
 */
internal class ItemClaimMapper(
    private val kind: ClaimKind,
    private val entity: (item: String) -> Entity? = { null },
) : InstagramMapper {
    override fun map(record: FlatRecord): Output {
        val (label, items) = record.lists.entries.firstOrNull() ?: return Output.NONE
        val claims =
            items.mapIndexed { index, item ->
                Claim(
                    kind = kind,
                    rawKind = record.rawKind,
                    provenance = record.provenance.copy(itemIndex = index),
                    asOf = record.time,
                    entities = listOfNotNull(entity(item)),
                    attributes = record.fields.mapValues { (key, value) -> if (key == label) item else value },
                )
            }
        return Output(claims = claims)
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
            "ads_viewed.json" to EventMapper(EventKind.AdShown, owner(EntityKind.Advertiser) + hashtags()),
            "posts_viewed.json" to EventMapper(EventKind.ContentViewed, owner(EntityKind.Account) + hashtags()),
            "videos_watched.json" to EventMapper(EventKind.ContentViewed, owner(EntityKind.Account) + hashtags()),
            "stories_viewed.json" to EventMapper(EventKind.ContentViewed, owner(EntityKind.Account) + hashtags()),
            "suggested_profiles_viewed.json" to EventMapper(EventKind.ContentViewed, profile()),
            "threads_viewed.json" to EventMapper(EventKind.ContentViewed, account("Author")),
            // Likes and reactions
            "liked_posts.json" to EventMapper(EventKind.ContentLiked, owner(EntityKind.Account) + hashtags()),
            "liked_comments.json" to EventMapper(EventKind.ContentLiked, account("Title")),
            "liked_threads.json" to EventMapper(EventKind.ContentLiked, account("Title")),
            "story_likes.json" to EventMapper(EventKind.ContentLiked, owner(EntityKind.Account) + hashtags()),
            "emoji_story_reactions.json" to EventMapper(EventKind.ContentLiked, owner(EntityKind.Account) + hashtags()),
            "story_reaction_sticker_reactions.json" to
                EventMapper(EventKind.ContentLiked, owner(EntityKind.Account, prefix = "Owner / Owner")),
            // Logins and logouts
            "login_activity.json" to EventMapper(EventKind.Login, device()),
            "logout_activity.json" to EventMapper(EventKind.Logout, device()),
            // Social graph
            "followers.json" to EventMapper(EventKind.Follow, account("Value", nameField = "Title", urlField = "Href")),
            "following.json" to EventMapper(EventKind.Follow, account("Title", urlField = "Href")),
            "recent_follow_requests.json" to EventMapper(EventKind.Follow, profile()),
            "pending_follow_requests.json" to EventMapper(EventKind.Follow, profile()),
            "recently_unfollowed_profiles.json" to EventMapper(EventKind.Unfollow, profile()),
            // Searches and links
            "recent_searches.json" to EventMapper(EventKind.Search),
            "profile_searches.json" to EventMapper(EventKind.Search, account("Title")),
            "word_or_phrase_searches.json" to EventMapper(EventKind.Search),
            "link_history.json" to EventMapper(EventKind.LinkVisited),
            // Timed files the vocabulary does not distinguish (ADR 0001)
            "post_comments.json" to EventMapper(EventKind.Other, account("Media Owner")),
            "posts.json" to EventMapper(EventKind.Other),
            "stories.json" to EventMapper(EventKind.Other),
            "saved_posts.json" to EventMapper(EventKind.Other, owner(EntityKind.Account) + hashtags()),
            "polls.json" to EventMapper(EventKind.Other, owner(EntityKind.Account) + hashtags()),
            "quizzes.json" to EventMapper(EventKind.Other, owner(EntityKind.Account, prefix = "Media / Owner")),
            "password_change_activity.json" to EventMapper(EventKind.Other),
            "profile_changes.json" to EventMapper(EventKind.Other),
            // Advertisers that uploaded a list holding the user's contact details: one Claim each
            "advertisers_using_your_activity_or_information.json" to
                ItemClaimMapper(ClaimKind.AdvertiserHoldsContact, named(EntityKind.Advertiser)),
            // What the Source infers the user is, or is not, interested in
            "your_topics.json" to ClaimMapper(ClaimKind.InferredInterest, topic("Name")),
            "see_less_topics.json" to ClaimMapper(ClaimKind.InferredInterest),
            "posts_you're_interested_or_not_interested_in.json" to
                ClaimMapper(ClaimKind.InferredInterest, owner(EntityKind.Account)),
            "posts_you're_not_interested_in.json" to ClaimMapper(ClaimKind.InferredInterest, owner(EntityKind.Account)),
            "profiles_you're_not_interested_in.json" to ClaimMapper(ClaimKind.InferredInterest, profile()),
            // Where the Source places the user
            "locations_of_interest.json" to ItemClaimMapper(ClaimKind.InferredLocation, named(EntityKind.Place)),
            "profile_based_in.json" to ClaimMapper(ClaimKind.InferredLocation, basedIn()),
            "last_known_location.json" to ClaimMapper(ClaimKind.InferredLocation),
            // Audience categories advertisers reach the user through: one Claim each
            "other_categories_used_to_reach_you.json" to ItemClaimMapper(ClaimKind.ReachCategory),
            // Consents, profile fields and synced contacts
            "consents.json" to ClaimMapper(ClaimKind.ConsentGiven),
            "personal_information.json" to ClaimMapper(ClaimKind.ProfileField),
            "signup_details.json" to ClaimMapper(ClaimKind.ProfileField),
            "instagram_profile_information.json" to ClaimMapper(ClaimKind.ProfileField),
            "possible_phone_numbers.json" to ClaimMapper(ClaimKind.ProfileField),
            "synced_contacts.json" to ClaimMapper(ClaimKind.ContactSynced),
            // Untimed files the vocabulary does not distinguish (ADR 0001)
            "ad_preferences.json" to ClaimMapper(ClaimKind.Other),
            "your_activity_off_meta_technologies_settings.json" to ClaimMapper(ClaimKind.Other),
            "notification_preferences.json" to ClaimMapper(ClaimKind.Other),
            "instagram_friend_map.json" to ClaimMapper(ClaimKind.Other),
            "audience_insights.json" to ClaimMapper(ClaimKind.Other),
            "content_interactions.json" to ClaimMapper(ClaimKind.Other),
            "profiles_reached.json" to ClaimMapper(ClaimKind.Other),
            "eligibility.json" to ClaimMapper(ClaimKind.Other),
            "digital_product_payment_quotes.json" to ClaimMapper(ClaimKind.Other),
            "your_ownership_of_digital_items.json" to ClaimMapper(ClaimKind.Other),
            "checkout_payment_information.json" to ClaimMapper(ClaimKind.Other),
            "recently_viewed_items.json" to ClaimMapper(ClaimKind.Other),
            "device_camera_effects_support.json" to ClaimMapper(ClaimKind.Other),
            "note_and_repost_interactions.json" to
                ClaimMapper(ClaimKind.Other, owner(EntityKind.Account, prefix = "Author")),
        )

    private val partSuffix = Regex("_\\d+(?=\\.json$)")

    fun forFile(fileName: String): InstagramMapper? = byFileName[fileName.lowercase().replace(partSuffix, "")]
}
