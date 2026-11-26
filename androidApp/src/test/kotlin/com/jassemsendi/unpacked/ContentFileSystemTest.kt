package com.jassemsendi.unpacked

import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.ingest.ExportError
import com.jassemsendi.unpacked.ingest.ExportFile
import com.jassemsendi.unpacked.ingest.open
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.model.Result
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.channels.FileChannel
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** The Export zip is read in place through whatever the content resolver opens for its URI, never copied. */
class ContentFileSystemTest {
    private val uri = "content://com.example.documents/document/msf%3A42"
    private val location = ExportLocation.Zip(ContentPaths.pathOf(uri, "instagram-me-2026-10-20.zip").toString())
    private val zip = File.createTempFile("instagram-me-2026-10-20", ".zip").apply { deleteOnExit() }
    private val opened = mutableListOf<String>()

    private fun fileSystem(open: (String) -> FileChannel = { FileInputStream(zip).channel }) =
        ContentFileSystem { uri ->
            opened += uri
            open(uri)
        }

    init {
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry(ADS_VIEWED))
            out.write("[]".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("media/posts/one.jpg"))
            out.write("not really media".toByteArray())
            out.closeEntry()
        }
    }

    @Test
    fun theExportAtADocumentUriListsAndReadsItsFilesThroughThatUri() {
        val export = Export.open(fileSystem(), location)

        assertIs<Result.Success<Export>>(export)
        assertEquals(
            listOf(ADS_VIEWED, "media/posts/one.jpg"),
            (export.data.files() as Result.Success).data.map { it.path },
        )
        assertEquals("[]", export.data.readText(ExportFile(export.data.id, ADS_VIEWED)))
        // Okio opens the zip again for each entry it reads; every one of those opens goes through the URI.
        assertEquals(setOf(uri), opened.toSet())
    }

    @Test
    fun aDocumentTheResolverCannotFindIsNotFound() {
        val export = Export.open(fileSystem { throw FileNotFoundException("gone") }, location)

        assertEquals(Result.Error(ExportError.NOT_FOUND), export)
    }

    @Test
    fun aDocumentTheAppMayNoLongerReadIsNotFoundSoTheUserPicksItAgain() {
        val export = Export.open(fileSystem { throw SecurityException("permission revoked") }, location)

        assertEquals(Result.Error(ExportError.NOT_FOUND), export)
    }

    @Test
    fun aPathThatIsNotADocumentIsNotFoundWithoutAskingTheResolver() {
        val export = Export.open(fileSystem(), ExportLocation.Zip("/storage/emulated/0/Download/instagram.zip"))

        assertEquals(Result.Error(ExportError.NOT_FOUND), export)
        assertEquals(emptyList(), opened)
    }

    @Test
    fun aDocumentThatIsNotAZipIsNotReadable() {
        zip.writeText("not a zip")

        assertEquals(Result.Error(ExportError.NOT_READABLE), Export.open(fileSystem(), location))
    }

    @Test
    fun nothingIsEverWritten() {
        val path = ContentPaths.pathOf(uri, "instagram-me-2026-10-20.zip")

        assertFailsWith<IOException> { fileSystem().sink(path) }
        assertFailsWith<IOException> { fileSystem().delete(path) }
        assertFailsWith<IOException> { fileSystem().createDirectory(path) }
    }

    private companion object {
        const val ADS_VIEWED = "ads_information/ads_and_topics/ads_viewed.json"
    }
}
