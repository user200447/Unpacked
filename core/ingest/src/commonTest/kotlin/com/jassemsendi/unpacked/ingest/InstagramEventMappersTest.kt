package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.fixtures.InstagramFixtures
import com.jassemsendi.unpacked.fixtures.InstagramStringDataFixtures
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
import kotlin.time.Instant

/** The Importer over hand-written files of each Instagram dialect: what every mapped file becomes. */
class InstagramEventMappersTest {
    private val fs = FakeFileSystem()
    private val root = "/exports/instagram-synthetic".toPath()

    private suspend fun importFile(
        path: String,
        json: String,
    ): Pair<FileResult, List<Event>> {
        fs.writeText(root / path.toPath(), json)
        val steps = Importer(InstagramAdapter).run(Export.directory(fs, root)).toList()
        val finished = steps.filterIsInstance<ImportStep.FileFinished>().single { it.outcome.result.path == path }
        return finished.outcome.result to finished.outcome.output.events
    }

    private fun account(
        username: String,
        url: String,
        displayName: String = username,
    ) = Entity(
        Source.Instagram,
        EntityKind.Account,
        username.lowercase(),
        displayName,
        mapOf("username" to username, "url" to url),
    )

    @Test
    fun followersListFileBecomesFollowEventsNamingTheFollowerAccount() =
        runTest {
            val (result, events) =
                importFile(InstagramStringDataFixtures.FOLLOWERS_PATH, InstagramStringDataFixtures.FOLLOWERS_JSON)

            assertEquals(
                FileResult.Parsed(
                    InstagramStringDataFixtures.FOLLOWERS_PATH,
                    RawKind("followers_1"),
                    recordCount = 3,
                    eventCount = 2,
                    claimCount = 0,
                    droppedCount = 1,
                ),
                result,
            )
            assertEquals(listOf(EventKind.Follow, EventKind.Follow), events.map { it.kind })
            assertEquals(listOf(0, 1), events.map { it.provenance.recordIndex })
            assertEquals(
                Instant.fromEpochSeconds(InstagramStringDataFixtures.FIRST_FOLLOW_TIME_SECONDS),
                events[0].time,
            )
            assertEquals(
                listOf(account("Amira_Ayari", "https://www.instagram.com/Amira_Ayari")),
                events[0].entities,
            )
            assertEquals(
                listOf(account("basma.example", "https://www.threads.com/basma.example", "Basma Example")),
                events[1].entities,
            )
            assertEquals("instagram:account:amira_ayari", events[0].entities.single().id)
            assertEquals(
                mapOf("Value" to "Amira_Ayari", "Href" to "https://www.instagram.com/Amira_Ayari"),
                events[0].attributes,
            )
        }

    @Test
    fun followingContainerFileBecomesFollowEventsNamedByTitle() =
        runTest {
            val (result, events) =
                importFile(InstagramStringDataFixtures.FOLLOWING_PATH, InstagramStringDataFixtures.FOLLOWING_JSON)

            assertEquals(2, (result as FileResult.Parsed).eventCount)
            assertEquals(
                listOf(
                    account("Myriem.Example", "https://www.instagram.com/_u/myriem.example"),
                    account("aziz_example", "https://www.instagram.com/_u/aziz_example"),
                ),
                events.map { it.entities.single() },
            )
            assertEquals(
                mapOf("Title" to "Myriem.Example", "Href" to "https://www.instagram.com/_u/myriem.example"),
                events[0].attributes,
            )
        }

    @Test
    fun loginActivityMapFileBecomesLoginEventsNamingTheDeviceByItsUserAgent() =
        runTest {
            val (result, events) =
                importFile(
                    InstagramStringDataFixtures.LOGIN_ACTIVITY_PATH,
                    InstagramStringDataFixtures.LOGIN_ACTIVITY_JSON,
                )

            assertEquals(3, (result as FileResult.Parsed).eventCount)
            assertEquals(0, result.droppedCount)
            assertEquals(List(3) { EventKind.Login }, events.map { it.kind })
            assertEquals(Instant.fromEpochSeconds(InstagramStringDataFixtures.FIRST_LOGIN_TIME_SECONDS), events[0].time)
            val phone = events[0].entities.single()
            assertEquals(EntityKind.Device, phone.kind)
            assertEquals(InstagramStringDataFixtures.PHONE_USER_AGENT, phone.displayName)
            assertEquals(phone, events[2].entities.single(), "the same user agent is the same device")
            assertEquals(InstagramStringDataFixtures.LAPTOP_USER_AGENT, events[1].entities.single().displayName)
            assertEquals("2026-10-08T12:43:40Z", events[0].attributes["Time"], "a dated entry reads as an instant")
            assertEquals("203.0.113.7", events[0].attributes["IP Address"])
            assertEquals(null, events[0].attributes["Title"], "an empty title is dropped")
        }

    @Test
    fun secondsAndMillisecondsFromDifferentFilesMapToTheSameInstant() =
        runTest {
            fs.writeText(
                root / InstagramStringDataFixtures.LOGIN_ACTIVITY_PATH.toPath(),
                InstagramStringDataFixtures.LOGIN_ACTIVITY_JSON,
            )
            val (_, logouts) =
                importFile(
                    InstagramStringDataFixtures.LOGOUT_ACTIVITY_PATH,
                    InstagramStringDataFixtures.LOGOUT_ACTIVITY_JSON,
                )

            val logout = logouts.single()
            assertEquals(EventKind.Logout, logout.kind)
            assertEquals(Instant.fromEpochSeconds(InstagramStringDataFixtures.FIRST_LOGIN_TIME_SECONDS), logout.time)
            assertEquals(InstagramStringDataFixtures.LAPTOP_USER_AGENT, logout.entities.single().displayName)
        }

    @Test
    fun postsViewedBecomeContentViewedNamingTheAuthorAccountAndEachHashtagTopic() =
        runTest {
            val (result, events) = importFile(InstagramFixtures.POSTS_VIEWED_PATH, InstagramFixtures.POSTS_VIEWED_JSON)

            assertEquals(2, (result as FileResult.Parsed).eventCount)
            assertEquals(listOf(EventKind.ContentViewed, EventKind.ContentViewed), events.map { it.kind })
            assertEquals(
                listOf("instagram:account:example.player", "instagram:topic:football", "instagram:topic:explore"),
                events[0].entities.map { it.id },
            )
            assertEquals("Example Player", events[0].entities[0].displayName)
            assertEquals("#Football", events[0].entities[1].displayName)
            assertEquals(EntityKind.Topic, events[0].entities[1].kind)
            assertEquals(emptyList(), events[1].entities, "no owner and no hashtags name nothing")
        }
}
