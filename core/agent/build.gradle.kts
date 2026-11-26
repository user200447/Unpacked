plugins {
    id("unpacked.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The Tools read the Library through core:data's repository interfaces and reuse the Gap report
            // reader from core:insights. The Tools themselves run with no model.
            api(project(":core:insights"))
            api(libs.kotlinx.datetime)
            // The Koog graph the Ask agent runs on; the prompt executor and model types the provider binding names
            // come with it. The Tool trace is recorded by the Tool wrappers themselves, so no Koog feature is needed.
            api(libs.koog.agentsCore)
            // What a Tool output looks like to a model: JSON the agent renders itself, with citation markers.
            implementation(libs.kotlinx.serializationJson)
        }
        commonTest.dependencies {
            implementation(project(":core:fixtures"))
            implementation(libs.kotlinx.coroutinesTest)
            // One seam seeds the fakes by running the real adapter over the synthetic Export in memory.
            implementation(libs.okio.fakefilesystem)
        }
    }
}
