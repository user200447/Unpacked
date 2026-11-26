package com.jassemsendi.unpacked.model

/**
 * Where an Export is on the device, by absolute path: the zip as downloaded, or the folder it was unzipped
 * into. An Import remembers it so the Explorer can read a file from the Export on demand; nothing keeps the
 * Export there, so whoever opens it later must be ready to find nothing and say so.
 */
public sealed interface ExportLocation {
    public val path: String

    public data class Zip(
        override val path: String,
    ) : ExportLocation

    public data class Folder(
        override val path: String,
    ) : ExportLocation
}
