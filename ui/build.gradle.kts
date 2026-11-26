plugins {
    id("unpacked.kmp.compose")
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            // The iOS app's own module is named Unpacked, so the framework needs another name.
            baseName = "UnpackedKit"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // The library is reached through its repository interfaces and the Import use case only.
            implementation(project(":core:data"))
            // For the Export location and part types the Import screen names, and nothing that reads an Export.
            implementation(project(":core:ingest"))
            implementation(project(":core:insights"))
            // The Ask screen talks to the agent through its interface; the app binds which model answers.
            implementation(project(":core:agent"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiBackhandler)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.core)
            implementation(libs.koin.composeViewmodel)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.turbine)
        }
        jvmTest.dependencies {
            // Compose UI tests run on the desktop target, where Skiko renders without a device.
            implementation(libs.compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
    }
}
