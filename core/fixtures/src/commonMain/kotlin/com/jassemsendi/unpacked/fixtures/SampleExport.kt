package com.jassemsendi.unpacked.fixtures

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/**
 * The Sample Export shipped in the repository for anyone to try the app with: the generator's full synthetic
 * Instagram Export (the ads, every timed file, every untimed file, the message threads with their media, and
 * one file no mapper knows, for the Explorer), with nobody's data in it. The twenty-question eval asks its
 * questions over this same Export, and `./gradlew writeSampleExport` writes it to [ZIP_PATH].
 */
public object SampleExport {
    /** Where the zip lives, relative to the repository root. */
    public const val ZIP_PATH: String = "sample/instagram-sample-export.zip"

    public fun export(): SyntheticInstagramExport =
        SyntheticInstagramExport(
            adsViewed = 5,
            includeTimedFiles = true,
            includeClaimFiles = true,
            includeMessageThreads = true,
        )

    /** Every file of the Sample Export by its path inside the Export, in path order, with its text. */
    public fun files(): Map<String, String> {
        val fileSystem = FakeFileSystem()
        val root = "/export".toPath()
        export().writeTo(fileSystem, root)
        return fileSystem.textFiles(root)
    }
}
