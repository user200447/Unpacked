package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.fixtures.InstagramFixtures
import com.jassemsendi.unpacked.fixtures.SyntheticInstagramExport
import com.jassemsendi.unpacked.fixtures.writeText
import com.jassemsendi.unpacked.model.ExportId
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportTest {
    private val fs = FakeFileSystem()

    @Test
    fun directoryExportListsFilesRelativeToItsRootInSortedOrder() {
        val root = "/exports/instagram-synthetic".toPath()
        SyntheticInstagramExport(adsViewed = 1, mediaFiles = 1).writeTo(fs, root)

        val export = Export.directory(fs, root)

        assertEquals(ExportId("instagram-synthetic"), export.id)
        assertEquals(
            listOf(
                InstagramFixtures.UNMAPPED_PATH,
                InstagramFixtures.ADS_VIEWED_PATH,
                "media/posts/202610/synthetic_0.jpg",
            ).sorted(),
            export.files().map { it.path },
        )
    }

    @Test
    fun zipExportDropsTheExtensionFromItsIdAndReadsEntriesInPlace() {
        val directory = "/exports/instagram-synthetic".toPath()
        val zip = "/exports/instagram-synthetic.zip".toPath()
        val generator = SyntheticInstagramExport(adsViewed = 2, mediaFiles = 0, includeUnmappedFile = false)
        generator.writeTo(fs, directory)
        fs.zipDirectory(directory, zip)
        fs.deleteRecursively(directory)
        val pathsBefore = fs.allPaths

        val export = Export.zip(fs, zip)
        val adsFile = export.files().single()

        assertEquals(ExportId("instagram-synthetic"), export.id)
        assertEquals(InstagramFixtures.ADS_VIEWED_PATH, adsFile.path)
        assertEquals(generator.adsViewedJson(), export.readText(adsFile))
        assertEquals(pathsBefore, fs.allPaths, "reading a zip must not write anything")
    }

    @Test
    fun aWrapperFolderNamedLikeTheExportIsUnwrappedOnce() {
        val root = "/exports/instagram-wrapped".toPath()
        SyntheticInstagramExport(adsViewed = 1, mediaFiles = 0).writeTo(fs, root / "instagram-wrapped")

        val paths = Export.directory(fs, root).files().map { it.path }

        assertTrue(paths.none { it.startsWith("instagram-wrapped/") }, paths.toString())
        assertTrue(InstagramFixtures.ADS_VIEWED_PATH in paths)
    }

    @Test
    fun aLoneTopLevelFolderWithAnotherNameIsPartOfTheExport() {
        val root = "/exports/instagram-small".toPath()
        SyntheticInstagramExport(adsViewed = 1, mediaFiles = 0, includeUnmappedFile = false).writeTo(fs, root)

        assertEquals(listOf(InstagramFixtures.ADS_VIEWED_PATH), Export.directory(fs, root).files().map { it.path })
    }

    @Test
    fun hiddenAndMacOsMetadataFilesAreIgnored() {
        val root = "/exports/instagram-synthetic".toPath()
        SyntheticInstagramExport(adsViewed = 1, mediaFiles = 0, includeUnmappedFile = false).writeTo(fs, root)
        fs.writeText(root / ".DS_Store", "junk")
        fs.writeText(root / "__MACOSX" / "._ads_viewed.json", "junk")

        assertEquals(listOf(InstagramFixtures.ADS_VIEWED_PATH), Export.directory(fs, root).files().map { it.path })
    }

    @Test
    fun exportFileDerivesNameExtensionAndRawKind() {
        val file = ExportFile(ExportId("export"), "ads_information/ads_and_topics/Ads_Viewed.JSON")
        assertEquals("Ads_Viewed.JSON", file.name)
        assertEquals("json", file.extension)
        assertEquals("ads_viewed", file.rawKind.value)
        assertTrue(file.isJson)
        assertTrue(ExportFile(ExportId("export"), "media/posts/a.MP4").isMedia)
    }

    @Test
    fun aFileWithNoExtensionInsideAPhotosVideosOrAudioFolderIsMedia() {
        val export = ExportId("export")
        assertTrue(ExportFile(export, "your_instagram_activity/messages/inbox/t_1/photos/1066712851843908").isMedia)
        assertTrue(ExportFile(export, "your_instagram_activity/messages/inbox/t_1/videos/1039734214128312").isMedia)
        assertTrue(ExportFile(export, "your_instagram_activity/messages/inbox/t_1/audio/1057182629448480").isMedia)
        assertFalse(ExportFile(export, "your_instagram_activity/messages/inbox/t_1/files/1057182629448480").isMedia)
        assertFalse(ExportFile(export, "your_instagram_activity/messages/inbox/t_1/photos/notes.txt").isMedia)
        assertFalse(ExportFile(export, "personal_information/README").isMedia)
    }
}
