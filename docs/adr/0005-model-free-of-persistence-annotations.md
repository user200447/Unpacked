---
status: accepted
date: 2026-10-24
---

# Canonical model classes carry no persistence annotations

Room (ADR 0004) could annotate `Event`, `Claim` and `Entity` directly, which is less code. We instead keep `core:model` as plain Kotlin and give `core:data` its own Room entity classes plus mappers in both directions. Insight cards, gap-report rules and agent tools then depend only on plain objects and run against in-memory fakes, and no module other than `core:data` pulls in Room or KSP.

## Consequences

- About a day of mapper boilerplate and a mapper test per class.
- The `attributes` map is stored in a side table (or a converter) inside `core:data` only; the model never knows how.
