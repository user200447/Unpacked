package com.jassemsendi.unpacked.fixtures

import com.jassemsendi.unpacked.fixtures.InstagramMessageJson.audio
import com.jassemsendi.unpacked.fixtures.InstagramMessageJson.callDuration
import com.jassemsendi.unpacked.fixtures.InstagramMessageJson.content
import com.jassemsendi.unpacked.fixtures.InstagramMessageJson.message
import com.jassemsendi.unpacked.fixtures.InstagramMessageJson.photo
import com.jassemsendi.unpacked.fixtures.InstagramMessageJson.reaction
import com.jassemsendi.unpacked.fixtures.InstagramMessageJson.share
import com.jassemsendi.unpacked.fixtures.InstagramMessageJson.thread

/**
 * Hand-written message threads shaped exactly like the real Export, with invented people and
 * words. Everything in [PRIVATE_TEXT] is what ADR 0003 says must never reach the library.
 */
public object InstagramMessageFixtures {
    private const val MESSAGES = "your_instagram_activity/messages"

    public const val THREAD_PATH: String = "$MESSAGES/inbox/exampleplayer_1001/message_1.json"
    public const val THREAD_KEY: String = "exampleplayer_1001"
    public const val THREAD_TITLE: String = "Example Player"
    public const val REQUEST_THREAD_PATH: String = "$MESSAGES/message_requests/examplecourier_1003/message_1.json"
    public const val REQUEST_THREAD_KEY: String = "examplecourier_1003"
    public const val MOJIBAKE_THREAD_PATH: String = "$MESSAGES/inbox/examplecafe_1002/message_1.json"

    /** "Example Café" as the Export writes it. */
    public const val MOJIBAKE_THREAD_TITLE: String = "Example CafÃ©"
    public const val REPAIRED_THREAD_TITLE: String = "Example Café"

    public const val FIRST_MESSAGE_TIME_MS: Long = 1_791_600_000_250L
    private const val MINUTE_MS = 60_000L

    private const val PLAYER = "Example Player"
    private const val COURIER = "Example Courier"
    private const val COACH = "Example Coach"
    private const val BAKER = "Example Baker"
    private const val PHOTO = "$MESSAGES/inbox/exampleplayer_1001/photos/ticket_4411.jpg"
    private const val VOICE_NOTE = "$MESSAGES/inbox/examplecafe_1002/audio/note_9001.aac"
    private const val REEL = "https://www.instagram.com/reel/XYZ123/"
    private const val OWNER = InstagramProfileFixtures.OWNER_NAME_AS_WRITTEN

    /** Three messages, newest first: received, sent, received; the first carries a photo, the last a reaction. */
    public val THREAD_JSON: String =
        thread(
            THREAD_TITLE,
            "inbox/exampleplayer_1001",
            listOf(PLAYER, OWNER),
            listOf(
                message(PLAYER, at(2), content("Sending the ticket now"), photo(PHOTO, FIRST_MESSAGE_TIME_MS / 1000)),
                message(OWNER, at(1), content("Yes, see you at 8")),
                message(PLAYER, at(0), content("Are you coming to the match tonight?"), reaction("❤", OWNER)),
            ),
        )

    /** A thread still waiting in message requests: two messages received, none sent. */
    public val REQUEST_THREAD_JSON: String =
        thread(
            COURIER,
            "message_requests/examplecourier_1003",
            listOf(COURIER, OWNER),
            listOf(
                message(COURIER, at(4), share(REEL)),
                message(COURIER, at(3), content("Your parcel is at the depot")),
            ),
        )

    /**
     * A group whose title needs mojibake repair: one message sent by the owner (whose name also
     * needs it), three received, and one with no time at all.
     */
    public val MOJIBAKE_THREAD_JSON: String =
        thread(
            MOJIBAKE_THREAD_TITLE,
            "inbox/examplecafe_1002",
            listOf(COACH, BAKER, OWNER),
            listOf(
                message(BAKER, at(8), audio(VOICE_NOTE, FIRST_MESSAGE_TIME_MS / 1000)),
                message(COACH, at(7), content("Training moved to Thursday")),
                message(OWNER, at(6), content("Croissants on me"), reaction("😂", COACH)),
                message(COACH, at(5), callDuration(312)),
                message(BAKER, 0, content("An undated message")),
            ),
        )

    /** Every word, path and name in the threads above that must never be stored, other than the thread titles. */
    public val PRIVATE_TEXT: List<String> =
        listOf(
            "Sending the ticket now",
            "Yes, see you at 8",
            "Are you coming to the match tonight?",
            "Your parcel is at the depot",
            "Training moved to Thursday",
            "Croissants on me",
            "An undated message",
            PHOTO,
            VOICE_NOTE,
            REEL,
            COACH,
            BAKER,
            InstagramProfileFixtures.OWNER_NAME,
            InstagramProfileFixtures.OWNER_NAME_AS_WRITTEN,
            InstagramProfileFixtures.OWNER_USERNAME,
        )

    public const val MALFORMED_THREAD_JSON: String = """{"participants": [], "messages": [{"sender_name": """

    private fun at(minute: Int): Long = FIRST_MESSAGE_TIME_MS + minute * MINUTE_MS
}
