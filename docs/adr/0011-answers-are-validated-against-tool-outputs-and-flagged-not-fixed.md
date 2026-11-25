---
status: accepted
date: 2026-11-25
---

# Every number in an answer is validated against the Tool outputs; a failing answer is retried once, then shown flagged

The Ask agent runs as one Koog graph strategy per question: a classification step chooses which of the eight Tools
the model is offered, the model calls them under a step cap, and the answer it writes is checked before anyone sees
it. The check is mechanical and model-free: every number in the answer, compared canonically, must appear in some
Tool output of that question, a refusal's wording included. A citation marker that names no cited Provenance is not
a failure; it is shown as plain text instead of a link. An answer that fails is sent back to the model once, naming
the numbers it could not find. One that fails again is shown with
an unverified flag that names those numbers. The app never edits an answer, never drops a number from it, and never
asks a model to judge another model's answer.

Citations are markers, `[3]`, assigned per question in the order the Tool outputs first cite a Provenance. The model
sees markers and never a record's file path or index (the Gap report's evidence names the Export's files, as the
report does on its own screen), so a cloud model would receive nothing more than ADR 0006 allows, and the screen
resolves a marker back to the Record through the same index.

The model comes from a `ModelProvider` the app binds in Koin (ADR 0008): Ollama on localhost on desktop, no model
on the phones. It is asked anew for every question, so the screen notices Ollama stopping or starting without a
restart.

## Consequences

- The validator and the citation index are pure functions in `core:agent`, tested on their own; the strategy, the
  retry and the step cap are tested against a scripted `PromptExecutor` with no live model, as the acceptance
  criteria of ticket #20 ask.
- Formatting alone never fails an answer ("1,200" matches 1200), but a number the Tools never returned always does,
  even a correct one the model knew by itself. A flagged answer is a signal to the user, not an error of the app.
- The Tool outputs render times in the Library's zone, so a time the model repeats verifies; a model that converts
  zones on its own gets flagged, which is what we want.
- A model that keeps calling Tools is stopped at eight calls, retry included, and the user sees the calls it made.
  At the cap the model gets its last Tool results with no Tool on offer, so a model that has what it needs answers
  rather than being cut off; a turn that asks for several Tools at once never takes the trace past the cap. The
  Koog iteration limit stays as a backstop behind the cap and never fires first.
- The Ollama binding lives in `desktopApp` (ADR 0008); `core:agent` holds the interface, the pure model choice and
  the no-model binding the phones use.
- Raising the Android JVM target from 11 to 17 was the price of Koog's inline graph DSL; nothing else needed it.
