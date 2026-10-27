package com.jassemsendi.unpacked.fixtures

import com.jassemsendi.unpacked.fixtures.InstagramJson.container
import com.jassemsendi.unpacked.fixtures.InstagramJson.entry
import com.jassemsendi.unpacked.fixtures.InstagramJson.labelValues
import com.jassemsendi.unpacked.fixtures.InstagramJson.leaf
import com.jassemsendi.unpacked.fixtures.InstagramJson.list
import com.jassemsendi.unpacked.fixtures.InstagramJson.stringList
import com.jassemsendi.unpacked.fixtures.InstagramJson.stringMap
import com.jassemsendi.unpacked.fixtures.InstagramJson.timed
import com.jassemsendi.unpacked.fixtures.InstagramRecords.accountHistory
import com.jassemsendi.unpacked.fixtures.InstagramRecords.content
import com.jassemsendi.unpacked.fixtures.InstagramRecords.media
import com.jassemsendi.unpacked.fixtures.InstagramRecords.profileRecord
import com.jassemsendi.unpacked.fixtures.InstagramRecords.quiz
import com.jassemsendi.unpacked.fixtures.InstagramRecords.stickerReaction

/**
 * The timed files of the synthetic Export, one per file the Instagram adapter recognises,
 * each with a handful of invented records shaped like the real Export. Counts are fixed so a test
 * can compute the expected Events per Kind by hand: see the comment above each group.
 */
internal object SyntheticTimedFiles {
    private val player = Account("Example Player", "example.player")
    private val coach = Account("Example Coach", "Example.Coach")
    private val baker = Account("Example Baker", "example_baker")

    /** Every file by path, with its JSON text, given the instant the first record happens at. */
    fun all(firstTime: Long): Map<String, String> {
        val t = Ticker(firstTime)
        return viewed(t) + liked(t) + logins(t) + social(t) + searchesAndLinks(t) + other(t)
    }

    /** Successive records are one minute apart. */
    private class Ticker(
        private val start: Long,
    ) {
        private var ticks = 0

        fun next(): Long = start + MINUTE * ticks++
    }

    // ContentViewed: 2 + 2 + 2 + 1 + 2 = 9 Events, 1 undated record dropped
    private fun viewed(t: Ticker): Map<String, String> =
        mapOf(
            "$ADS/posts_viewed.json" to
                list(
                    content(
                        t.next(),
                        Post("p/CCC", player, "Match day #Football #explore", listOf("Football", "explore")),
                    ),
                    content(t.next(), Post("p/DDD", coach, "Half time")),
                    content(0, Post("p/EEE", baker, "Undated view")),
                ),
            "$ADS/videos_watched.json" to
                list(
                    content(t.next(), Post("reel/FFF", coach, "Training #fyp", listOf("fyp"))),
                    content(t.next(), Post("reel/GGG", baker)),
                ),
            "$STORY_INTERACTIONS/stories_viewed.json" to
                list(
                    content(t.next(), Post("stories/example.player/1", player)),
                    content(t.next(), Post("stories/example_baker/2", baker)),
                ),
            "$ADS/suggested_profiles_viewed.json" to list(profileRecord(t.next(), coach)),
            "$THREADS/threads_viewed.json" to
                container(
                    "text_post_app_text_post_app_posts_seen",
                    threadSeen(t.next(), player, "p/HHH"),
                    threadSeen(t.next(), coach, "p/III"),
                ),
        )

    private fun threadSeen(
        timestamp: Long,
        author: Account,
        path: String,
    ): String =
        stringMap(
            null,
            entry("Author", value = author.username),
            entry("Time", timestamp = timestamp),
            entry("URL", href = "https://www.instagram.com/$path/"),
        )

