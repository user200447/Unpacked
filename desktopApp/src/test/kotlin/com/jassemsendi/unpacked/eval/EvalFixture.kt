package com.jassemsendi.unpacked.eval

import com.jassemsendi.unpacked.data.Library
import com.jassemsendi.unpacked.data.inMemory
import com.jassemsendi.unpacked.fixtures.SyntheticInstagramExport
import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.ingest.ImportStep
import com.jassemsendi.unpacked.ingest.InstagramAdapter
import com.jassemsendi.unpacked.model.Import
import kotlinx.coroutines.flow.toList
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/**
 * The Library the eval asks about: the full synthetic Export (ads, every timed file, every untimed file and the
 * message threads) imported once through the real Instagram adapter into an in-memory Library, so every expected
 * value can be read back from what the fixture is known to hold.
 */
class EvalFixture private constructor(
    val export: SyntheticInstagramExport,
    val library: Library,
    val import: Import,
) : AutoCloseable {
    override fun close(): Unit = library.close()

    companion object {
        suspend fun seed(): EvalFixture {
            val export =
                SyntheticInstagramExport(
                    adsViewed = 5,
                    includeTimedFiles = true,
                    includeClaimFiles = true,
                    includeMessageThreads = true,
                )
            val fs = FakeFileSystem()
            val root = "/exports/instagram-synthetic".toPath()
            export.writeTo(fs, root)
            val library = Library.inMemory()
            val last = library.import(Export.directory(fs, root), InstagramAdapter).toList().last()
            val stored =
                last as? ImportStep.Stored ?: run {
                    library.close()
                    error("the synthetic Export did not import: $last")
                }
            return EvalFixture(export, library, stored.import)
        }
    }
}
