package com.jassemsendi.unpacked.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Counts the Kotlin, Swift and Gradle sources with cloc, grouped by source set, writes the table to [report]
 * and into the README between its markers, and prints it. Needs cloc on the PATH. Never up to date: its input
 * is the whole tree, which is not worth fingerprinting for a task that runs in under a second.
 */
abstract class SharedCodeReportTask : DefaultTask() {
    /** The repository root cloc runs over. */
    @get:Internal
    abstract val root: DirectoryProperty

    /** The README whose marked block is rewritten; read as well as written. */
    @get:OutputFile
    abstract val readme: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @get:Inject
    abstract val exec: ExecOperations

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun count() {
        if (!clocIsOnThePath()) {
            throw GradleException("cloc is not on the PATH; install it (brew install cloc, apt install cloc) and retry")
        }
        val table = SharedCodeReport.render(SharedCodeReport.parseClocCsv(runCloc()))
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(table)
        val readmeFile = readme.get().asFile
        readmeFile.writeText(SharedCodeReport.updateReadme(readmeFile.readText(), table))
        logger.lifecycle(table)
    }

    private fun clocIsOnThePath(): Boolean =
        System
            .getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .any { directory -> CLOC_NAMES.any { File(directory, it).canExecute() } }

    private fun runCloc(): String {
        val out = ByteArrayOutputStream()
        exec.exec {
            workingDir = root.get().asFile
            commandLine(
                "cloc",
                "--csv",
                "--by-file",
                "--quiet",
                // cloc counts one of two identical files unless told otherwise; a test the same on two targets is two files.
                "--skip-uniqueness",
                "--include-ext=kt,kts,swift",
                "--exclude-dir=build,.gradle,.kotlin,.idea,schemas",
                ".",
            )
            standardOutput = out
        }
        return out.toString(Charsets.UTF_8)
    }

    private companion object {
        val CLOC_NAMES = listOf("cloc", "cloc.exe", "cloc.bat")
    }
}
