package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.ExportId
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/** Where the user's Export is on disk, by absolute path: the zip as downloaded, or the folder it was unzipped into. */
public sealed interface ExportLocation {
    public val path: String

    /** The id [Export] will give this Export, known before it is opened: the zip name without `.zip`, or the folder name. */
    public val exportId: ExportId

    public data class Zip(
        override val path: String,
    ) : ExportLocation {
        override val exportId: ExportId get() = exportIdOfZip(path.toPath())
    }

    public data class Folder(
        override val path: String,
    ) : ExportLocation {
        override val exportId: ExportId get() = exportIdOfFolder(path.toPath())
    }
}

/** Opens the Export at [location], reading a zip in place and a folder as it is. */
public fun Export.Companion.open(
    fileSystem: FileSystem,
    location: ExportLocation,
): Export =
    when (location) {
        is ExportLocation.Zip -> zip(fileSystem, location.path.toPath())
        is ExportLocation.Folder -> directory(fileSystem, location.path.toPath())
    }

// The one place an Export's id is derived from where it sits, shared by the location and by [Export] itself.
internal fun exportIdOfZip(zipFile: Path): ExportId = ExportId(zipFile.name.removeSuffixIgnoreCase(".zip"))

internal fun exportIdOfFolder(directory: Path): ExportId = ExportId(directory.name)

private fun String.removeSuffixIgnoreCase(suffix: String): String =
    if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this
