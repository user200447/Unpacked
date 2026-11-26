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
