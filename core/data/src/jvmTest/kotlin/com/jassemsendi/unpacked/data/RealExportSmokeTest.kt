package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.ingest.ExportLocation
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.ingest.InstagramAdapter
import com.jassemsendi.unpacked.ingest.open
import com.jassemsendi.unpacked.model.FileResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

/**
 * Seam 4: the owner's real Instagram Export, read from the path in `UNPACKED_INSTAGRAM_EXPORT`,
 * imported into an in-memory library. Asserts that no file Failed and prints what landed per Kind.
 * Skipped when the variable is absent; the Export itself is never committed.
 */
class RealExportSmokeTest {
    @Test
    fun theOwnersExportImportsWithNoFailedFile() =
        runTest(timeout = 10.minutes) {
            val path = System.getenv(EXPORT_VARIABLE)?.takeUnless { it.isBlank() }
            assumeTrue("$EXPORT_VARIABLE is not set; skipping the real-Export smoke test", path != null)
            checkNotNull(path)
            val location =
                if (path.endsWith(".zip", ignoreCase = true)) ExportLocation.Zip(path) else ExportLocation.Folder(path)

            Library.inMemory().use { library ->
                val steps = library.import(Export.open(FileSystem.SYSTEM, location), InstagramAdapter).toList()
                val import = assertIs<ImportStep.Stored>(steps.last()).import

                val summary = import.summary
                println("Export ${summary.exportId.value}: ${summary.inventory}")
                println("Parsed ${summary.parsed.size}, skipped ${summary.skipped.size}, failed ${summary.failed.size}")
                println("Events per Kind: ${library.events.countByKind(import.id).toSortedMap()}")
                println("Claims per Kind: ${library.claims.countByKind(import.id).toSortedMap()}")
                println("Skipped JSON files, by name:")
                summary.skipped
                    .filter { it.path.endsWith(".json") }
                    .groupingBy { it.path.substringAfterLast('/') }
                    .eachCount()
                    .toSortedMap()
                    .forEach { (name, count) -> println("  $name x$count") }
                summary.failed.forEach { println("FAILED ${it.path}: ${it.reason}") }
                assertEquals(emptyList<FileResult.Failed>(), summary.failed)
            }
        }

    private companion object {
        const val EXPORT_VARIABLE = "UNPACKED_INSTAGRAM_EXPORT"
    }
}
