package com.jassemsendi.unpacked.fixtures

import java.io.File
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes the [SampleExport] as a zip whose bytes depend on its files alone: entries in path order, one fixed
 * time on each and no extra fields, so regenerating it changes nothing when the generator has not changed.
 * Also the entry point of `./gradlew writeSampleExport`, whose one argument is the zip to write.
 */
public object SampleExportZip {
    private val ENTRY_TIME: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    public fun writeTo(target: File) {
        target.absoluteFile.parentFile.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            SampleExport.files().forEach { (path, text) ->
                zip.putNextEntry(ZipEntry(path).apply { setTimeLocal(ENTRY_TIME) })
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @JvmStatic
    public fun main(args: Array<String>) {
        val target = File(args.singleOrNull() ?: error("one argument: the path of the zip to write"))
        writeTo(target)
        println("Wrote ${SampleExport.files().size} files to $target")
    }
}
