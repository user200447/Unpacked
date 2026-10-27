plugins {
    id("unpacked.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
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

val hostOs = System.getProperty("os.name").lowercase()
val hostArch = System.getProperty("os.arch").lowercase()
val hostNatives =
    when {
        hostOs.contains("mac") -> "osx_arm64" to "libsqliteJni.dylib"
        hostOs.contains("windows") -> "windows_x64" to "sqliteJni.dll"
        hostArch == "aarch64" -> "linux_arm64" to "libsqliteJni.so"
        else -> "linux_x64" to "libsqliteJni.so"
    }

tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        dependsOn(extractSqliteNatives)
        systemProperty(
            "androidx.sqlite.driver.bundled.path",
            sqliteNativesDir
                .get()
                .asFile
                .resolve("natives/${hostNatives.first}")
                .absolutePath,
        )
        systemProperty("androidx.sqlite.driver.bundled.name", hostNatives.second)
    }
}
