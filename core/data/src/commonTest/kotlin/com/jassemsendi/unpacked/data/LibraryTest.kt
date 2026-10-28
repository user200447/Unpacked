package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.fixtures.InstagramClaimFixtures
import com.jassemsendi.unpacked.fixtures.InstagramFixtures
import com.jassemsendi.unpacked.fixtures.InstagramMessageFixtures
import com.jassemsendi.unpacked.fixtures.SyntheticInstagramExport
import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.fixtures.writeText
import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.ingest.ExportError
import com.jassemsendi.unpacked.ingest.ExportLocation
import com.jassemsendi.unpacked.ingest.ImportError
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.ingest.InstagramAdapter
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.Inventory
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Seam 1: a synthetic Export in, a library out, asserted only through the repository interfaces.
 * Abstract because opening an in-memory library differs per platform; each target has a one-line subclass.
 * One class by design, however large it grows: every repository is asserted against the same imported library.
 */
@Suppress("LargeClass")
abstract class LibraryTest {
    private val fs = FakeFileSystem()
    private val root = "/exports/instagram-synthetic".toPath()
    private val clock = TickingClock()
    private val pageSize = 20
    private lateinit var library: Library
    private val notFound = Result.Error(DataError.Local.NOT_FOUND)

    protected abstract fun openLibrary(clock: Clock): Library

    @BeforeTest
    fun open() {
        library = openLibrary(clock)
    }

    @AfterTest
    fun close() = library.close()

    /** Every page of the timeline in turn, until one comes back empty. */
    private suspend fun readEveryPage(): List<List<Event>> =
        buildList {
            var page = library.events.newestFirst(limit = pageSize, offset = 0).orFail()
            while (page.isNotEmpty()) {
                add(page)
                page = library.events.newestFirst(limit = pageSize, offset = size * pageSize).orFail()
            }
        }

    /** Every page of a search in turn, until one comes back empty. */
    private suspend fun searchEveryPage(text: String): List<List<SearchHit>> =
        buildList {
            var page = library.search.search(text, limit = pageSize, offset = 0).orFail()
            while (page.isNotEmpty()) {
                add(page)
                page = library.search.search(text, limit = pageSize, offset = size * pageSize).orFail()
            }
        }

    /** Whether any of these texts has a word that starts with [word], the way the search reads them. */
    private fun Iterable<String>.mention(word: String): Boolean =
        any { text -> text.split(nonWord).any { it.startsWith(word, ignoreCase = true) } }

    private fun Event.mentions(word: String) = (attributes.values + entities.map { it.displayName }).mention(word)

    private fun Claim.mentions(word: String) = (attributes.values + entities.map { it.displayName }).mention(word)

    private suspend fun import(export: Export): Import {
        val steps = library.import(export, InstagramAdapter).toList()
        return assertIs<ImportStep.Stored>(steps.last()).import
    }

    @Test
    fun importedExportIsCountedPerKindAndItsFilesAreListedWithReasons() =
        runTest {
            SyntheticInstagramExport(adsViewed = 4, mediaFiles = 2, otherFiles = 1).writeTo(fs, root)

            val import = import(Export.directory(fs, root))

            assertEquals(mapOf(EventKind.AdShown to 4), library.events.countByKind(import.id).orFail())
            assertEquals(emptyMap(), library.claims.countByKind(import.id).orFail())
            assertEquals(import, library.imports.get(import.id).orFail())
            assertEquals(listOf(import), library.imports.all().orFail())
            assertEquals(ExportId("instagram-synthetic"), import.summary.exportId)
            assertEquals(Source.Instagram, import.summary.source)
            assertEquals(Inventory(jsonFiles = 2, mediaFiles = 2, otherFiles = 1), import.summary.inventory)
            assertEquals(
                listOf(
                    FileResult.Parsed(InstagramFixtures.ADS_VIEWED_PATH, RawKind("ads_viewed"), 4, 4, 0),
                    FileResult.Skipped(
                        InstagramFixtures.UNMAPPED_PATH,
                        RawKind("subscription_for_no_ads"),
                        "no mapper registered for subscription_for_no_ads.json",
                    ),
                    FileResult.Skipped("personal_information/notes_0.txt", RawKind("notes_0"), "not a JSON file"),
                ),
                import.summary.files,
            )
            assertEquals(TickingClock.START, import.startedAt)
            assertEquals(TickingClock.START + TickingClock.TICK, import.completedAt)
        }

