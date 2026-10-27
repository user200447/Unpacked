package com.jassemsendi.unpacked.fixtures

/**
 * Builders for JSON text in the message-thread shape of a real Instagram Export: one file per
 * thread part, holding the participants, the messages newest first, and the thread's title and path.
 */
internal object InstagramMessageJson {
    fun thread(
        title: String,
        threadPath: String,
        participants: List<String>,
        messages: List<String>,
    ): String {
        val participantObjects = participants.joinToString(", ") { """{"name": "$it"}""" }
        return """
            {
              "participants": [$participantObjects],
              "messages": [
            ${messages.joinToString(",\n")}
              ],
              "title": "$title",
              "is_still_participant": true,
              "thread_path": "$threadPath",
              "magic_words": []
            }
            """.trimIndent()
    }

    /** One message: who sent it, when in epoch milliseconds, and whatever else Instagram attached ([extras]). */
    fun message(
        sender: String,
        timestampMs: Long,
        vararg extras: String,
    ): String {
        val attached = extras.joinToString("") { ", $it" }
        return """    {"sender_name": "$sender", "timestamp_ms": $timestampMs$attached, """ +
            """"is_geoblocked_for_viewer": false, "is_unsent_image_by_messenger_kid_parent": false}"""
    }

    fun content(text: String): String = """"content": "$text""""

    fun photo(
        uri: String,
        creationTimestamp: Long,
    ): String = """"photos": [{"uri": "$uri", "creation_timestamp": $creationTimestamp}]"""

    fun audio(
        uri: String,
        creationTimestamp: Long,
    ): String = """"audio_files": [{"uri": "$uri", "creation_timestamp": $creationTimestamp}]"""

    fun reaction(
        emoji: String,
        actor: String,
    ): String = """"reactions": [{"reaction": "$emoji", "actor": "$actor"}]"""

    fun share(link: String): String = """"share": {"link": "$link"}"""

    fun callDuration(seconds: Int): String = """"call_duration": $seconds"""
}
