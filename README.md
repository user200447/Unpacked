# Unpacked

A privacy-first Kotlin Multiplatform app that turns the personal-data archives people download from large platforms into a searchable local timeline, answers questions about that data with provenance, and reports what the archive leaves out compared with GDPR Article 15.

Nothing leaves the device by default. The domain language and the decisions behind the design live in [CONTEXT.md](CONTEXT.md) and [docs/adr](docs/adr).

## Modules

| Module | Role |
| --- | --- |
| `core:model` | Canonical model: Events, Claims, Entities, Kinds, Provenance, and the Import summary that reports one run. Plain Kotlin, no dependencies. |
| `core:ingest` | Reads an Export (zip or folder) through a Source adapter into the canonical model. |
| `core:data` | Local library on Room KMP; the only module that touches persistence. |
| `core:insights` | Insight cards and the Gap report as pure functions over repository interfaces. |
| `core:agent` | The eight read-only Tools over the repository interfaces and the query-time Match rule, with no model dependency; the Koog agent, provider selection and citation validation follow. |
| `ui` | Compose Multiplatform screens shared by all targets (MVI per screen, Koin-injected ViewModels); produces the `UnpackedKit` iOS framework, whose entry point in `ui/iosMain` starts the app on iOS. |
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

On Android the Export zip is picked through the system file picker and read in place through its document URI; a running Import is hosted in a foreground service with a progress notification, so leaving the app does not stop it.

On iOS the Export zip is picked through the system document picker and read in place under the security scope the picker grants; the Import runs while the app is in the foreground. The app's own Documents folder is visible in the Files app as "Unpacked", so a zip can be dropped there (or into a simulator with `xcrun simctl`) and picked from it.

### iOS, step by step

1. Install Xcode 26 and open `iosApp/iosApp.xcodeproj`.
2. Leave `TEAM_ID` empty in `iosApp/Configuration/Config.xcconfig` for a simulator; set it to your Apple team for a device.
3. Select the `iosApp` scheme and an iPhone simulator, then run. The target's build phase runs `./gradlew :ui:embedAndSignAppleFrameworkForXcode`, which builds the `UnpackedKit` framework the Swift shell embeds; the first build takes several minutes.
4. To try an Import on the simulator, put a zip into the app's Documents folder and pick it with "Choose a zip". Any Instagram Export zip does; the synthetic one the tests use is written by `SyntheticInstagramExport` in `core:fixtures`, and zipping its folder gives a small Export with no personal data in it.

   ```sh
   xcrun simctl install booted <path to Unpacked.app>
   cp instagram-export.zip "$(xcrun simctl get_app_container booted com.jassemsendi.unpacked.Unpacked data)/Documents/"
   ```

The same build runs from the command line, as CI does:

```sh
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO
```

## Tests and quality gates

```sh
./gradlew ktlintCheck detekt              # lint; a violation fails the build
./gradlew jvmTest testAndroidHostTest     # common tests on JVM and Android
./gradlew :desktopApp:test :androidApp:testDebugUnitTest   # app-module tests
./gradlew iosSimulatorArm64Test           # common and iOS tests of every module on the iOS simulator (macOS only)
./gradlew compileKotlinIosArm64           # device slice of every module (macOS only)
./gradlew koverHtmlReport                 # merged coverage report in build/reports/kover/html
./gradlew check                           # lint plus tests on every target the host can run
```

An opt-in smoke test imports your own Instagram Export, asserts that no file Failed and prints the count per Kind. It runs only when the variable is set, and the Export is never committed:

```sh
UNPACKED_INSTAGRAM_EXPORT=~/Downloads/instagram-export.zip ./gradlew :core:data:jvmTest --tests '*RealExportSmokeTest*'
```

`./gradlew ktlintFormat` fixes formatting. The CI workflow in `.github/workflows/ci.yml` runs lint, all tests, the desktop and Android builds, and both iOS slices (simulator via `xcodebuild`, device via the Kotlin compiler) on every push.

## License

Apache-2.0. See [LICENSE](LICENSE).