    @Test
    fun theFullSyntheticExportLandsWithEveryEventKindCountedAndNoFailedFile() =
        runTest {
            SyntheticInstagramExport(adsViewed = 5, includeTimedFiles = true).writeTo(fs, root)

            val import = import(Export.directory(fs, root))

            assertEquals(emptyList(), import.summary.failed)
            assertEquals(
                mapOf(
                    EventKind.AdShown to 5,
                    EventKind.ContentViewed to 9,
                    EventKind.ContentLiked to 10,
                    EventKind.Login to 3,
                    EventKind.Logout to 2,
                    EventKind.Follow to 10,
                    EventKind.Unfollow to 2,
                    EventKind.Search to 4,
                    EventKind.LinkVisited to 2,
                    EventKind.Other to 10,
                ),
                library.events.countByKind(import.id).orFail(),
            )
            val follows =
                library.events
                    .forImport(import.id)
                    .orFail()
                    .filter { it.kind == EventKind.Follow }
            assertTrue(follows.all { event -> event.entities.any { it.id.startsWith("instagram:account:") } })
            assertTrue(
                library.entities
                    .all()
                    .orFail()
                    .any { it.kind == EntityKind.Device },
                "logins name their devices",
            )
        }

    @Test
    fun theUntimedFilesLandAsClaimsCountedPerKindAndNeverAsEvents() =
        runTest {
            SyntheticInstagramExport(adsViewed = 2, includeClaimFiles = true).writeTo(fs, root)

            val import = import(Export.directory(fs, root))

            assertEquals(emptyList(), import.summary.failed)
            assertEquals(
                mapOf(
                    ClaimKind.AdvertiserHoldsContact to 4,
                    ClaimKind.InferredInterest to 8,
                    ClaimKind.InferredLocation to 5,
                    ClaimKind.ReachCategory to 3,
                    ClaimKind.ConsentGiven to 1,
                    ClaimKind.ProfileField to 4,
                    ClaimKind.ContactSynced to 3,
                    ClaimKind.Other to 14,
                ),
                library.claims.countByKind(import.id).orFail(),
            )
            assertEquals(mapOf(EventKind.AdShown to 2), library.events.countByKind(import.id).orFail())
            val claims = library.claims.forImport(import.id).orFail()
            assertEquals(42, claims.size)
            val advertisers = claims.filter { it.kind == ClaimKind.AdvertiserHoldsContact }
            assertTrue(advertisers.all { claim -> claim.entities.single().kind == EntityKind.Advertiser })
            assertEquals(4, advertisers.map { it.entities.single().id }.distinct().size)
            val consent = claims.single { it.kind == ClaimKind.ConsentGiven }
            assertNotNull(consent.asOf)
            assertEquals(consent, library.claims.byProvenance(consent.provenance).orFail())
            assertEquals(notFound, library.events.byProvenance(consent.provenance), "a dated Claim is no Event")
            assertEquals(
                setOf(
                    InstagramClaimFixtures.REPAIRED_NAME,
                    InstagramClaimFixtures.ARABIC_NAME,
                    InstagramClaimFixtures.LATIN1_NAME,
                ),
                claims
                    .filter { it.kind == ClaimKind.ContactSynced }
                    .map {
                        it.attributes.getValue(
                            "First Name",
                        )
                    }.toSet(),
            )
            assertTrue(
                library.entities
                    .all()
                    .orFail()
                    .any { it.kind == EntityKind.Place },
                "locations name their places",
            )
        }

