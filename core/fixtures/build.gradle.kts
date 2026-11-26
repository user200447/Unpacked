// Test fixtures shared by the core modules' commonTest source sets: the synthetic Instagram
// Export generator and the hand-written record constants. Never a dependency of main code.
plugins {
    id("unpacked.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.okio)
        }
    }
}
