package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.fixtures.InstagramFixtures
import com.jassemsendi.unpacked.fixtures.SyntheticInstagramExport
import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.fixtures.writeText
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Seam: the Explorer's reading of one file from the synthetic Export, by the Instagram adapter and with no Kind. */
class ExportFileReaderTest {
    private val fs = FakeFileSystem()
    private val root = "/exports/instagram-synthetic".toPath()
    private val location = ExportLocation.Folder(root.toString())
    private val reader = FileSystemExportFileReader(fs)

    private suspend fun read(path: String) = reader.read(location, Source.Instagram, path)

    @Test
    fun anUnrecognisedJsonFileReadsAsItsLabelAndValuePairsWithItsTimestampAndNoKind() =
        runTest {
            SyntheticInstagramExport(adsViewed = 1, mediaFiles = 0).writeTo(fs, root)

            val records = read(InstagramFixtures.UNMAPPED_PATH).orFail()

            assertEquals(
                FileRecords(
                    listOf(
                        Record(
                            0,
                            mapOf("Timestamp" to "2026-10-08T07:48:39Z", "Subscription status" to "Not subscribed"),
                        ),
                    ),
                ),
                records,
            )
        }

    @Test
    fun aParsedFileReadsAsOneRecordPerEntryInFileOrder() =
        runTest {
            val export = SyntheticInstagramExport(adsViewed = 3, mediaFiles = 0)
            export.writeTo(fs, root)

            val records = read(InstagramFixtures.ADS_VIEWED_PATH).orFail()

            assertEquals(null, records.sourceFailure)
            assertEquals(listOf(0, 1, 2), records.records.map { it.index })
            assertEquals(export.ads.map { it.caption }, records.records.map { it.fields.getValue("Caption") })
            assertEquals(export.ads[0].name, records.records[0].fields.getValue("Owner / Name"))
        }

    @Test
    fun aFileTheSourceCannotReadIsReadAsPlainJsonWithWhyTheSourceCouldNot() =
        runTest {
            SyntheticInstagramExport(adsViewed = 1, mediaFiles = 0).writeTo(fs, root)
            val path = "personal_information/device_information/devices.json"
            fs.writeText(
                root / path.toPath(),
                """[{"name": "Pixel", "os": {"family": "Android", "version": 15}, "tags": ["work", "home"], "spare": null},
                   {"name": "Laptop"}]""",
            )

            val records = read(path).orFail()

            assertEquals(
                listOf(
                    Record(
                        0,
                        mapOf(
                            "name" to "Pixel",
                            "os / family" to "Android",
                            "os / version" to "15",
                            "tags" to "work, home",
                        ),
                    ),
                    Record(1, mapOf("name" to "Laptop")),
                ),
                records.records,
            )
            assertTrue(assertNotNull(records.sourceFailure).isNotBlank())
        }

    @Test
    fun aFailedFileThatIsNotJsonAtAllReadsAsNoRecordsAndTheReason() =
        runTest {
            SyntheticInstagramExport(adsViewed = 1, malformedAdsViewed = true, mediaFiles = 0).writeTo(fs, root)

            val records = read(InstagramFixtures.ADS_VIEWED_PATH).orFail()

            assertEquals(emptyList(), records.records)
            assertTrue(assertNotNull(records.sourceFailure).isNotBlank())
        }

    @Test
    fun aMessageThreadReadsAsOneRecordPerMessageAsWritten() =
        runTest {
            SyntheticInstagramExport(adsViewed = 1, mediaFiles = 0, includeMessageThreads = true).writeTo(fs, root)
            val courier = SyntheticInstagramExport.MESSAGE_THREADS.last()

            val records = read("${courier.folder}/message_1.json").orFail()

            assertEquals(null, records.sourceFailure)
            assertEquals(
                listOf("Hello, this is your courier", "Your parcel is at the depot"),
                records.records.map { it.fields.getValue("content") }.sorted(),
            )
            assertEquals(setOf("Example Courier"), records.records.map { it.fields.getValue("sender_name") }.toSet())
        }

    @Test
    fun anExportThatHasMovedIsGoneAndAFileNoLongerInItIsGone() =
        runTest {
            SyntheticInstagramExport(adsViewed = 1, mediaFiles = 0).writeTo(fs, root)

            assertEquals(
                Result.Error(ExplorerError.ExportGone(ExportError.NOT_FOUND)),
                reader.read(ExportLocation.Zip("/downloads/instagram-synthetic.zip"), Source.Instagram, "x.json"),
            )
            assertEquals(Result.Error(ExplorerError.FileGone), read("ads_information/removed_since.json"))
        }
}