    @Test
    fun messageThreadsLandAsSentAndReceivedCountsNamingTheThreadAndStoringNoText() =
        runTest {
            SyntheticInstagramExport(adsViewed = 1, includeMessageThreads = true, mediaFiles = 2).writeTo(fs, root)

            val import = import(Export.directory(fs, root))

            assertEquals(emptyList(), import.summary.failed)
            val threads = SyntheticInstagramExport.MESSAGE_THREADS
            assertEquals(
                mapOf(
                    EventKind.AdShown to 1,
                    EventKind.MessageSent to threads.sumOf { it.sent },
                    EventKind.MessageReceived to threads.sumOf { it.received },
                ),
                library.events.countByKind(import.id).orFail(),
            )
            val messages =
                library.events
                    .forImport(import.id)
                    .orFail()
                    .filter { it.kind != EventKind.AdShown }
            assertTrue(messages.all { it.attributes.isEmpty() }, "a message Event stores nothing but its counts")
            assertTrue(messages.all { it.entities.single().kind == EntityKind.Thread })
            assertEquals(
                threads.map { it.title }.toSet(),
                library.entities
                    .all()
                    .orFail()
                    .filter { it.kind == EntityKind.Thread }
                    .map { it.displayName }
                    .toSet(),
            )
            assertEquals(
                2 + SyntheticInstagramExport.THREAD_MEDIA_PATHS.size,
                import.summary.inventory.mediaFiles,
                "the media inside thread folders is counted",
            )
        }

    @Test
    fun aThreadWhoseOwnerIsUnknownIsStoredAsParsedWithItsDroppedReason() =
        runTest {
            fs.writeText(root / InstagramMessageFixtures.THREAD_PATH.toPath(), InstagramMessageFixtures.THREAD_JSON)

            val import = import(Export.directory(fs, root))

            assertEquals(
                listOf(
                    FileResult.Parsed(
                        InstagramMessageFixtures.THREAD_PATH,
                        RawKind("message_1"),
                        recordCount = 3,
                        eventCount = 0,
                        claimCount = 0,
                        droppedCount = 3,
                        droppedReason = InstagramAdapter.OWNER_UNKNOWN,
                    ),
                ),
                import.summary.files,
            )
            assertEquals(emptyMap(), library.events.countByKind(import.id).orFail())
        }

    @Test
    fun aClaimReadsBackWithItsAsOfTimeAttributesAndProvenanceAndNoEventQueryReturnsIt() =
        runTest {
            fs.writeText(root / InstagramClaimFixtures.CONSENTS_PATH.toPath(), InstagramClaimFixtures.CONSENTS_JSON)
            val import = import(Export.directory(fs, root))
            val provenance = Provenance(ExportId("instagram-synthetic"), InstagramClaimFixtures.CONSENTS_PATH, 0)

            val claim = library.claims.byProvenance(provenance).orFail()

            assertEquals(ClaimKind.ConsentGiven, claim.kind)
            assertEquals(RawKind("consents"), claim.rawKind)
            assertEquals(provenance, claim.provenance)
            assertEquals(Instant.fromEpochSeconds(InstagramClaimFixtures.CONSENT_TIME_SECONDS), claim.asOf)
            assertEquals(
                mapOf(
                    InstagramClaimFixtures.CONSENT_LABEL to "",
                    "Update time" to InstagramClaimFixtures.CONSENT_TIME_ISO,
                ),
                claim.attributes,
            )
            assertEquals(mapOf(ClaimKind.ConsentGiven to 1), library.claims.countByKind(import.id).orFail())
            assertEquals(listOf(claim), library.claims.forImport(import.id).orFail())
            assertEquals(notFound, library.events.byProvenance(provenance))
            assertEquals(emptyMap(), library.events.countByKind(import.id).orFail())
            assertEquals(emptyList(), library.events.forImport(import.id).orFail())
        }

