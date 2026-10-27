---
status: accepted
date: 2026-10-27
---

# An Import is written file by file, and the latest Import of an Export owns its rows

Event and Claim ids are the hash of their Provenance (spec), so importing the same Export twice cannot create duplicate rows. That leaves two questions the schema has to answer: which Import a re-imported row belongs to, and what a crash in the middle of a long Import leaves behind. We write the Import row first, commit each file's Events, Claims, Entities and attributes in one transaction as the file finishes, and set the completion time last. A re-imported record replaces its earlier row, so it moves to the newer Import; deleting the older duplicate Import then removes only its file inventory. Entities carry no Import; deleting an Import removes the Entities that no remaining Event or Claim refers to.

## Considered options

- One transaction per Import: nothing half-written after a crash, but every record held in memory until the end, a multi-minute write lock on a real Export, and progress on screen that outruns what is on disk.
- Insert-or-ignore on re-import: rows stay with the first Import, so a later, successful Import owns nothing and deleting the first one empties the library behind it.
- An import–event join table: correct in every case, at the cost of a third join table and ownership counting for a case the UI rarely shows.

## Consequences

- An Import with no completion time is a run that was cancelled or crashed. It stays visible, its counts are real, and the user deletes or re-runs it; the UI must show it as incomplete.
- `Library.import` emits the Importer's steps as they happen and ends with the stored Import, so progress on screen matches the database.
- Attributes live in three typed side tables (`event_attributes`, `claim_attributes`, `entity_attributes`) with cascading foreign keys and a position column, so a record's label order survives the round trip and deleting an owner needs no bookkeeping.
- Two Events from one record would share an id. Mappers produce at most one Event and one Claim per record; a mapper that needs more must first give the Provenance a sub-index.
