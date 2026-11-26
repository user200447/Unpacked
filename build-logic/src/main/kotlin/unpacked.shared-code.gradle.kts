import com.jassemsendi.unpacked.buildlogic.SharedCodeReportTask

// The README's shared-code table: `./gradlew sharedCodeReport` counts the tree with cloc and rewrites it.
tasks.register<SharedCodeReportTask>("sharedCodeReport") {
    description = "Counts lines per source set with cloc and writes the table into the README (needs cloc)."
    group = "documentation"
    root.set(layout.projectDirectory)
    readme.set(layout.projectDirectory.file("README.md"))
    report.set(layout.buildDirectory.file("reports/shared-code.md"))
}