    @Test
    fun oneRecordListingSeveralAdvertisersStoresOneClaimPerItemFoundByItsItemIndex() =
        runTest {
            fs.writeText(
                root / InstagramClaimFixtures.ADVERTISERS_PATH.toPath(),
                InstagramClaimFixtures.ADVERTISERS_JSON,
            )
            val import = import(Export.directory(fs, root))
            val record = Provenance(ExportId("instagram-synthetic"), InstagramClaimFixtures.ADVERTISERS_PATH, 0)

            assertEquals(mapOf(ClaimKind.AdvertiserHoldsContact to 3), library.claims.countByKind(import.id).orFail())
            val second = library.claims.byProvenance(record.copy(itemIndex = 1)).orFail()
            assertEquals("Smith, Jones & Co", second.attributes.getValue(InstagramClaimFixtures.ADVERTISERS_LABEL))
            assertEquals("instagram:advertiser:smith, jones & co", second.entities.single().id)
            assertEquals(
                listOf(0, 1, 2),
                library.claims
                    .forImport(import.id)
                    .orFail()
                    .map { it.provenance.itemIndex },
            )
            assertEquals(notFound, library.claims.byProvenance(record.copy(itemIndex = 3)))
            assertEquals(
                3,
                library.entities
                    .all()
                    .orFail()
                    .count { it.kind == EntityKind.Advertiser },
            )
        }

    @Test
    fun theImportUseCaseImportsTheExportAtAPickedLocationAndEndsFailedWhenThereIsNoneThere() =
        runTest {
            SyntheticInstagramExport(adsViewed = 3, mediaFiles = 1).writeTo(fs, root)
            val useCase = LibraryImportUseCase(library, fs)

            val steps = useCase.run(ExportLocation.Folder(root.toString())).toList()

            val stored = assertIs<ImportStep.Stored>(steps.last()).import
            assertIs<ImportStep.Started>(steps.first())
            assertEquals(ExportId("instagram-synthetic"), stored.summary.exportId)
            assertEquals(mapOf(EventKind.AdShown to 3), library.events.countByKind(stored.id).orFail())
            assertEquals(listOf(stored), library.imports.all().orFail())
            assertEquals(
                listOf(ImportStep.Failed(ImportError.Reading(ExportError.NOT_FOUND))),
                useCase.run(ExportLocation.Zip("/nowhere/missing.zip")).toList(),
            )
            assertEquals(
                listOf(stored),
                library.imports.all().orFail(),
                "a location that will not open records no Import",
            )
        }

    @Test
    fun aFileThatCannotBeReadIsRecordedAsFailedWithItsReasonAndTheRestStillLands() =
        runTest {
            SyntheticInstagramExport(adsViewed = 2, malformedAdsViewed = true, mediaFiles = 1).writeTo(fs, root)

            val import = import(Export.directory(fs, root))

            val failed = import.summary.failed.single()
            assertEquals(InstagramFixtures.ADS_VIEWED_PATH, failed.path)
            assertEquals(RawKind("ads_viewed"), failed.rawKind)
            assertTrue(failed.reason.isNotBlank())
            assertEquals(1, import.summary.skipped.size)
            assertEquals(emptyMap(), library.events.countByKind(import.id).orFail())
            assertEquals(Inventory(jsonFiles = 2, mediaFiles = 1, otherFiles = 0), import.summary.inventory)
            assertEquals(import, library.imports.get(import.id).orFail(), "the Failed entry and its reason are stored")
        }

