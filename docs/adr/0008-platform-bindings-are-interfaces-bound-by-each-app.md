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

- A platform without a chooser yet binds `NoExportPicker`; the Import screen says so instead of
  failing to start. Android (#17) and iOS (#18) replace that binding.
- The same shape serves the later bindings the spec lists: secure key storage, the Ollama check,
  and the model provider factory.
- Koin is started in three places: `desktopApp`'s `main`, `UnpackedApplication` on Android and
  `MainViewController` in `ui/iosMain`. Each is a few lines and lists the same three modules.
- `ImportUseCase` is the only write path the UI has; everything else it reads goes through the
  repository interfaces.
