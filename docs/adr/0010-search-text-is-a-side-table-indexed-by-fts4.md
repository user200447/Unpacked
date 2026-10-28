---
status: accepted
date: 2026-10-28
---

# Search text is a side table per record type, indexed by an external-content FTS4 table

Full-text search runs on FTS4 (ADR 0004), but the words to index are spread over the attribute side
tables and the Entity names, and an FTS table has no foreign keys to be kept in step with Imports and
their deletion. We write the searchable text of each Event and Claim once more, as one row in
`event_text` or `claim_text` keyed by the record's id with a cascading foreign key to the record, and
declare the FTS4 tables `event_search` and `claim_search` as external-content indexes over those two.
Room generates the triggers that mirror every insert, update and delete of a text row into its index,
and SQLite fires them for cascades too, so the Import writes nothing but the text row and deleting a
record, or an Import, takes its search rows with it with no code of its own.

What is indexed is the record's attribute values and the display names of its Entities, never its
labels: "Caption" must not find every ad. The tokenizer is unicode61, which folds case and strips
diacritics, so "zoe" finds "Zoë" and the repaired mojibake names read back as typed. What the user
types is split at anything that is not a letter or digit and each word becomes a quoted prefix term
(`"cour*"`), so every word is required, each matches from its start, and the characters FTS4 reads as
operators are only ever word breaks. Hits are ordered by time (an Event's time, a Claim's "as of" time,
undated Claims last), with the record type and id as tie-breakers so pages never overlap.

## Considered options

- A standalone FTS4 table keyed by the record id, deleted by hand beside each record delete. Fewer
  tables, but Room's KSP processor for Kotlin/Native cannot read the default content-entity marker of a
  standalone `@Fts4` table ("Cannot find external content entity class"), and every future delete path
  would have to remember the index.
- One FTS table for both record types, with the type in a not-indexed column. Its rowid could not be the
  record id, since an Event and a Claim read from the same record share one, and deletes would scan.
- Matching whole words only, or relevance ordering. Whole-word matching makes search-as-you-type show
  nothing until the last word is complete; FTS4 has no BM25, and ADR 0004 already settled on time order.

## Consequences

- The schema is at version 3 with four more tables. Every record's text is stored twice: once in its
  attribute rows and once in its text row, which the FTS index reads from on demand.
- The search query is a `UNION ALL` of the two record types ordered in SQL, so one page costs one FTS
  query per type plus the usual hydration by id.
- `SearchRepository.search(text, limit, offset)` answers with `SearchHit.OfEvent` or `OfClaim`, the
  same model objects the timeline reads, so a hit opens the same Record detail.
- The agent's `searchText` Tool (spec) can be the same repository method.
