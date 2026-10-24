---
status: accepted
date: 2026-10-24
---

# Direct messages are imported as counts, never as text

Message threads are the largest and most personal part of an Export and contain other people's words and images. We import one Event per message (Kind MessageSent or MessageReceived, a thread Entity, a time) and store no message body, no media reference and no participant list beyond the thread title. The timeline and cards get the real volume of messaging; the database never holds anything a third party wrote. Full-text import of messages is a possible later addition, not a planned one.

## Considered options

- Skip messages entirely: simplest, but removes the single largest activity signal from the timeline.
- Import full text with search: useful, but duplicates private third-party content into the app's database and makes any demo on real data impossible.
