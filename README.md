# Unpacked

A privacy-first Kotlin Multiplatform app that turns the personal-data archives people download from large platforms into a searchable local timeline, answers questions about that data with provenance, and reports what the archive leaves out compared with GDPR Article 15.

Nothing leaves the device by default. The domain language and the decisions behind the design live in [CONTEXT.md](CONTEXT.md) and [docs/adr](docs/adr).

## Try it

Everything below works from a fresh clone with the Sample Export in this repository; no account, no network and no data of your own are needed.

### Prerequisites

- JDK 21. The Gradle daemon's toolchain is pinned in `gradle/gradle-daemon-jvm.properties`, and Gradle downloads it when it is missing.
- For Android: the Android SDK, with `sdk.dir` in `local.properties` (Android Studio writes it) or `ANDROID_HOME` set, and a device or emulator running API 24 or later.
- For iOS: a Mac with Xcode 26.
- For the agent on desktop, optionally: [Ollama](https://ollama.com) with a model that calls tools (see [Turning the agent on](#turning-the-agent-on)).

### The Sample Export

`sample/instagram-sample-export.zip` is a small Instagram Export in the real folder layout, with nobody's data in it: every name, caption, device and message count is made up by the generator in `core:fixtures`. It holds the ads shown, the posts viewed, logins, follows, the untimed files that become Claims, message threads (counted, as the app does with every thread) and one file no mapper knows, so the Explorer has something to show. It imports with no Failed file, which `SampleExportTest` in `core:data` checks on every test run.

The zip is written by a Gradle task from the same generator the tests use, and regenerating it changes nothing unless the generator changed:

```sh
./gradlew writeSampleExport
```

### Desktop

```sh
./gradlew :desktopApp:run
```

Read the Welcome screen, choose "Choose an Export", then "Choose a zip" and pick `sample/instagram-sample-export.zip`. An unzipped Export folder works too, through "Choose a folder". The Import takes a second; "See your timeline" leads to Home.

The Library and the Settings are kept in the per-user application data directory (`~/Library/Application Support/Unpacked` on macOS, `%APPDATA%\Unpacked` on Windows, `~/.local/share/Unpacked` elsewhere). Deleting an Import from the Imports screen removes everything it produced.

### Android

```sh
./gradlew :androidApp:installDebug
adb push sample/instagram-sample-export.zip /sdcard/Documents/
```

`adb` is in the SDK's `platform-tools` directory. Open Unpacked, choose "Choose an Export", then "Choose a zip"; in the system picker open the device's "Documents" folder and pick the zip. The zip is read in place through its document URI, and the Import runs in a foreground service with a progress notification, so leaving the app does not stop it. On API 33 and later the app asks once for permission to show that notification.

### iOS

1. Open `iosApp/iosApp.xcodeproj` in Xcode 26. Leave `TEAM_ID` empty in `iosApp/Configuration/Config.xcconfig` for a simulator; set it to your Apple team for a device.
2. Select the `iosApp` scheme and an iPhone simulator, then run. The target's build phase runs `./gradlew :ui:embedAndSignAppleFrameworkForXcode`, which builds the `UnpackedKit` framework the Swift shell embeds; the first build takes several minutes.
3. Put the Sample Export where the app can see it. The app's own Documents folder appears in the Files app as "Unpacked"; on a simulator, copy the zip there from the command line:

   ```sh
   cp sample/instagram-sample-export.zip \
     "$(xcrun simctl get_app_container booted com.jassemsendi.unpacked.Unpacked data)/Documents/"
   ```

   On a device, AirDrop the zip or save it into the "Unpacked" folder in Files.
4. Choose "Choose an Export", then "Choose a zip", and pick the zip in the document picker (On My iPhone, then Unpacked). It is read in place; the Import runs while the app is in the foreground.

The same build runs from the command line, as CI does:

```sh
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO
```

### Turning the agent on

The Ask screen needs a model. Every other screen works without one, and when no model answers the Ask screen says why and points to the Insight cards and the Gap report.

**Local, on desktop.** Install Ollama and pull a model that calls tools, then ask; nothing leaves the machine. The app looks for the first installed model from `qwen3`, `gpt-oss`, `llama3.1`, `llama3.2`, `mistral` and `qwen2.5` that Ollama reports as able to call tools; `UNPACKED_OLLAMA_MODEL=<name>` names one when several are installed. The provider is asked anew for every question, so starting or stopping Ollama is noticed without a restart.

```sh
ollama pull qwen3
./gradlew :desktopApp:run
```

**Cloud, on any platform.** Settings (from Home) switches the agent among local, cloud and off, and takes a Google Gemini key for Gemini Flash on its free tier. The key goes into the platform's secure storage: the macOS Keychain (an owner-only file in the app's data directory on Windows and Linux), the Android Keystore, the iOS Keychain. Before the first request of every question asked of the cloud model, the Payload inspector shows the exact request and waits for you to send or refuse it; only the question and the Tool outputs ever leave the device, never a record from the Export, and every answer is marked `local` or `cloud`.

