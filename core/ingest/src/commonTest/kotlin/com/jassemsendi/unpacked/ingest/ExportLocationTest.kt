package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.fixtures.SyntheticInstagramExport
import com.jassemsendi.unpacked.model.ExportId
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The location the user picked opens as the same Export whether it is a zip or a folder, and names its part. */
class ExportLocationTest {
    private val fs = FakeFileSystem()

    @Test
    fun aZipAndAFolderLocationOpenToTheSameFiles() {
        val folder = "/picked/instagram-synthetic".toPath()
        SyntheticInstagramExport(adsViewed = 2, mediaFiles = 1).writeTo(fs, folder)
        fs.zipDirectory(folder, "/picked/instagram-synthetic.zip".toPath())

        val fromFolder = Export.open(fs, ExportLocation.Folder(folder.toString()))
        val fromZip = Export.open(fs, ExportLocation.Zip("/picked/instagram-synthetic.zip"))

        assertEquals(ExportId("instagram-synthetic"), fromFolder.id)
        assertEquals(fromFolder.id, fromZip.id)
        assertEquals(fromFolder.files(), fromZip.files())
    }

    @Test
    fun aLocationKnowsTheExportIdBeforeItIsOpened() {
        assertEquals(
            ExportId("instagram-me-2026-10-20"),
            ExportLocation.Zip("/downloads/instagram-me-2026-10-20.ZIP").exportId,
        )
        assertEquals(
            ExportId("instagram-me-2026-10-20"),
            ExportLocation.Folder("/downloads/instagram-me-2026-10-20/").exportId,
        )
    }

    @Test
    fun aMultiPartExportIsRecognisedFromItsName() {
        assertEquals(ExportPart(2, 3), ExportPart.of(ExportId("instagram-me-2026-10-20-part-2-of-3")))
        assertEquals(ExportPart(2, 3), ExportPart.of(ExportId("instagram-me-2026-10-20 (part 2 of 3)")))
        assertEquals(ExportPart(1, null), ExportPart.of(ExportId("instagram-me-2026-10-20-part1")))
        assertEquals(ExportPart(2, null), ExportPart.of(ExportId("takeout-20261020T120000Z-002")))
        assertNull(ExportPart.of(ExportId("instagram-me-2026-10-20")))
        assertNull(ExportPart.of(ExportId("instagram-synthetic")))
    }
}