    // ContentLiked: 3 + 2 + 2 + 1 + 1 + 1 = 10 Events
    private fun liked(t: Ticker): Map<String, String> =
        mapOf(
            "$LIKES/liked_posts.json" to
                list(
                    content(t.next(), Post("reel/JJJ", baker, "Sunday bake #bread", listOf("bread"))),
                    content(t.next(), Post("p/KKK", player)),
                    content(t.next(), Post("p/LLL", coach)),
                ),
            "$LIKES/liked_comments.json" to
                container(
                    "likes_comment_likes",
                    stringList(player.username, "👍", "https://www.instagram.com/reel/JJJ/", t.next()),
                    stringList(baker.username, "👍", "https://www.instagram.com/p/KKK/", t.next()),
                ),
            "$STORY_INTERACTIONS/story_likes.json" to
                list(
                    content(t.next(), Post("stories/example.player/3", player)),
                    content(t.next(), Post("stories/example.coach/4", coach)),
                ),
            "$STORY_INTERACTIONS/emoji_story_reactions.json" to
                list(content(t.next(), Post("stories/example_baker/5", baker, extra = leaf("Emoji", "😂")))),
            "$STORY_INTERACTIONS/story_reaction_sticker_reactions.json" to
                list(stickerReaction(t.next(), "⚫", Post("stories/example.player/6", player))),
            "$THREADS/liked_threads.json" to
                container(
                    "text_post_app_media_likes",
                    stringList(coach.username, "👍", "https://www.threads.com/@example.coach", t.next()),
                ),
        )

    // Login: 3 Events from 2 devices; Logout: 2 Events
    private fun logins(t: Ticker): Map<String, String> =
        mapOf(
            "$LOGIN/login_activity.json" to
                container(
                    "account_history_login_history",
                    accountHistory(t.next(), PHONE),
                    accountHistory(t.next(), LAPTOP),
                    accountHistory(t.next(), PHONE),
                ),
            "$LOGIN/logout_activity.json" to
                container(
                    "account_history_logout_history",
                    accountHistory(t.next(), PHONE),
                    accountHistory(t.next(), LAPTOP),
                ),
        )

    // Follow: 3 + 4 + 1 + 1 + 1 = 10 Events, 1 undated record dropped; Unfollow: 2 Events
    private fun social(t: Ticker): Map<String, String> =
        mapOf(
            "$CONNECTIONS/followers_1.json" to
                list(
                    stringList("", "Amira_Example", "https://www.instagram.com/Amira_Example", t.next()),
                    stringList("", baker.username, "https://www.instagram.com/example_baker", t.next()),
                    stringList("", coach.username, "https://www.instagram.com/Example.Coach", t.next()),
                    stringList("", "nobody", "https://www.instagram.com/nobody", 0),
                ),
            "$CONNECTIONS/following.json" to
                container(
                    "relationships_following",
                    stringList(player.username, null, "https://www.instagram.com/_u/example.player", t.next()),
                    stringList(coach.username, null, "https://www.instagram.com/_u/Example.Coach", t.next()),
                    stringList("Myriem.Example", null, "https://www.instagram.com/_u/Myriem.Example", t.next()),
                    stringList("aziz_example", null, "https://www.instagram.com/_u/aziz_example", t.next()),
                ),
            "$CONNECTIONS/recent_follow_requests.json" to
                list(profileRecord(t.next(), Account("Chaima Example", "chaima_example"))),
            "$CONNECTIONS/pending_follow_requests.json" to
                profileRecord(t.next(), Account("Pending Example", "pending_example")),
            "$THREADS/followers.json" to
                container(
                    "text_post_app_text_post_app_followers",
                    stringList("Basma Example", "basma.example", "https://www.threads.com/basma.example", t.next()),
                ),
            "$CONNECTIONS/recently_unfollowed_profiles.json" to
                list(profileRecord(t.next(), Account("User", "islem_example")), profileRecord(t.next(), baker)),
        )

