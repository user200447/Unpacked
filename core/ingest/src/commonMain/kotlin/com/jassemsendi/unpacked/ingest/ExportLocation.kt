package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.ExportLocation
import com.jassemsendi.unpacked.model.Result
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/** The id [Export] will give the Export at this location, known before it is opened: the zip name without `.zip`, or the folder name. */
public val ExportLocation.exportId: ExportId
    get() =
        when (this) {
            is ExportLocation.Zip -> exportIdOfZip(path.toPath())
            is ExportLocation.Folder -> exportIdOfFolder(path.toPath())
        }

/**
 * Opens the Export at [location], reading a zip in place and a folder as it is. Never throws for a
 * location the user may have picked: what is not there, or not an Export, comes back as an [ExportError].
 */
public fun Export.Companion.open(
    fileSystem: FileSystem,
    location: ExportLocation,
): Result<Export, ExportError> =
    exportCall {
        when (location) {
            is ExportLocation.Zip -> zip(fileSystem, location.path.toPath())
            is ExportLocation.Folder -> directory(fileSystem, location.path.toPath())
        }
    }

// The one place an Export's id is derived from where it sits, shared by the location and by [Export] itself.
internal fun exportIdOfZip(zipFile: Path): ExportId = ExportId(zipFile.name.removeSuffixIgnoreCase(".zip"))

internal fun exportIdOfFolder(directory: Path): ExportId = ExportId(directory.name)

private fun String.removeSuffixIgnoreCase(suffix: String): String =
    if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this
