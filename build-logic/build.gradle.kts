plugins {
    `kotlin-dsl`
}

group = "com.jassemsendi.unpacked.buildlogic"

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.android.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    implementation(libs.composeCompiler.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    // The shared-code report's grouping and rendering are plain functions with their own tests.
    testImplementation(libs.kotlin.testJunit)
}
