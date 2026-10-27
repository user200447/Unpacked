import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Shared settings for every core library: JVM, Android, iosArm64 and iosSimulatorArm64,
// explicit API, kotlin-test in commonTest, Kover and the quality gates.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlinx.kover")
    id("unpacked.quality")
}

val libs = the<VersionCatalogsExtension>().named("libs")

configure<KotlinMultiplatformExtension> {
    explicitApi()

    jvm()
    iosArm64()
    iosSimulatorArm64()

    (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        namespace = "com.jassemsendi.unpacked.${project.name}"
        compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
        minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        withHostTest {
            // Host tests run against stub Android classes; a stub that returns a default beats one that throws.
            isReturnDefaultValues = true
        }
    }

    sourceSets.commonTest.dependencies {
        implementation(libs.findLibrary("kotlin-test").get())
    }
}
