---
status: accepted
date: 2026-10-27
---

# Expected failures are typed Results, caught by the layer that owns the exception

Before this, an Export that would not open threw out of the Import use case's Flow, a database that
refused a read or write threw out of a repository, and each ViewModel either caught a `Throwable` and
showed its `message` or did not catch at all. The user could read a driver's wording, and a
database error on the Home screen would have crashed the app.

We now give `core:model` one `Result<D, E : Error>` with `Success` and `Error` cases, the
`EmptyResult` alias and the `map`, `onSuccess`, `onFailure` and `asEmptyResult` helpers, and the
rule that a failure a user can meet is returned as a `Result.Error` carrying a typed `Error`, never
thrown. Each layer declares the errors it owns and is the only place its exceptions are caught:

- `core:ingest` turns what okio throws into `ExportError` (`NOT_FOUND`, `NOT_READABLE`) in `Export.open`
  and `Export.files()`. A file that will not parse is still a `FileResult.Failed` with its reason, because
  that reason is stored and shown per file and the run goes on.
- `core:data` turns what Room and SQLite throw into `DataError.Local` (`DISK_FULL`, `NOT_FOUND`,
  `UNKNOWN`) in one `localCall` helper. Every repository method answers with a `Result`; an absent
  row is `NOT_FOUND`, not null. `Library.import` ends the run with `ImportStep.Failed` instead of
  failing the Flow, so the ViewModel never sees an exception from a run.
- `ImportError` names why a run stopped: `Reading(ExportError)` or `Writing(DataError.Local)`.
- `ui` maps each error to words in one `toMessage()` per error type, the way the presentation
  skill's `toUiText()` does; plain strings stand in for `UiText` until the app has string resources.
  A read that fails leaves the last successful read on screen beside the message.

Programming errors (`checkNotNull`, `error(...)`) still throw: they are bugs to fix, not outcomes
to show.

## Considered options

- `kotlin.Result`: untyped failure, so a ViewModel cannot tell "not a zip" from "disk full" without
  inspecting exception classes, and the compiler cannot check that every case is worded.
- Exceptions caught in the ViewModels only: what the user reads would depend on each driver's
  message, and every new call site would have to remember the catch.

## Consequences

- Tests unwrap with `orFail()` from `core:fixtures`, which now depends on `core:model`.
- `DataError.Network` arrives with the agent's cloud providers (`core:agent`); nothing uses a network yet.
- The ingest internals (`InstagramDecoder`) keep `kotlin.Result` where the only consumer turns it
  straight into a `FileResult.Failed` reason.
