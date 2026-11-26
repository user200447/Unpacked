package com.jassemsendi.unpacked

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import okio.FileHandle
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Sink
import okio.Source
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * The documents the user picked, as a read-only Okio file system keyed by [ContentPaths]. Opening one asks
 * [openDocument] for a channel on the document that can be read at any position, which Okio's zip reader then
 * reads entry by entry in place: the zip is never copied into app storage. A document that is gone, or that the
 * app may no longer read, is a [FileNotFoundException], the signal the Explorer turns into "pick it again". Nothing
 * here is ever written, and a document is never a directory.
 */
@Suppress("TooManyFunctions") // every operation of FileSystem is abstract, and most of them only refuse to write
internal class ContentFileSystem(
    private val openDocument: (uri: String) -> FileChannel,
) : FileSystem() {
    override fun openReadOnly(file: Path): FileHandle = ChannelFileHandle(channel(file))

    override fun source(file: Path): Source {
        val handle = openReadOnly(file)
        return handle.source().also { handle.close() }
    }

    override fun metadataOrNull(path: Path): FileMetadata? {
        val channel =
            try {
                channel(path)
            } catch (_: FileNotFoundException) {
                return null
            }
        return channel.use { FileMetadata(isRegularFile = true, isDirectory = false, size = it.size()) }
    }

    override fun canonicalize(path: Path): Path {
        ContentPaths.uriOf(path) ?: throw FileNotFoundException("not a document: $path")
        return path
    }

    override fun list(dir: Path): List<Path> = throw IOException("a document is not a directory: $dir")

    override fun listOrNull(dir: Path): List<Path>? = null

    override fun openReadWrite(
        file: Path,
        mustCreate: Boolean,
        mustExist: Boolean,
    ): FileHandle = throw readOnly()

    override fun sink(
        file: Path,
        mustCreate: Boolean,
    ): Sink = throw readOnly()

    override fun appendingSink(
        file: Path,
        mustExist: Boolean,
    ): Sink = throw readOnly()

    override fun createDirectory(
        dir: Path,
        mustCreate: Boolean,
    ): Unit = throw readOnly()

    override fun atomicMove(
        source: Path,
        target: Path,
    ): Unit = throw readOnly()

    override fun delete(
        path: Path,
        mustExist: Boolean,
    ): Unit = throw readOnly()

    override fun createSymlink(
        source: Path,
        target: Path,
    ): Unit = throw readOnly()

    private fun channel(file: Path): FileChannel {
        val uri = ContentPaths.uriOf(file) ?: throw FileNotFoundException("not a document: $file")
        return try {
            openDocument(uri)
        } catch (error: SecurityException) {
            // The grant was revoked or never persisted: as far as the app is concerned, the Export is not there.
            throw FileNotFoundException("the app may no longer read $file").apply { initCause(error) }
        }
    }

    private fun readOnly() = IOException("picked documents are read-only")
}

/** The content resolver opens a picked document for reading; closing the channel closes the descriptor with it. */
internal fun ContentResolver.exportFileSystem(): ContentFileSystem =
    ContentFileSystem { uri ->
        val descriptor = openFileDescriptor(Uri.parse(uri), "r") ?: throw FileNotFoundException("no document at $uri")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).channel
    }

/** A read-only Okio handle over a channel, reading at absolute positions so the zip's directory can be sought. */
private class ChannelFileHandle(
    private val channel: FileChannel,
) : FileHandle(readWrite = false) {
    override fun protectedRead(
        fileOffset: Long,
        array: ByteArray,
        arrayOffset: Int,
        byteCount: Int,
    ): Int {
        val buffer = ByteBuffer.wrap(array, arrayOffset, byteCount)
        var position = fileOffset
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, position)
            if (read < 0) break
            position += read
        }
        val total = (position - fileOffset).toInt()
        return if (total == 0 && byteCount > 0) -1 else total
    }

    override fun protectedSize(): Long = channel.size()

    override fun protectedClose() = channel.close()

    override fun protectedFlush(): Unit = throw IOException("read-only")

    override fun protectedResize(size: Long): Unit = throw IOException("read-only")

    override fun protectedWrite(
        fileOffset: Long,
        array: ByteArray,
        arrayOffset: Int,
        byteCount: Int,
    ): Unit = throw IOException("read-only")
}
