plugins {
    // Declared here so each plugin is loaded once, in the root classloader,
    // instead of once per subproject.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
}

// One merged coverage report over every module that runs common tests.
dependencies {
    kover(project(":core:model"))
    kover(project(":core:ingest"))
    kover(project(":core:data"))
    kover(project(":core:insights"))
    kover(project(":core:agent"))
    kover(project(":ui"))
}
