plugins {
    id("unpacked.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.kotlinx.coroutinesCore)
            api(libs.okio)
            // For the dates of the Exports each Source was tested against.
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serializationJson)
        }
        commonTest.dependencies {
            implementation(project(":core:fixtures"))
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.okio.fakefilesystem)
        }
    }
}