    @Test
    fun anEventReadsBackWithItsAttributesRawKindProvenanceAndEntities() =
        runTest {
            fs.writeText(root / InstagramFixtures.ADS_VIEWED_PATH.toPath(), InstagramFixtures.ADS_VIEWED_JSON)
            val import = import(Export.directory(fs, root))
            val provenance = Provenance(ExportId("instagram-synthetic"), InstagramFixtures.ADS_VIEWED_PATH, 1)

            val event = library.events.byProvenance(provenance).orFail()

            assertEquals(EventKind.AdShown, event.kind)
            assertEquals(RawKind("ads_viewed"), event.rawKind)
            assertEquals(provenance, event.provenance)
            assertEquals(Instant.fromEpochSeconds(InstagramFixtures.FIRST_AD_TIME_SECONDS) + 1.minutes, event.time)
            assertEquals(
                listOf("URL", "Caption", "Hashtags / Name", "Owner / URL", "Owner / Name", "Owner / Username"),
                event.attributes.keys.toList(),
                "attribute order follows the record",
            )
            assertEquals("Ship anything #logistics #growth", event.attributes["Caption"])
            assertEquals("logistics, growth", event.attributes["Hashtags / Name"])
            assertEquals(
                listOf("instagram:advertiser:examplecourier", "instagram:topic:logistics", "instagram:topic:growth"),
                event.entities.map { it.id },
                "entity order follows the record: the advertiser, then each hashtag as a Topic",
            )
            val advertiser = event.entities.first()
            assertEquals(EntityKind.Advertiser, advertiser.kind)
            assertEquals("Example Courier", advertiser.displayName)
            assertEquals(
                mapOf("username" to "ExampleCourier", "url" to "http://example.com/courier"),
                advertiser.attributes,
            )
            assertEquals(advertiser, library.entities.byId(advertiser.id).orFail())
            assertEquals(
                notFound,
                library.events.byProvenance(provenance.copy(recordIndex = 2)),
                "the undated record is dropped",
            )
            assertEquals(
                listOf(0, 1),
                library.events
                    .forImport(import.id)
                    .orFail()
                    .map { it.provenance.recordIndex },
            )
        }

    @Test
    fun theTimelineReadsEveryEventNewestFirstInPagesNarrowedToOneKindAndNeverAClaim() =
        runTest {
            SyntheticInstagramExport(
                adsViewed = 7,
                includeTimedFiles = true,
                includeClaimFiles = true,
            ).writeTo(fs, root)
            val import = import(Export.directory(fs, root))
            val all = library.events.forImport(import.id).orFail()
            val newestFirst = all.sortedWith(compareByDescending<Event> { it.time }.thenByDescending { it.id })
            assertTrue(all.size > pageSize, "the fixture must span more than one page")

            val read = readEveryPage()

            assertTrue(read.dropLast(1).all { it.size == pageSize }, "every page but the last is full")
            assertEquals(newestFirst, read.flatten())
            assertEquals(
                newestFirst.filter { it.kind == EventKind.ContentLiked },
                library.events.newestFirst(kind = EventKind.ContentLiked, limit = all.size, offset = 0).orFail(),
            )
            assertEquals(
                library.events
                    .countByKind()
                    .orFail()
                    .getValue(EventKind.ContentLiked),
                library.events
                    .newestFirst(kind = EventKind.ContentLiked, limit = all.size, offset = 0)
                    .orFail()
                    .size,
            )
            assertEquals(emptyList(), library.events.newestFirst(limit = pageSize, offset = all.size).orFail())
            val claims = library.claims.forImport(import.id).orFail()
            assertTrue(claims.any { it.asOf != null }, "the fixture must hold dated Claims")
            assertTrue(newestFirst.none { event -> claims.any { it.provenance == event.provenance } })
        }

