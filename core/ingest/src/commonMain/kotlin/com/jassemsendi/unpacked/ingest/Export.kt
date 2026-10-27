package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.ExportId
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip

/**
 * An Export as downloaded, presented as one read-only file tree behind a root path whether it is
 * a zip or an unzipped directory. Zip entries are read in place and never extracted.
 */
public class Export private constructor(
    public val id: ExportId,
    private val fileSystem: FileSystem,
    private val root: Path,
) {
    /** Every regular file in the Export, by path relative to the root, in a stable sorted order. */
    public fun files(): List<ExportFile> =
        fileSystem
            .listRecursively(root)
            .map { path -> path to path.relativeTo(root).segments }
            .filter { (_, segments) -> segments.none(::isHidden) }
            .filter { (path, _) -> fileSystem.metadata(path).isRegularFile }
            .map { (_, segments) -> ExportFile(id, segments.joinToString("/")) }
            .sortedBy { it.path }
            .toList()

    public fun readText(file: ExportFile): String = fileSystem.read(root / file.path.toPath()) { readUtf8() }

    public companion object {
        /** An unzipped Export; its id is the directory name. */
        public fun directory(
            fileSystem: FileSystem,
            directory: Path,
        ): Export {
            val id = exportIdOfFolder(directory)
            return Export(id, fileSystem, unwrap(fileSystem, directory, id))
        }

        /** A zipped Export read entry by entry; its id is the file name without `.zip`. */
        public fun zip(
            fileSystem: FileSystem,
            zipFile: Path,
        ): Export {
            val id = exportIdOfZip(zipFile)
            val zipFileSystem = fileSystem.openZip(zipFile)
            return Export(id, zipFileSystem, unwrap(zipFileSystem, "/".toPath(), id))
        }

        /**
         * A zip made by hand from a folder (macOS "Compress", most zip tools) wraps everything in one
         * folder named like the Export. Look through exactly that one level, and only that.
         */
        private fun unwrap(
            fileSystem: FileSystem,
            root: Path,
            id: ExportId,
        ): Path {
            val only = fileSystem.list(root).filterNot { isHidden(it.name) }.singleOrNull() ?: return root
            val isWrapper = only.name.equals(id.value, ignoreCase = true) && fileSystem.metadata(only).isDirectory
            return if (isWrapper) only else root
        }

        private fun isHidden(segment: String): Boolean = segment.startsWith(".") || segment == "__MACOSX"
    }
}