**Android and iOS have no local model.** The phones offer cloud and off only and start off, so without a Gemini key the agent is off on iOS and on Android: the Timeline, Search, the Claims and Entities screens, the eight Insight cards, the Gap report, the Access request and the Explorer all run without it.

### What to try

1. **Home**: the eight Insight cards over the Sample Export (advertisers holding your contact details, ads shown, content viewed by day, logins by device and network, follows and unfollows by month, what the Source inferred, activity beyond the app, messages sent and received by month as counts only) and the Timeline under them, newest first, narrowed to one Kind on request. Every number on a card opens what it was counted from; every entry opens its Record with the file and position it came from.
2. **Search**: type `cour` to find the courier advertiser by the start of a word, or `sourdough` to find the two searches for it.
3. **Claims** and **Entities**: what the Source believes about the Export owner, grouped by Kind; the advertisers, accounts, devices, networks, topics, places and threads it named, each with its own timeline.
4. **Ask**, with a model on: "How many ads was I shown?", "Which advertiser showed me the most ads?", "On which day was I most active?" (which draws a chart), "In the Gap report, is the purposes item covered, partial or missing?". Each answer shows its Tool trace, cites by marker, and is validated against the Tool outputs: a number the Tools never returned is retried once and then flagged, never hidden. Stop Ollama and ask again to see the fallback.
5. **Gap report**: the nine items of GDPR Article 15 with a Verdict for the Import and the evidence behind each, then **Access request** for the letter asking the Source for what is Missing or Partial, copied to the clipboard or, on desktop, saved as a text file.
6. **Explorer**: every file of the Export as the Source wrote it, the Skipped `subscription_for_no_ads.json` included, read from the zip on demand and stored nowhere.
7. **Settings**: where the agent runs, the Gemini key, and the Tested Exports.

### Tested Exports

The Instagram adapter was tested against the JSON Export downloaded on 2026-10-20 through "Download your information" with all of your information, all time, in the JSON format, as the zip or the unzipped folder; the HTML format is not read. Each adapter declares its own list, and Settings shows them all. A large Export arrives as several parts, each imported on its own. The app imports the owner's real Export of that date with no Failed file (the opt-in smoke test below), and the Sample Export on every test run.

## Modules

| Module | Role |
| --- | --- |
| `core:model` | Canonical model: Events, Claims, Entities, Kinds, Provenance, and the Import summary that reports one run. Plain Kotlin, no dependencies. |
| `core:ingest` | Reads an Export (zip or folder) through a Source adapter into the canonical model. |
| `core:data` | Local library on Room KMP; the only module that touches persistence. |
| `core:fixtures` | The synthetic Export generator and the hand-written records the tests share, and the Sample Export built from them. Never a dependency of main code. |
| `core:insights` | Insight cards and the Gap report as pure functions over repository interfaces. |
| `core:agent` | The eight read-only Tools over the repository interfaces and the query-time Match rule, with no model dependency; the Koog Ask agent that classifies a question, calls the Tools under a step cap, cites by marker, validates every number in the answer against the Tool outputs and charts a count over time or a ranking; the model providers: the platform's own (Ollama on desktop, none on the phones), Gemini Flash with a key from the platform's secure storage, and the switch between them that Settings drives; the Payload inspector's rendering of a cloud request. |
| `ui` | Compose Multiplatform screens shared by all targets (MVI per screen, Koin-injected ViewModels); produces the `UnpackedKit` iOS framework, whose entry point in `ui/iosMain` starts the app on iOS. |
| `androidApp`, `desktopApp`, `iosApp` | Thin entry points. |
| `build-logic` | Gradle convention plugins (`unpacked.kmp.library`, `unpacked.kmp.compose`, `unpacked.quality`, `unpacked.shared-code`). |