    @Test
    fun searchFindsTheEventsAndClaimsThatMentionAWordNewestFirstWithUndatedClaimsLast() =
        runTest {
            val generator = SyntheticInstagramExport(adsViewed = 8, includeTimedFiles = true, includeClaimFiles = true)
            generator.writeTo(fs, root)
            val import = import(Export.directory(fs, root))
            val events = library.events.forImport(import.id).orFail()
            val claims = library.claims.forImport(import.id).orFail()
            assertTrue(generator.ads.any { it.name == "Example Hotels" }, "the fixture must show an Example Hotels ad")

            val hits = library.search.search("hotels", limit = pageSize, offset = 0).orFail()

            assertEquals(
                events.filter { it.mentions("hotels") }.sortedByDescending { it.time }.map { SearchHit.OfEvent(it) },
                hits.filterIsInstance<SearchHit.OfEvent>(),
            )
            assertEquals(
                claims.filter { it.mentions("hotels") }.map { SearchHit.OfClaim(it) },
                hits.filterIsInstance<SearchHit.OfClaim>(),
                "the advertisers list names Example Hotels in a Claim",
            )
            assertTrue(hits.last() is SearchHit.OfClaim && hits.last().time == null, "the undated Claim sorts last")
            val found = hits.filterIsInstance<SearchHit.OfClaim>().single()
            assertEquals(found.claim, library.claims.byProvenance(found.provenance).orFail(), "a hit opens its Record")

            val everything = searchEveryPage("example").flatten()

            assertTrue(everything.size > pageSize, "the fixture must span more than one page")
            assertEquals(
                events.count { it.mentions("example") } + claims.count { it.mentions("example") },
                everything.size,
            )
            assertEquals(everything.distinct(), everything, "no page repeats a hit")
            val dated = everything.takeWhile { it.time != null }
            assertTrue(everything.drop(dated.size).all { it.time == null }, "undated Claims come after every dated hit")
            assertEquals(
                dated.map { it.time },
                dated.map { it.time }.sortedByDescending { it },
                "dated hits read newest first",
            )
            assertTrue(dated.first() is SearchHit.OfClaim, "the dated Claims are the newest records in the fixture")
            assertEquals(
                everything,
                library.search.search("example", limit = everything.size + 1, offset = 0).orFail(),
                "pages read the same as one read",
            )
        }

    @Test
    fun searchMatchesEveryWordFromItsStartIgnoringCaseAndAccents() =
        runTest {
            fs.writeText(root / InstagramFixtures.ADS_VIEWED_PATH.toPath(), InstagramFixtures.ADS_VIEWED_JSON)
            fs.writeText(
                root / InstagramClaimFixtures.SYNCED_CONTACTS_PATH.toPath(),
                InstagramClaimFixtures.SYNCED_CONTACTS_JSON,
            )
            import(Export.directory(fs, root))
            val courier = Provenance(ExportId("instagram-synthetic"), InstagramFixtures.ADS_VIEWED_PATH, 1)

            suspend fun found(text: String) = library.search.search(text, limit = pageSize, offset = 0).orFail()

            suspend fun firstNames(text: String) =
                found(text).map { (it as SearchHit.OfClaim).claim.attributes.getValue("First Name") }

            assertEquals(listOf(courier), found("cour").map { it.provenance }, "a word matches from its start")
            assertEquals(listOf(courier), found("COURIER").map { it.provenance })
            assertEquals(listOf(courier), found("ship anything").map { it.provenance }, "every word must be there")
            assertEquals(emptyList(), found("ship nobody"))
            assertEquals(listOf(courier), found("logistics").map { it.provenance }, "a hashtag Topic is a name")
            assertEquals(listOf(InstagramClaimFixtures.LATIN1_NAME), firstNames("zoe"))
            assertEquals(listOf(InstagramClaimFixtures.REPAIRED_NAME), firstNames("emilie noel"))
            assertEquals(listOf(InstagramClaimFixtures.ARABIC_NAME), firstNames(InstagramClaimFixtures.ARABIC_NAME))
            assertEquals(emptyList(), found("caption"), "labels are not searched, only values")
        }

