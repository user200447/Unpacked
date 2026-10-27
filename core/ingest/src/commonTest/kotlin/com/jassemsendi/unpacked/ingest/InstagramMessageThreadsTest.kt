package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.fixtures.InstagramMessageFixtures
import com.jassemsendi.unpacked.fixtures.InstagramProfileFixtures
import com.jassemsendi.unpacked.fixtures.writeText
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The Importer over hand-written message threads: counts only, decided against the Export owner (ADR 0003). */
class InstagramMessageThreadsTest {
    private val fs = FakeFileSystem()
    private val root = "/exports/instagram-synthetic".toPath()

    private fun writeOwnerProfile() {
        fs.writeText(
            root / InstagramProfileFixtures.PERSONAL_INFORMATION_PATH.toPath(),
            InstagramProfileFixtures.PERSONAL_INFORMATION_JSON,
        )
    }

    private suspend fun importFile(
        path: String,
        json: String,
    ): Pair<FileResult, List<Event>> {
        fs.writeText(root / path.toPath(), json)
        val steps = Importer(InstagramAdapter).run(Export.directory(fs, root)).toList()
        val finished = steps.filterIsInstance<ImportStep.FileFinished>().single { it.outcome.result.path == path }
        return finished.outcome.result to finished.outcome.output.events
    }

    @Test
    fun aThreadBecomesOneEventPerMessageSentOrReceivedAgainstTheOwnerNamingOnlyTheThread() =
        runTest {
            writeOwnerProfile()

            val (result, events) =
                importFile(
                    InstagramMessageFixtures.THREAD_PATH,
                    InstagramMessageFixtures.THREAD_JSON,
                )

            assertEquals(
                FileResult.Parsed(
                    InstagramMessageFixtures.THREAD_PATH,
                    RawKind("message_1"),
                    recordCount = 3,
                    eventCount = 3,
                    claimCount = 0,
                ),
                result,
            )
            assertEquals(
                listOf(EventKind.MessageReceived, EventKind.MessageSent, EventKind.MessageReceived),
                events.map { it.kind },
                "newest first, as the file lists them",
            )
            assertEquals(listOf(0, 1, 2), events.map { it.provenance.recordIndex })
            assertEquals(
                Instant.fromEpochMilliseconds(InstagramMessageFixtures.FIRST_MESSAGE_TIME_MS),
                events[2].time,
                "epoch milliseconds, kept to the millisecond",
            )
            val thread =
                Entity(
                    Source.Instagram,
                    EntityKind.Thread,
                    InstagramMessageFixtures.THREAD_KEY,
                    InstagramMessageFixtures.THREAD_TITLE,
                )
            assertTrue(events.all { it.entities == listOf(thread) }, "every message names the thread and nothing else")
            assertEquals("instagram:thread:exampleplayer_1001", thread.id)
            assertTrue(events.all { it.attributes.isEmpty() }, "a message Event carries no attributes at all")
        }

    @Test
    fun aMessageRequestThreadIsReadLikeAnInboxThread() =
        runTest {
            writeOwnerProfile()

            val (result, events) =
                importFile(InstagramMessageFixtures.REQUEST_THREAD_PATH, InstagramMessageFixtures.REQUEST_THREAD_JSON)

            assertEquals(2, (result as FileResult.Parsed).eventCount)
            assertEquals(List(2) { EventKind.MessageReceived }, events.map { it.kind })
            assertEquals(
                "instagram:thread:${InstagramMessageFixtures.REQUEST_THREAD_KEY}",
                events[0].entities.single().id,
            )
        }

    @Test
    fun mojibakeInTheTitleAndInTheSenderIsRepairedBeforeTheOwnerIsCompared() =
        runTest {
            writeOwnerProfile()

            val (result, events) =
                importFile(InstagramMessageFixtures.MOJIBAKE_THREAD_PATH, InstagramMessageFixtures.MOJIBAKE_THREAD_JSON)

            assertEquals(
                FileResult.Parsed(
                    InstagramMessageFixtures.MOJIBAKE_THREAD_PATH,
                    RawKind("message_1"),
                    recordCount = 5,
                    eventCount = 4,
                    claimCount = 0,
                    droppedCount = 1,
                ),
                result,
                "the undated message is dropped, not failed",
            )
            assertEquals(
                mapOf(EventKind.MessageSent to 1, EventKind.MessageReceived to 3),
                events.groupingBy { it.kind }.eachCount(),
            )
            assertEquals(InstagramMessageFixtures.REPAIRED_THREAD_TITLE, events[0].entities.single().displayName)
        }

    @Test
    fun withoutTheOwnersProfileMessagesAreCountedAndDroppedWithAReasonAndNothingFails() =
        runTest {
            val (result, events) =
                importFile(
                    InstagramMessageFixtures.THREAD_PATH,
                    InstagramMessageFixtures.THREAD_JSON,
                )

            assertEquals(
                FileResult.Parsed(
                    InstagramMessageFixtures.THREAD_PATH,
                    RawKind("message_1"),
                    recordCount = 3,
                    eventCount = 0,
                    claimCount = 0,
                    droppedCount = 3,
                    droppedReason = InstagramAdapter.OWNER_UNKNOWN,
                ),
                result,
            )
            assertEquals(emptyList(), events)
        }

    @Test
    fun aMalformedThreadIsFailedWithAReason() =
        runTest {
            writeOwnerProfile()

            val (result, events) =
                importFile(InstagramMessageFixtures.THREAD_PATH, InstagramMessageFixtures.MALFORMED_THREAD_JSON)

            val failed = assertIs<FileResult.Failed>(result)
            assertTrue(failed.reason.isNotBlank())
            assertEquals(emptyList(), events)
        }

    @Test
    fun noMessageTextMediaPathOrParticipantNameReachesAnEventOrAnEntity() =
        runTest {
            writeOwnerProfile()
            fs.writeText(root / InstagramMessageFixtures.THREAD_PATH.toPath(), InstagramMessageFixtures.THREAD_JSON)
            fs.writeText(
                root / InstagramMessageFixtures.REQUEST_THREAD_PATH.toPath(),
                InstagramMessageFixtures.REQUEST_THREAD_JSON,
            )
            fs.writeText(
                root / InstagramMessageFixtures.MOJIBAKE_THREAD_PATH.toPath(),
                InstagramMessageFixtures.MOJIBAKE_THREAD_JSON,
            )

            val steps = Importer(InstagramAdapter).run(Export.directory(fs, root)).toList()
            val events =
                steps
                    .filterIsInstance<ImportStep.FileFinished>()
                    .flatMap { it.outcome.output.events }
                    .filter { it.kind == EventKind.MessageSent || it.kind == EventKind.MessageReceived }

            assertEquals(9, events.size)
            val stored =
                events.flatMap { event ->
                    event.attributes.keys + event.attributes.values +
                        event.entities.flatMap {
                            listOf(it.key, it.displayName) + it.attributes.keys +
                                it.attributes.values
                        }
                }
            for (text in InstagramMessageFixtures.PRIVATE_TEXT) {
                assertFalse(stored.any { it.contains(text) }, "'$text' must not be stored")
            }
            assertEquals(emptySet(), events.flatMap { it.entities }.flatMap { it.attributes.keys }.toSet())
        }
}