    // Search: 2 + 1 + 1 = 4 Events; LinkVisited: 2 Events
    private fun searchesAndLinks(t: Ticker): Map<String, String> =
        mapOf(
            "$SEARCHES/recent_searches.json" to
                list(
                    labelValues(t.next(), leaf("Search query", "example baker"), timed("Update time", t.next())),
                    labelValues(t.next(), leaf("Search query", "sourdough"), timed("Update time", t.next())),
                ),
            "$SEARCHES/profile_searches.json" to
                container(
                    "searches_user",
                    stringList(baker.username, null, "https://cdn.example/avatar.jpg", t.next()),
                ),
            "$SEARCHES/word_or_phrase_searches.json" to
                container(
                    "searches_keyword",
                    stringMap("", entry("Search", value = "sourdough"), entry("Time", timestamp = t.next())),
                ),
            "logged_information/link_history/link_history.json" to
                list(
                    labelValues(
                        t.next(),
                        leaf("Website link you visited", "https://qpr.example/"),
                        leaf("Title of website page you visited", "QPR"),
                    ),
                    labelValues(t.next(), leaf("Website link you visited", "https://bakery.example/")),
                ),
        )

    // Other: 2 + 1 + 2 + 1 + 1 + 1 + 1 + 1 = 10 Events
    private fun other(t: Ticker): Map<String, String> =
        mapOf(
            "your_instagram_activity/comments/post_comments_1.json" to
                list(
                    stringMap(
                        null,
                        entry("Comment", value = "Lista"),
                        entry("Media Owner", value = baker.username),
                        entry("Time", timestamp = t.next()),
                    ),
                    stringMap(null, entry("Comment", value = "🔥"), entry("Time", timestamp = t.next())),
                ),
            "your_instagram_activity/media/posts.json" to
                labelValues(
                    t.next(),
                    leaf("Published", "True"),
                    timed("Creation time", 0),
                    timed("Update time", t.next()),
                ),
            "your_instagram_activity/media/stories.json" to
                container(
                    "ig_stories",
                    media("media/other/story_1.webp", t.next(), "Having fun"),
                    media("media/other/story_2.webp", t.next(), ""),
                ),
            "your_instagram_activity/saved/saved_posts.json" to
                list(content(t.next(), Post("reel/MMM", player, "Claude in motion"))),
            "$STORY_INTERACTIONS/polls.json" to
                list(
                    content(
                        t.next(),
                        Post(
                            "stories/example.coach/7",
                            coach,
                            extra =
                                leaf("Option 1", "Yes") + ", " + leaf("Question", "Ready?"),
                        ),
                    ),
                ),
            "$STORY_INTERACTIONS/quizzes.json" to
                list(
                    quiz(t.next(), "Which?", Post("stories/example_baker/8", baker)),
                ),
            "$LOGIN/password_change_activity.json" to
                container(
                    "account_history_password_change_history",
                    stringMap("", entry("Time", timestamp = t.next())),
                ),
            "personal_information/personal_information/profile_changes.json" to
                container(
                    "profile_profile_change",
                    stringMap(
                        "Profile change",
                        entry("Changed", value = "Email"),
                        entry("Previous value"),
                        entry("New value", value = "new@example.com"),
                        entry("Change date", timestamp = t.next()),
                    ),
                ),
        )

    private const val MINUTE = 60L
    private const val ADS = "ads_information/ads_and_topics"
    private const val STORY_INTERACTIONS = "your_instagram_activity/story_interactions"
    private const val LIKES = "your_instagram_activity/likes"
    private const val THREADS = "your_instagram_activity/threads"
    private const val LOGIN = "security_and_login_information/login_and_profile_creation"
    private const val CONNECTIONS = "connections/followers_and_following"
    private const val SEARCHES = "logged_information/recent_searches"
    private const val PHONE =
        "Instagram 450.0.0.50.77 Android (34/14; 420dpi; 1080x2199; samsung; SM-A135F; a13; mt6768; en_US)"
    private const val LAPTOP =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141"
}
