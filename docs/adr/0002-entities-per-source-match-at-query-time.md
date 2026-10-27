---
status: accepted
date: 2026-10-24
---

# Entities belong to one Source; cross-Source identity is a query-time Match

The headline agent question is "which advertisers appear in both my Instagram and Google data?", which tempts a global, merged Entity table. We instead keep every Entity owned by the Source that produced it, so each stored row traces to one file in one Export, and we compute cross-Source identity at query time as a Match: two Entities from different Sources whose normalized names agree, returned with both original names so the user can judge.

## Considered options

- Merge at import into a global Entity: simpler queries, but the import makes identity judgments, a wrong merge is invisible, and a better matching rule forces a re-import.

## Consequences

- Within a Source, Entity identity is `source:kind:key`, where key is the Source's own stable identifier when it has one, else a normalized display name (trimmed, lowercased, whitespace collapsed).
- A stable identifier is normalized only as far as the Source itself treats it as equivalent: Instagram usernames are case-insensitive, so they are lowercased; an advertiser name that is the only identifier is normalized like a display name. The display name is always stored as given.
- Improving the matching rule changes answers without touching the database.
- The UI must present Matches as "these look like the same advertiser", never as a single merged row.
