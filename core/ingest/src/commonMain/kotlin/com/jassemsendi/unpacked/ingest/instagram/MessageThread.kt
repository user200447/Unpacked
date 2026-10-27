package com.jassemsendi.unpacked.ingest.instagram

import com.jassemsendi.unpacked.ingest.ExportFile
import com.jassemsendi.unpacked.ingest.FileOutcome
import com.jassemsendi.unpacked.ingest.InstagramAdapter
import com.jassemsendi.unpacked.ingest.Output
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Source
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One part of a message thread as the Export writes it, read only as far as the counts need: the
 * thread's title and each message's sender and time. Message text, media references and the
 * participant list are never deserialised, so they never exist in memory as model values (ADR 0003).
 */
@Serializable
internal data class MessageThread(
    val title: String? = null,
    val messages: List<Message> = emptyList(),
) {
    @Serializable
    internal data class Message(
        @SerialName("sender_name") val senderName: String? = null,
        @SerialName("timestamp_ms") val timestampMs: Long? = null,
    )
}

/**
 * Turns a thread part into one Event per dated message: MessageSent when the sender is the Export
 * owner, MessageReceived otherwise, naming the thread and carrying no attributes. Inbox threads and
 * message requests are read alike. The one file whose Kind is decided per record, not per file name.
 */
internal object MessageThreads {
    private val json = Json { ignoreUnknownKeys = true }
    private val threadFileName = Regex("message(_\\d+)?\\.json", RegexOption.IGNORE_CASE)

    fun isThreadFile(fileName: String): Boolean = threadFileName.matches(fileName)

    fun read(
        file: ExportFile,
        text: String,
        owner: ExportOwner?,
    ): FileOutcome =
        runCatching { json.decodeFromString<MessageThread>(text) }.fold(
            onSuccess = { thread -> count(file, thread, owner) },
            onFailure = { error ->
                FileOutcome(FileResult.Failed(file.path, file.rawKind, error.message ?: "could not decode the thread"))
            },
        )

    /** Without an owner no message can be told sent from received, so every one is counted and dropped, with the reason. */
    private fun count(
        file: ExportFile,
        thread: MessageThread,
        owner: ExportOwner?,
    ): FileOutcome {
        val messages = thread.messages
        if (owner == null) {
            return FileOutcome(file.parsed(messages.size, emptyList(), InstagramAdapter.OWNER_UNKNOWN))
        }
        val entity = file.threadEntity(thread.title)
        val events =
            messages.mapIndexedNotNull { index, message ->
                val time = epochToInstant(message.timestampMs) ?: return@mapIndexedNotNull null
                val sent = message.senderName?.let(::repairMojibake) == owner.name
                Event(
                    kind = if (sent) EventKind.MessageSent else EventKind.MessageReceived,
                    rawKind = file.rawKind,
                    time = time,
                    provenance = file.provenanceOf(index),
                    entities = listOf(entity),
                )
            }
        return FileOutcome(file.parsed(messages.size, events), Output(events = events))
    }

    private fun ExportFile.parsed(
        messageCount: Int,
        events: List<Event>,
        droppedReason: String? = null,
    ): FileResult.Parsed =
        FileResult.Parsed(
            path = path,
            rawKind = rawKind,
            recordCount = messageCount,
            eventCount = events.size,
            claimCount = 0,
            droppedCount = messageCount - events.size,
            droppedReason = droppedReason,
        )

    /**
     * The thread as an Entity: keyed by its folder, the Source's own stable id for the thread (the
     * last segment of its `thread_path`), so two threads with the same title stay apart; shown by
     * its title, repaired, or by that id when the Source gave no title.
     */
    private fun ExportFile.threadEntity(title: String?): Entity {
        val folder = path.substringBeforeLast('/', missingDelimiterValue = "").substringAfterLast('/')
        val displayName = title.orBlank()?.let(::repairMojibake) ?: folder
        return Entity(Source.Instagram, EntityKind.Thread, folder, displayName)
    }
}
