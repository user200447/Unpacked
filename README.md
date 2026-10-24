# Unpacked

A privacy-first Kotlin Multiplatform app that turns the personal-data archives people download from large platforms into a searchable local timeline, answers questions about that data with provenance, and reports what the archive leaves out compared with GDPR Article 15.

Nothing leaves the device by default. The domain language and the decisions behind the design live in [CONTEXT.md](CONTEXT.md) and [docs/adr](docs/adr).

## Modules

| Module | Role |
| --- | --- |
| `core:model` | Canonical model: Events, Claims, Entities, Kinds, Provenance. Plain Kotlin, no dependencies. |
| `core:ingest` | Reads an Export (zip or folder) through a Source adapter into the canonical model. |
| `core:data` | Local library on Room KMP; the only module that touches persistence. |
| `core:insights` | Insight cards and the Gap report as pure functions over repository interfaces. |
| `core:agent` | Koog agent, read-only Tools, provider selection, citation validation. |
| `ui` | Compose Multiplatform screens shared by all targets; produces the `UnpackedKit` iOS framework. |
| `androidApp`, `desktopApp`, `iosApp` | Thin entry points. |
| `build-logic` | Gradle convention plugins (`unpacked.kmp.library`, `unpacked.kmp.compose`, `unpacked.quality`). |

Every core module compiles for JVM, Android, iosArm64 and iosSimulatorArm64 with explicit API mode on.

## Requirements

- JDK 21 (the Gradle daemon toolchain is pinned in `gradle/gradle-daemon-jvm.properties`)
- Android SDK with `sdk.dir` in `local.properties`
- Xcode 26 for the iOS app

## Build and run

```sh
./gradlew :desktopApp:run                 # desktop
./gradlew :androidApp:assembleDebug       # Android APK
open iosApp/iosApp.xcodeproj              # iOS: run the iosApp scheme in Xcode
```

## Tests and quality gates

```sh
./gradlew ktlintCheck detekt              # lint; a violation fails the build
./gradlew jvmTest testAndroidHostTest     # common tests on JVM and Android
./gradlew iosSimulatorArm64Test           # common tests on the iOS simulator (macOS only)
./gradlew compileKotlinIosArm64           # device slice of every module (macOS only)
./gradlew koverHtmlReport                 # merged coverage report in build/reports/kover/html
./gradlew check                           # lint plus tests on every target the host can run
```

`./gradlew ktlintFormat` fixes formatting. The CI workflow in `.github/workflows/ci.yml` runs lint, all tests, the desktop and Android builds, and both iOS slices (simulator via `xcodebuild`, device via the Kotlin compiler) on every push.

## License

Apache-2.0. See [LICENSE](LICENSE).
