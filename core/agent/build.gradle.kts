plugins {
    id("unpacked.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The Tools read the Library through core:data's repository interfaces and reuse the Gap report
            // reader from core:insights. No model client is a dependency here: a Tool runs without one.
            api(project(":core:insights"))
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(project(":core:fixtures"))
            implementation(libs.kotlinx.coroutinesTest)
            // One seam seeds the fakes by running the real adapter over the synthetic Export in memory.
            implementation(libs.okio.fakefilesystem)
        }
    }
}
