package com.jassemsendi.unpacked.ingest

import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path

/**
 * Writes a directory into a stored (uncompressed) zip file, entry by entry, so tests can prove
 * that a zipped Export and a directory Export are read identically. Test-only on purpose.
 */
internal fun FileSystem.zipDirectory(
    directory: Path,
    zipFile: Path,
) {
    val files = listRecursively(directory).filter { metadata(it).isRegularFile }.sortedBy { it.toString() }.toList()
    val entries = Buffer()
    val centralDirectory = Buffer()
    for (file in files) {
        val entry =
            ZipEntry(
                name =
                    file
                        .relativeTo(directory)
                        .segments
                        .joinToString("/")
                        .encodeUtf8(),
                data = read(file) { readByteString() },
                offset = entries.size.toInt(),
            )
        entries.writeLocalHeader(entry).write(entry.data)
        centralDirectory.writeCentralHeader(entry)
    }
    val centralOffset = entries.size.toInt()
    val centralSize = centralDirectory.size.toInt()
    entries.writeAll(centralDirectory)
    entries
        .writeIntLe(END_OF_CENTRAL_DIRECTORY)
        .writeShortLe(0)
        .writeShortLe(0)
        .writeShortLe(files.size)
        .writeShortLe(files.size)
        .writeIntLe(centralSize)
        .writeIntLe(centralOffset)
        .writeShortLe(0)
    zipFile.parent?.let { createDirectories(it) }
    write(zipFile) { writeAll(entries) }
}

private class ZipEntry(
    val name: ByteString,
    val data: ByteString,
    val offset: Int,
) {
    val crc: Int = crc32(data)
}

private fun Buffer.writeLocalHeader(entry: ZipEntry): Buffer =
    writeIntLe(LOCAL_FILE_HEADER)
        .writeShortLe(VERSION)
        .writeShortLe(0)
        .writeShortLe(STORED)
        .writeShortLe(0)
        .writeShortLe(DOS_DATE_1980)
        .writeIntLe(entry.crc)
        .writeIntLe(entry.data.size)
        .writeIntLe(entry.data.size)
        .writeShortLe(entry.name.size)
        .writeShortLe(0)
        .write(entry.name)

private fun Buffer.writeCentralHeader(entry: ZipEntry): Buffer =
    writeIntLe(CENTRAL_FILE_HEADER)
        .writeShortLe(VERSION)
        .writeShortLe(VERSION)
        .writeShortLe(0)
        .writeShortLe(STORED)
        .writeShortLe(0)
        .writeShortLe(DOS_DATE_1980)
        .writeIntLe(entry.crc)
        .writeIntLe(entry.data.size)
        .writeIntLe(entry.data.size)
        .writeShortLe(entry.name.size)
        .writeShortLe(0)
        .writeShortLe(0)
        .writeShortLe(0)
        .writeShortLe(0)
        .writeIntLe(0)
        .writeIntLe(entry.offset)
        .write(entry.name)

private const val LOCAL_FILE_HEADER = 0x04034b50
private const val CENTRAL_FILE_HEADER = 0x02014b50
private const val END_OF_CENTRAL_DIRECTORY = 0x06054b50
private const val VERSION = 20
private const val STORED = 0
private const val DOS_DATE_1980 = 0x21

private val crcTable: IntArray =
    IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1 }
        c
    }

private fun crc32(data: ByteString): Int {
    var crc = -1
    for (i in 0 until data.size) {
        crc = crcTable[(crc xor data[i].toInt()) and 0xff] xor (crc ushr 8)
    }
    return crc.inv()
}
