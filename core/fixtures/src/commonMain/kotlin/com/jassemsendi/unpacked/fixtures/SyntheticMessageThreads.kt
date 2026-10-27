package com.jassemsendi.unpacked.fixtures

import com.jassemsendi.unpacked.fixtures.InstagramMessageJson.content
import com.jassemsendi.unpacked.fixtures.InstagramMessageJson.message
import com.jassemsendi.unpacked.fixtures.InstagramMessageJson.photo
import com.jassemsendi.unpacked.fixtures.InstagramMessageJson.reaction
import com.jassemsendi.unpacked.fixtures.InstagramMessageJson.thread

/**
 * One thread of the synthetic Export and what importing it must count, decided against the
 * owner's name. [key] is the Entity key the thread gets: the id its folder name ends in.
 */
public data class SyntheticThread(
    val folder: String,
    val key: String,
    val title: String,
    val sent: Int,
    val received: Int,
) {
    /** The `thread_path` the Source writes inside the file: the folder relative to the messages folder. */
    val threadPath: String get() = folder.substringAfter("/messages/")
}

/**
 * The message threads of the synthetic Export: an inbox thread split into two parts, a group
 * whose title needs mojibake repair, and a message request, plus the media inside their folders.
 * Counts are fixed so a test can state the expected sent and received Events per thread by hand.
 */
internal object SyntheticMessageThreads {
    private const val MESSAGES = "your_instagram_activity/messages"
    private const val OWNER = InstagramProfileFixtures.OWNER_NAME_AS_WRITTEN
    private const val PLAYER = "Example Player"
    private const val COACH = "Example Coach"
    private const val BAKER = "Example Baker"
    private const val COURIER = "Example Courier"
    private const val MILLISECOND_IN_SECOND = 1_000L

    val threads: List<SyntheticThread> =
        listOf(
            SyntheticThread("$MESSAGES/inbox/exampleplayer_1001", "1001", PLAYER, sent = 4, received = 2),
            SyntheticThread(
                "$MESSAGES/inbox/examplecafe_1002",
                "1002",
                InstagramMessageFixtures.REPAIRED_THREAD_TITLE,
                sent = 1,
                received = 4,
            ),
            SyntheticThread(
                "$MESSAGES/message_requests/examplecourier_1003",
                "1003",
                COURIER,
                sent = 0,
                received = 2,
            ),
        )

    /** Media the threads attached, inside their own folders; one has no extension, as the real Export has. */
    val mediaPaths: List<String> =
        listOf(
            "${threads[0].folder}/photos/1062933361981631.jpg",
            "${threads[0].folder}/photos/1066712851843908",
            "${threads[1].folder}/videos/1039734214128312.mp4",
            "${threads[1].folder}/audio/1057182629448480.aac",
        )

    /** Every thread part by path, with its JSON text, given the instant in seconds the first message happens at. */
    fun all(firstTime: Long): Map<String, String> {
        val t = Ticker(firstTime)
        val (player, cafe, courier) = threads
        return mapOf(
            "${player.folder}/message_1.json" to
                thread(
                    player.title,
                    player.threadPath,
                    listOf(PLAYER, OWNER),
                    newestFirst(
                        message(OWNER, ms(t), content("On my way")),
                        message(PLAYER, ms(t), content("Where are you?"), reaction("❤", OWNER)),
                        message(OWNER, ms(t), content("Yes, see you at 8")),
                        message(PLAYER, ms(t), content("Are you coming to the match tonight?")),
                        message(OWNER, ms(t), photo(mediaPaths[0], t.next())),
                    ),
                ),
            "${player.folder}/message_2.json" to
                thread(player.title, player.threadPath, listOf(PLAYER, OWNER), listOf(message(OWNER, ms(t)))),
            "${cafe.folder}/message_1.json" to
                thread(
                    InstagramMessageFixtures.MOJIBAKE_THREAD_TITLE,
                    cafe.threadPath,
                    listOf(COACH, BAKER, OWNER),
                    newestFirst(
                        message(BAKER, ms(t), content("Croissants are ready")),
                        message(COACH, ms(t), content("Training moved to Thursday")),
                        message(OWNER, ms(t), content("Croissants on me"), reaction("😂", COACH)),
                        message(COACH, ms(t), content("Who is coming?")),
                        message(BAKER, ms(t), photo(mediaPaths[1], t.next())),
                    ),
                ),
            "${courier.folder}/message_1.json" to
                thread(
                    courier.title,
                    courier.threadPath,
                    listOf(COURIER, OWNER),
                    newestFirst(
                        message(COURIER, ms(t), content("Your parcel is at the depot")),
                        message(COURIER, ms(t), content("Hello, this is your courier")),
                    ),
                ),
        )
    }

    /** The real Export lists messages newest first; the builder is called oldest first so the Ticker reads naturally. */
    private fun newestFirst(vararg messages: String): List<String> = messages.reversed()

    private fun ms(t: Ticker): Long = t.next() * MILLISECOND_IN_SECOND + MILLISECOND_IN_SECOND / 2
}
