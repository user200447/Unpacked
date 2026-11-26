plugins {
    id("unpacked.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The repository interfaces are core:data's boundary; Room stays behind them and never reaches here (ADR 0005).
            api(project(":core:data"))
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(project(":core:fixtures"))
            implementation(libs.kotlinx.coroutinesTest)
            // The Gap report seam seeds its fakes by running the real adapter over the synthetic Export in memory.
            implementation(libs.okio.fakefilesystem)
        }
    }
}
