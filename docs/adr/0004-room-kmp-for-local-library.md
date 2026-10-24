---
status: accepted
date: 2026-10-24
---

# Room KMP is the local library database

The local library (Events, Claims, Entities, Provenance, full-text search) needs one SQLite-backed store on JVM, Android and iOS. We chose Room KMP with the bundled SQLite driver (`androidx.sqlite:sqlite-bundled`), which gives one driver for all three targets, over SQLDelight. The owner knows Room well, and in a six-week solo build familiarity outweighs SQLDelight's SQL-first FTS5 and annotation-free models.

## Considered options

- SQLDelight 2.x: FTS5 as plain SQL, no annotations on model classes, but three drivers and a new tool to learn.

## Consequences

- Room supports FTS3/FTS4, not FTS5. Full-text search uses an `@Fts4` table; there is no BM25 ranking, so results order by time or by a simple hit count.
- Room needs KSP on every target, which adds build time to the iOS compile in particular.
- Whether canonical model classes carry Room annotations or persistence uses separate entity classes with mappers is decided in ADR 0005.
