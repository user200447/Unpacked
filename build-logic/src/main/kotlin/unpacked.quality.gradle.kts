import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

// ktlint and detekt, wired into `check` so a violation fails the build.
// Applied to every Kotlin module: the core libraries, `ui` and the two JVM-side apps.
plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

val libs = the<VersionCatalogsExtension>().named("libs")

configure<KtlintExtension> {
    version.set(libs.findVersion("ktlint").get().requiredVersion)
    android.set(false)
    filter {
        exclude { it.file.path.contains("/build/generated/") }
    }
}

configure<DetektExtension> {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    // The default detekt source set is src/main and src/test; multiplatform modules keep
    // their sources under src/<sourceSet>/kotlin, so lint every Kotlin file under src.
    source.setFrom(fileTree("src") { include("**/*.kt") })
}
