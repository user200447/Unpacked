package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.fixtures.SyntheticInstagramExport
import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.fixtures.writeText
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.Result
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

        val folderLocation = ExportLocation.Folder(folder.toString())
        val zipLocation = ExportLocation.Zip("/picked/instagram-synthetic.zip")
        val fromFolder = Export.open(fs, folderLocation).orFail()
        val fromZip = Export.open(fs, zipLocation).orFail()

        assertEquals(ExportId("instagram-synthetic"), fromFolder.id)
        assertEquals(fromFolder.id, fromZip.id)
        assertEquals(folderLocation.exportId, fromFolder.id, "the id a location promises is the id the Export gets")
        assertEquals(zipLocation.exportId, fromZip.id)
        assertEquals(fromFolder.files().orFail(), fromZip.files().orFail())
    }

    @Test
    fun aLocationWithNothingAtItIsNotFoundAndOneThatIsNotAnExportIsNotReadable() {
        fs.writeText("/picked/notes.zip".toPath(), "this is a text file with a zip name")
        fs.writeText("/picked/instagram-file".toPath(), "a file where a folder was expected")

        assertEquals(Result.Error(ExportError.NOT_FOUND), Export.open(fs, ExportLocation.Zip("/picked/missing.zip")))
        assertEquals(Result.Error(ExportError.NOT_FOUND), Export.open(fs, ExportLocation.Folder("/picked/missing")))
        assertEquals(Result.Error(ExportError.NOT_READABLE), Export.open(fs, ExportLocation.Zip("/picked/notes.zip")))
        assertEquals(
            Result.Error(ExportError.NOT_READABLE),
            Export.open(fs, ExportLocation.Folder("/picked/instagram-file")),
        )
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
        assertNull(ExportPart.of(ExportId("instagram-me-2026-10-20")))
        assertNull(ExportPart.of(ExportId("instagram-synthetic")))
    }
}
