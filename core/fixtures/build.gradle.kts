import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

// Test fixtures shared by the core modules' commonTest source sets: the synthetic Instagram
// Export generator and the hand-written record constants. Never a dependency of main code.
plugins {
    id("unpacked.kmp.library")
}

kotlin {
    // Test code, not a library surface.
    explicitApi = ExplicitApiMode.Disabled

    sourceSets {
        commonMain.dependencies {
            api(libs.okio)
        }
    }
}
