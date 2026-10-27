package com.jassemsendi.unpacked.fixtures

import com.jassemsendi.unpacked.fixtures.InstagramJson.entry
import com.jassemsendi.unpacked.fixtures.InstagramJson.group
import com.jassemsendi.unpacked.fixtures.InstagramJson.hashtags
import com.jassemsendi.unpacked.fixtures.InstagramJson.labelValues
import com.jassemsendi.unpacked.fixtures.InstagramJson.leaf
import com.jassemsendi.unpacked.fixtures.InstagramJson.stringMap

/** An invented Instagram account, as the Export names one. */
internal data class Account(
    val name: String,
    val username: String,
)

/** A piece of content as the content files describe it: where it is, what it says, who posted it. */
internal data class Post(
    val path: String,
    val owner: Account,
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    val extra: String? = null,
)

/** Whole records in the shapes the real Export repeats across many files. */
internal object InstagramRecords {
    private const val IG = "https://www.instagram.com"

    fun owner(account: Account): String =
        group("Owner", leaf("URL", ""), leaf("Name", account.name), leaf("Username", account.username))

    /** A record about a piece of content: its URL, caption, hashtags and the `Owner` group naming its author. */
    fun content(
        timestamp: Long,
        post: Post,
    ): String =
        labelValues(
            timestamp,
            leaf("URL", "$IG/${post.path}/", href = "$IG/${post.path}/"),
            leaf("Caption", post.caption),
            leaf("Title", ""),
            hashtags(post.hashtags),
            owner(post.owner),
            """{"title": "Brand partner", "dict": []}""",
            post.extra.orEmpty(),
        )

    /** A record about one account, as the follow-request and unfollow files are. */
    fun profileRecord(
        timestamp: Long,
        account: Account,
    ): String = labelValues(timestamp, leaf("URL", ""), leaf("Name", account.name), leaf("Username", account.username))

    /** A sticker reaction nests the story's content record, owner and all, under its own `Owner` group. */
    fun stickerReaction(
        timestamp: Long,
        emoji: String,
        post: Post,
    ): String =
        labelValues(
            timestamp,
            leaf("Emoji", emoji),
            group(
                "Owner",
                leaf("URL", "$IG/${post.path}/", href = "$IG/${post.path}/"),
                hashtags(emptyList()),
                owner(post.owner),
            ),
        )

    /** A quiz nests the story's content record under a `Media` group. */
    fun quiz(
        timestamp: Long,
        question: String,
        post: Post,
    ): String =
        labelValues(
            timestamp,
            leaf("Question", question),
            group(
                "Media",
                leaf("URL", "$IG/${post.path}/"),
                leaf("Caption", ""),
                hashtags(emptyList()),
                owner(post.owner),
            ),
        )

    /** One login or logout as the account history files record it: only `Time` is dated. */
    fun accountHistory(
        timestamp: Long,
        userAgent: String,
    ): String =
        stringMap(
            "",
            entry("Cookie Name", value = "*****"),
            entry("IP Address", value = "203.0.113.7"),
            entry("Port", value = "33068"),
            entry("Language Code", value = "en"),
            entry("Time", timestamp = timestamp),
            entry("User Agent", value = userAgent),
        )

    /** One item of the media-list dialect, as the stories and profile-photo files are made of. */
    fun media(
        uri: String,
        creationTimestamp: Long,
        title: String,
    ): String = """{"uri": "$uri", "creation_timestamp": $creationTimestamp, "media_metadata": {}, "title": "$title"}"""
}