    @Test
    fun searchReadsTypedPunctuationAsWordBreaksAndBlankTextFindsNothing() =
        runTest {
            fs.writeText(root / InstagramFixtures.ADS_VIEWED_PATH.toPath(), InstagramFixtures.ADS_VIEWED_JSON)
            import(Export.directory(fs, root))

            suspend fun found(text: String) = library.search.search(text, limit = pageSize, offset = 0).orFail()

            assertEquals(emptyList(), found(""))
            assertEquals(emptyList(), found("   "))
            assertEquals(emptyList(), found("\"*()-:"), "nothing but punctuation is no word")
            assertEquals(1, found("\"courier*\"").size, "quotes and stars are not operators")
            assertEquals(1, found("(courier) -growth").size, "every word is one more word to find")
            assertEquals(1, found("instagram.com/p/BBB").size)
            assertEquals(2, found("www.instagram.com").size, "both dated impressions carry a URL")
            assertEquals(emptyList(), found("courier OR club"), "OR is a word like any other, so it is required too")
        }

    @Test
    fun deletingAnImportRemovesItsHitsAndReimportingAddsNone() =
        runTest {
            val rootA = "/exports/instagram-a".toPath()
            val rootB = "/exports/instagram-b".toPath()
            SyntheticInstagramExport(adsViewed = 6, seed = 1).writeTo(fs, rootA)
            SyntheticInstagramExport(adsViewed = 3, seed = 2).writeTo(fs, rootB)
            val importA = import(Export.directory(fs, rootA))
            val importB = import(Export.directory(fs, rootB))

            suspend fun found() = library.search.search("synthetic ad", limit = 50, offset = 0).orFail()
            assertEquals(9, found().size, "every ad's caption reads 'Synthetic ad N'")

            val importAAgain = import(Export.directory(fs, rootA))

            assertEquals(9, found().size, "a re-imported record is one hit, not two")

            library.imports.delete(importA.id).orFail()

            assertEquals(9, found().size, "the re-imported records belong to the newer Import (ADR 0007)")

            library.imports.delete(importAAgain.id).orFail()

            assertEquals(
                library.events
                    .forImport(importB.id)
                    .orFail()
                    .map { it.provenance }
                    .toSet(),
                found().map { it.provenance }.toSet(),
            )

            library.imports.delete(importB.id).orFail()

            assertEquals(emptyList(), found())
        }

    @Test
    fun importingTheSameExportTwiceRecordsTwoImportsAndChangesNoCounts() =
        runTest {
            SyntheticInstagramExport(adsViewed = 6).writeTo(fs, root)
            val export = Export.directory(fs, root)

            val first = import(export)
            val second = import(export)

            assertEquals(
                listOf(first.id, second.id),
                library.imports
                    .all()
                    .orFail()
                    .map { it.id },
            )
            assertEquals(first.summary, second.summary)
            assertEquals(mapOf(EventKind.AdShown to 6), library.events.countByKind().orFail())
            assertEquals(mapOf(EventKind.AdShown to 6), library.events.countByKind(second.id).orFail())
            assertEquals(emptyMap(), library.events.countByKind(first.id).orFail(), "the latest Import owns the rows")
        }

    @Test
    fun anEntityNamedByManyEventsIsStoredOnceUnderSourceKindKey() =
        runTest {
            val generator = SyntheticInstagramExport(adsViewed = 12)
            generator.writeTo(fs, root)

            import(Export.directory(fs, root))

            val expectedIds =
                generator.ads
                    .map { "instagram:advertiser:${it.username}" }
                    .distinct()
                    .sorted()
            assertTrue(expectedIds.size < generator.ads.size, "the fixture must repeat advertisers")
            assertEquals(
                expectedIds,
                library.entities
                    .all()
                    .orFail()
                    .map { it.id },
            )
        }