Every core module compiles for JVM, Android, iosArm64 and iosSimulatorArm64 with explicit API mode on.

## Shared code

Lines of code per source set, counted by [cloc](https://github.com/AlDanial/cloc) over every `.kt`, `.kts` and `.swift` file outside the build directories. `commonMain` is the Kotlin every target shares; `jvmMain`, `androidMain`, `iosMain` and the apps' `main` are what each platform adds, and the Swift shell is the iOS app's entry point. `core:fixtures` exists for the tests and is counted with them, and `build-logic` holds the Gradle convention plugins. The table is written by one command, which needs cloc on the path (`brew install cloc` or `apt install cloc`) and rewrites this section in place; CI runs it on every push and warns when the table here is behind the tree:

```sh
./gradlew sharedCodeReport
```

<!-- shared-code-report:start -->
| Source set | Modules | Files | Lines of code |
| --- | --- | ---: | ---: |
| `commonMain` | core:agent, core:data, core:ingest, core:insights, core:model, ui | 150 | 11,375 |
| `main` | androidApp, desktopApp | 16 | 921 |
| `iosMain` | ui | 6 | 335 |
| `Swift` | iosApp | 2 | 24 |
| `androidMain` | core:data | 1 | 14 |
| `nonAndroidMain` | core:data | 1 | 10 |
| **Production** | | 176 | 12,679 |
| `commonTest` | core:agent, core:data, core:ingest, core:insights, core:model, ui | 62 | 8,927 |
| `commonMain` | core:fixtures | 18 | 1,400 |
| `test` | androidApp, desktopApp | 12 | 1,046 |
| `jvmTest` | core:data, ui | 4 | 207 |
| `iosTest` | core:data, ui | 3 | 136 |
| `jvmMain` | core:fixtures | 1 | 24 |
| `androidHostTest` | core:data | 1 | 6 |
| **Tests** | | 101 | 11,746 |
| `Gradle scripts` | androidApp, core:agent, core:data, core:fixtures, core:ingest, core:insights, core:model, desktopApp, root, ui | 11 | 406 |
| `Build logic` | build-logic | 9 | 400 |

Common Kotlin (`commonMain`) is 90% of the production code: 11,375 of 12,679 lines.
<!-- shared-code-report:end -->

## Tests and quality gates

```sh
./gradlew ktlintCheck detekt              # lint; a violation fails the build
./gradlew jvmTest testAndroidHostTest     # common tests on JVM and Android
./gradlew :desktopApp:test :androidApp:testDebugUnitTest   # app-module tests
./gradlew iosSimulatorArm64Test           # common and iOS tests of every module on the iOS simulator (macOS only)
./gradlew compileKotlinIosArm64           # device slice of every module (macOS only)
./gradlew koverHtmlReport                 # merged coverage report in build/reports/kover/html
./gradlew check                           # lint plus tests on every target the host can run
./gradlew -p build-logic test             # the shared-code report's own tests
```

An opt-in smoke test imports your own Instagram Export, asserts that no file Failed and prints the count per Kind. It runs only when the variable is set, and the Export is never committed:

```sh
UNPACKED_INSTAGRAM_EXPORT=~/Downloads/instagram-export.zip ./gradlew :core:data:jvmTest --tests '*RealExportSmokeTest*'
```

The twenty-question eval asks a live Ollama model twenty fixed questions over the Sample Export, scores each answer against the value the Sample Export is known to hold (a flagged answer counts as a failure) and prints a per-question table with the overall score; it passes at 70 percent or better, and `qwen3` scored 16 of 20 on 2026-11-26. It has its own task, is left out of `test` and CI, is skipped when Ollama is not reachable, and ends early when the model goes away mid-run:

```sh
./gradlew :desktopApp:eval                # UNPACKED_OLLAMA_MODEL=<name> picks the model, as in the app
```

`./gradlew ktlintFormat` fixes formatting. The CI workflow in `.github/workflows/ci.yml` runs lint, all tests, the shared-code report, the desktop and Android builds, and both iOS slices (simulator via `xcodebuild`, device via the Kotlin compiler) on every push.

## License

Apache-2.0. See [LICENSE](LICENSE).

Unpacked carries no third-party logo or brand asset: the app icon is its own mark, the Sources are named in words, and the Sample Export, like everything shown in a demo, is synthetic.
