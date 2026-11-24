package com.jassemsendi.unpacked

import com.jassemsendi.unpacked.ingest.ExportPart
import com.jassemsendi.unpacked.ingest.exportId
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.ExportLocation
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** On Android an Export's location is its document URI, written as a path whose last segment is the zip's name. */
class ContentPathsTest {
    private val uri = "content://com.example.documents/document/msf%3A42"

    @Test
    fun theLocationEndsWithTheZipNameSoTheExportIdAndPartAreReadFromIt() {
        val path = ContentPaths.pathOf(uri, "instagram-me-2026-10-20-part-2-of-3.zip")
        val location = ExportLocation.Zip(path.toString())

        assertEquals(
            "/content/com.example.documents/document/msf%3A42/instagram-me-2026-10-20-part-2-of-3.zip",
            location.path,
        )
        assertEquals(ExportId("instagram-me-2026-10-20-part-2-of-3"), location.exportId)
        assertEquals(ExportPart(2, 3), ExportPart.of(location.exportId))
    }

    @Test
    fun theDocumentUriIsReadBackFromThePath() {
        val path = ContentPaths.pathOf(uri, "instagram-me-2026-10-20.zip")

        assertEquals(uri, ContentPaths.uriOf(path))
    }

    @Test
    fun aNameWithASlashCannotBecomeASecondSegment() {
        val path = ContentPaths.pathOf(uri, "odd/name.zip")

        assertEquals(uri, ContentPaths.uriOf(path))
        assertEquals("odd_name.zip", path.name)
    }

    @Test
    fun aNameThatWouldWalkUpThePathIsReplaced() {
        assertEquals(uri, ContentPaths.uriOf(ContentPaths.pathOf(uri, "..")))
        assertEquals(uri, ContentPaths.uriOf(ContentPaths.pathOf(uri, ".")))
        assertEquals(uri, ContentPaths.uriOf(ContentPaths.pathOf(uri, "")))
    }

    @Test
    fun aPathThatIsNotADocumentHasNoUri() {
        assertNull(ContentPaths.uriOf("/storage/emulated/0/Download/instagram.zip".toPath()))
        assertNull(ContentPaths.uriOf("/content/only-an-authority".toPath()))
    }
}
