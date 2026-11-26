import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("unpacked.quality")
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":core:data"))
    // The agent's provider interface, bound here to Ollama on localhost (ADR 0008): Koog's Ollama client rides on a
    // Ktor HTTP client, which needs an engine on the classpath.
    implementation(project(":core:agent"))
    implementation(libs.koog.ollamaClient)
    implementation(libs.koog.httpClientKtor)
    implementation(libs.ktor.clientCio)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.koin.core)
    implementation(libs.okio)
    // Koog logs through SLF4J; the desktop app keeps quiet rather than warn that no logger is bound.
    runtimeOnly(libs.slf4j.nop)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutinesTest)
    // The twenty-question eval seeds an in-memory Library from the synthetic Export, written to a fake file system.
    testImplementation(project(":core:fixtures"))
    testImplementation(libs.okio.fakefilesystem)
}

// The twenty-question eval (spec: a manually run JVM integration test) asks a live Ollama model and is not part
// of CI's test run: `test` leaves it out and `eval` runs it alone, skipping when Ollama is not reachable.
val evalHarnessClass = "com.jassemsendi.unpacked.eval.EvalHarness"

tasks.test {
    filter.excludeTestsMatching(evalHarnessClass)
}

tasks.register<Test>("eval") {
    description = "Asks the twenty eval questions of a live Ollama model and reports the score; skipped without Ollama."
    group = "verification"
    val tests = sourceSets.test.get()
    testClassesDirs = tests.output.classesDirs
    classpath = tests.runtimeClasspath
    filter.includeTestsMatching(evalHarnessClass)
    testLogging.showStandardStreams = true
    outputs.upToDateWhen { false }
}

compose.desktop {
    application {
        mainClass = "com.jassemsendi.unpacked.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.jassemsendi.unpacked"
            packageVersion = "1.0.0"
        }
    }
}
