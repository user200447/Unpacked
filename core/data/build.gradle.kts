import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("unpacked.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    // JVM and iOS build a database from a path alone; Android needs a Context (see androidMain).
    applyDefaultHierarchyTemplate {
        common {
            group("nonAndroid") {
                withJvm()
                withIos()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:ingest"))
            api(libs.kotlinx.coroutinesCore)
            // Room and the bundled SQLite driver never leave this module (ADR 0005).
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(project(":core:fixtures"))
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.okio.fakefilesystem)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

// The Android artifact of the bundled driver ships Android ABIs only, so on a developer machine or CI
// runner the Android host test cannot load it. Its loader honours two system properties naming a
// library on disk; point them at the desktop library the JVM artifact carries for this OS and CPU.
val sqliteNatives: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    sqliteNatives(libs.androidx.sqlite.bundledJvm)
}

val sqliteNativesDir = layout.buildDirectory.dir("sqlite-natives")

abstract class ExtractNatives : DefaultTask() {
    @get:InputFiles
    abstract val jars: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val into: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun extract() {
        files.sync {
            from(jars.map { archives.zipTree(it) }) { include("natives/**") }
            into(into)
        }
    }
}

val extractSqliteNatives by tasks.registering(ExtractNatives::class) {
    jars.from(sqliteNatives)
    into.set(sqliteNativesDir)
}

/** Where the JVM artifact keeps the library for one OS and CPU, and what it is called there. */
data class HostNatives(
    val directory: String,
    val file: String,
)

val hostOs = System.getProperty("os.name").lowercase()
val hostArch = if (System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")) "arm64" else "x64"
val hostNatives =
    when {
        hostOs.contains("mac") -> HostNatives("osx_$hostArch", "libsqliteJni.dylib")
        hostOs.contains("windows") -> HostNatives("windows_$hostArch", "sqliteJni.dll")
        else -> HostNatives("linux_$hostArch", "libsqliteJni.so")
    }

tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        dependsOn(extractSqliteNatives)
        systemProperty(
            "androidx.sqlite.driver.bundled.path",
            sqliteNativesDir
                .get()
                .asFile
                .resolve("natives/${hostNatives.directory}")
                .absolutePath,
        )
        systemProperty("androidx.sqlite.driver.bundled.name", hostNatives.file)
    }
}
