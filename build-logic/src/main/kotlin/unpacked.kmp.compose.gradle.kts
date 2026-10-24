import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// A KMP library that also ships Compose Multiplatform UI. Composables, previews and
// ViewModels are not a consumed API, so explicit API mode is switched off here.
plugins {
    id("unpacked.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

configure<KotlinMultiplatformExtension> {
    explicitApi = ExplicitApiMode.Disabled
}
