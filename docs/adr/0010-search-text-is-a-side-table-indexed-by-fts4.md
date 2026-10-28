---
status: accepted
date: 2026-10-28
---

# Search text is a side table for Events and one for Claims, each indexed by an external-content FTS4 table

Full-text search runs on FTS4 (ADR 0004), but the words to index are spread over the attribute side
tables and the Entity names, and an FTS table has no foreign keys to be kept in step with Imports and
their deletion. We write the searchable text of each Event and Claim once more, as one row in
`event_text` or `claim_text` keyed by the Event's or Claim's id with a cascading foreign key to it, and
declare the FTS4 tables `event_search` and `claim_search` as external-content indexes over those two.
Room generates the triggers that mirror every insert, update and delete of a text row into its index,
and SQLite fires them for cascades too, so the Import writes nothing but the text row and deleting an
Event or Claim, or an Import, takes its search rows with it with no code of its own.

What is indexed is the Event's or Claim's attribute values and the display names of its Entities, never
its labels: "Caption" must not find every ad. The tokenizer is unicode61, which folds case and strips
diacritics, so "zoe" finds "Zoë" and the repaired mojibake names read back as typed. What the user
types is split at anything that is not a letter or digit and each word becomes a quoted prefix term
(`"cour*"`), so every word is required, each matches from its start, and the characters FTS4 reads as
operators are only ever word breaks. Hits are ordered by time (an Event's time, a Claim's "as of" time,
undated Claims last), with the table and the id as tie-breakers so pages never overlap.

## Considered options

- A standalone FTS4 table keyed by the Event's or Claim's id, deleted by hand beside each delete. Fewer
  tables, but Room's KSP processor for Kotlin/Native cannot read the default content-entity marker of a
  standalone `@Fts4` table ("Cannot find external content entity class"), and every future delete path
  would have to remember the index.
- One FTS table for Events and Claims alike, with the table named in a not-indexed column. Its rowid
  could not be the Event's or Claim's id, since one read from the same Record as the other shares it,
  and deletes would scan.
- Matching whole words only, or relevance ordering. Whole-word matching makes search-as-you-type show
  nothing until the last word is complete; FTS4 has no BM25, and ADR 0004 already settled on time order.

## Consequences

- The schema is at version 3 with four more tables. Every Event's and Claim's text is stored twice: once
  in its attribute rows and once in its text row, which the FTS index reads from on demand.
- The text row is written when its Event or Claim is, so it carries the Entity display names of that
  moment; a later Import that renames an Entity (ADR 0007: the latest wins) does not reindex older
  Events and Claims. Harmless while an Entity's key is its normalised name, and worth a re-index step
  if that ever changes.
- The search query is a `UNION ALL` over Events and Claims ordered in SQL, so one page costs one FTS
  query per type plus the usual hydration by id.
- `SearchRepository.search(text, limit, offset)` answers with `SearchHit.OfEvent` or `OfClaim`, the
  same model objects the timeline reads, so a hit opens the same Record detail.
- The agent's `searchText` Tool (spec) can be the same repository method.