    @Test
    fun deletingAnImportRemovesOnlyWhatItProducedAndEntitiesNobodyNeedsAnyMore() =
        runTest {
            val rootA = "/exports/instagram-a".toPath()
            val rootB = "/exports/instagram-b".toPath()
            SyntheticInstagramExport(adsViewed = 8, seed = 1).writeTo(fs, rootA)
            SyntheticInstagramExport(adsViewed = 1, seed = 2).writeTo(fs, rootB)
            val importA = import(Export.directory(fs, rootA))
            val importB = import(Export.directory(fs, rootB))
            val entitiesOfA =
                library.events
                    .forImport(importA.id)
                    .orFail()
                    .flatMap { it.entities }
                    .toSet()
            val entitiesOfB =
                library.events
                    .forImport(importB.id)
                    .orFail()
                    .flatMap { it.entities }
                    .toSet()
            assertTrue((entitiesOfA - entitiesOfB).isNotEmpty(), "A must name an advertiser B does not")

            library.imports.delete(importA.id).orFail()

            assertEquals(notFound, library.imports.get(importA.id))
            assertEquals(listOf(importB), library.imports.all().orFail())
            assertEquals(emptyMap(), library.events.countByKind(importA.id).orFail())
            assertEquals(emptyList(), library.events.forImport(importA.id).orFail())
            assertEquals(mapOf(EventKind.AdShown to 1), library.events.countByKind().orFail())
            assertEquals(
                entitiesOfB.sortedBy { it.id },
                library.entities.all().orFail(),
                "only Entities nobody references go",
            )
            val survivor =
                library.events
                    .forImport(importB.id)
                    .orFail()
                    .single()
            assertEquals(entitiesOfB.toList(), survivor.entities)
            assertTrue(survivor.attributes.isNotEmpty())

            library.imports.delete(importB.id).orFail()

            assertEquals(emptyList(), library.imports.all().orFail())
            assertEquals(emptyMap(), library.events.countByKind().orFail())
            assertEquals(emptyList(), library.entities.all().orFail())
        }

    @Test
    fun anEntityTwoImportsShareSurvivesUntilTheLastOfThemIsDeleted() =
        runTest {
            val rootA = "/exports/instagram-a".toPath()
            val rootB = "/exports/instagram-b".toPath()
            SyntheticInstagramExport(adsViewed = 3).writeTo(fs, rootA)
            SyntheticInstagramExport(adsViewed = 3).writeTo(fs, rootB)
            val importA = import(Export.directory(fs, rootA))
            val importB = import(Export.directory(fs, rootB))
            val shared = library.entities.all().orFail()
            assertTrue(shared.isNotEmpty())

            library.imports.delete(importA.id).orFail()
            assertEquals(shared, library.entities.all().orFail())

            library.imports.delete(importB.id).orFail()
            assertEquals(emptyList(), library.entities.all().orFail())
        }

    @Test
    fun anImportCutShortStaysVisibleWithWhatItWroteAndNoCompletionTime() =
        runTest {
            SyntheticInstagramExport(adsViewed = 2).writeTo(fs, root)

            val steps = library.import(Export.directory(fs, root), InstagramAdapter).take(3).toList()

            assertIs<ImportStep.Started>(steps[0])
            assertIs<ImportStep.FileStarted>(steps[1])
            assertIs<ImportStep.FileFinished>(steps[2])
            val import =
                library.imports
                    .all()
                    .orFail()
                    .single()
            assertNull(import.completedAt)
            assertEquals(TickingClock.START, import.startedAt)
            assertEquals(listOf(InstagramFixtures.ADS_VIEWED_PATH), import.summary.files.map { it.path })
            assertEquals(mapOf(EventKind.AdShown to 2), library.events.countByKind(import.id).orFail())
        }
}

private val nonWord = Regex("[^\\p{L}\\p{N}]+")

/** A Clock that advances by one tick per reading, so started and completed times are distinct and predictable. */
private class TickingClock : Clock {
    private var readings = 0

    override fun now(): Instant = START + TICK * readings++

    companion object {
        val START: Instant = Instant.fromEpochSeconds(1_800_000_000)
        val TICK = 1.seconds
    }
}
