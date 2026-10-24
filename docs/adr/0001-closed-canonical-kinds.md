---
status: accepted
date: 2026-10-24
---

# One closed Kind vocabulary shared by every Source

Each Source export has dozens of file types (Instagram alone has about 40) and a second Source, Google Takeout, is planned. We map every Event and Claim to one of a small closed set of canonical Kinds (roughly 12 Event Kinds and 8 Claim Kinds, each with an `Other` escape hatch) and keep the Source's own file name beside it as the Raw kind. Insight cards, agent tools and the gap report are written once against Kinds, so a second Source is a new adapter and nothing else, and a cross-Source question ("how much did I watch?") has one answer.

## Considered options

- Per-Source raw kinds (`instagram.stories_viewed`): faithful and free, but every card and tool becomes Source-specific and cross-Source questions need the model to know both vocabularies.
- Open string kinds with a registry: nothing stops two adapters naming the same thing differently.

## Consequences

- A new Kind is added only when a card, a tool or the gap report needs to distinguish it. Resist adding one per file.
- Unknown files still import as `Other` and stay countable and browsable in the generic explorer without a schema change.
