package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.fixtures.SampleExport
import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.fixtures.textFiles
import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.ingest.InstagramAdapter
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.FileResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.openZip
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The Sample Export checked into the repository, the one the README sends a reader to: it holds exactly what
 * the generator writes, and it imports through the real adapter with no file Failed.
 */
class SampleExportTest {
    private val zip: Path = repositoryFile(SampleExport.ZIP_PATH).toOkioPath()

    @Test
    fun theZipHoldsExactlyWhatTheGeneratorWrites() {
        assertTrue(FileSystem.SYSTEM.exists(zip), "$zip is missing; run ./gradlew writeSampleExport")
        val entries = FileSystem.SYSTEM.openZip(zip).textFiles("/".toPath())

        assertEquals(SampleExport.files(), entries, "the zip is stale; run ./gradlew writeSampleExport")
    }

    @Test
    fun theZipImportsWithNoFailedFile() =
        runTest {
            Library.inMemory().use { library ->
                val steps = library.import(Export.zip(FileSystem.SYSTEM, zip), InstagramAdapter).toList()
                val import = assertIs<ImportStep.Stored>(steps.last()).import

                assertEquals(emptyList<FileResult.Failed>(), import.summary.failed)
                assertTrue(import.summary.parsed.isNotEmpty())
                val eventsByKind = library.events.countByKind(import.id).orFail()
                assertEquals(SampleExport.export().ads.size, eventsByKind[EventKind.AdShown])
            }
        }

    /** A file of the repository, found from wherever the test runs: the module directory under Gradle, or the root. */
    private fun repositoryFile(relativePath: String): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { it.resolve(relativePath) }
            .firstOrNull { it.exists() }
            ?: File("").absoluteFile.resolve(relativePath)
}
