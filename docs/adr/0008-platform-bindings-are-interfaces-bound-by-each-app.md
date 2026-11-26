---
status: accepted
date: 2026-10-27
---

# Platform bindings are interfaces the app binds in Koin, and the library reaches the UI as interfaces only

The Import screen needs a file chooser, which differs on every platform, and needs to run an Import
and read Imports back, which only `core:data` can do. We make every platform-specific capability an
interface declared in the module that uses it (`ExportPicker` in `ui`), implemented in the app that
owns the platform (`DesktopExportPicker` in `desktopApp`), and bound in that app's `startKoin`.
`core:data` exposes one `libraryModule(library, fileSystem)` that binds an open `Library` to the
repository interfaces and the `ImportUseCase`; each app opens the library at its own path and passes
it in. `ui`'s shared code (`commonMain`) depends on `core:data` for those interfaces only and never
names `Library` or Room. The one exception is the iOS entry point: the Swift shell can call only into
the `UnpackedKit` framework that `ui` produces, so `MainViewController` in `ui/iosMain` is where iOS
opens its library and starts Koin, playing the part `main` and `UnpackedApplication` play elsewhere.

## Considered options

- `expect`/`actual` for the chooser: ties every platform's implementation to the `ui` module's
  source sets and makes the ViewModel untestable without a platform.
- Koin modules assembled inside `ui`: `ui` would then construct the Room-backed `Library`, which the
  spec's seam 5 (ViewModel tests with fakes) and ADR 0005 keep out of the UI.

## Consequences

- Every app binds a chooser: desktop its native dialogs, Android the Storage Access Framework
  picker (#17) and iOS the system document picker (#18); only desktop offers a folder, and the
  Import screen hides the choices a platform's chooser does not report. Until #17 and #18 the
  phones bound a `NoExportPicker` that could pick nothing, so the screen said so instead of failing
  to start; a new platform does the same until its chooser lands.
- iOS has no app module of its own in Kotlin, so its binding, `IosExportPicker` with the UIKit
  presenter it drives, lives in `ui/iosMain` beside the entry point that binds it, and the Swift
  shell stays an entry point only. The app offers no folder to pick on iOS. The picker opens the zip in place: the path it hands to the
  Import is readable under the security scope the pick grants, which the binding holds for the rest
  of the process. After a relaunch an Export outside the app's own documents is unreachable again,
  and the Explorer's "pick it again" path covers that as it covers an Export that moved.
- The same shape serves the later bindings the spec lists: secure key storage, the Ollama check,
  and the model provider factory.
- Koin is started in three places: `desktopApp`'s `main`, `UnpackedApplication` on Android and
  `MainViewController` in `ui/iosMain`. Each lists the same three modules; Android's also builds
  the bindings those modules take (the picker, the Import host and the scope it runs in).
- `ImportUseCase` is the only write path the UI has; everything else it reads goes through the
  repository interfaces. A platform that must do something around a long run wraps it through
  `libraryModule`'s `hostImport`: Android hosts the run in a foreground service with a progress
  notification and keeps it going when the screen that started it is gone (#17).
- The `FileSystem` an app passes to `libraryModule` is its own binding too: desktop and iOS read the
  Export from the file system by path, Android through the content resolver from a document URI
  written as a path (`androidApp`'s `ContentFileSystem`), so `core:ingest` never learns about URIs.
