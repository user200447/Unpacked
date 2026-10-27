# Unpacked

A privacy-first app that turns the personal-data archives people download from large platforms into a searchable local timeline, answers questions about that data with provenance, and reports what the archive leaves out compared with GDPR Article 15.

## Language

**Source**:
A platform whose personal-data archive Unpacked can read. Instagram is the first Source; Google Takeout is the planned second.
_Avoid_: Platform, provider, integration

**Export**:
The archive a Source hands to a user under their right of access, as downloaded, before Unpacked touches it.
_Avoid_: Dump, download, takeout, archive (as a generic noun)

**Import**:
One run of reading one Export into the user's local library. The unit the user deletes: removing an Import removes every Event, Claim and Entity it produced.
_Avoid_: Sync, load, ingestion (as the noun for the result), session

**Export owner**:
The person the Export is about, as the Source names them in the Export's own profile file. Read once before any file is parsed; it is what tells a message sent from one received.
_Avoid_: User (for this), account holder, me, self

**Library**:
The user's local store of every Import, held in one on-device database and read only through repository interfaces. The Library is what cards, the agent's Tools and the gap report query; the Export itself is never consulted after its Import.
_Avoid_: Database, store, cache, local data (as the noun for the thing)

### Canonical model

**Event**:
Something that happened at a known moment, recorded by a Source: an ad shown, a post liked, a login, a follow. Every Event has a time.
_Avoid_: Activity, record, row, log entry, item

**Claim**:
A standing assertion a Source makes about the user with no moment of occurrence: an inferred interest, an advertiser holding the user's contact details, a location of interest. A Claim may carry an "as of" time saying when the Source last updated it; that does not make it an Event.
_Avoid_: Fact, inference, attribute, untimed event, metadata

**Entity**:
A named thing that recurs across Events and Claims and is worth counting or following: an advertiser, an account, a device, a topic, a place, a message thread. An Entity belongs to exactly one Source; it is what that Source said, nothing more.
_Avoid_: Actor, object, tag, dimension

**Match**:
A pairing of two Entities from different Sources that appear to be the same real-world thing, judged by name at query time and shown with both original names. Matches are never stored and never merge Entities.
_Avoid_: Merge, dedupe, link, alias, resolved entity

**Kind**:
The canonical category of an Event or Claim, drawn from one small closed vocabulary shared by every Source (for example ContentViewed, AdShown, Login; AdvertiserHoldsContact, InferredInterest). Cards, agent tools and the gap report speak only in Kinds.
_Avoid_: Type, category, event type, class

**Raw kind**:
The Source's own name for where an Event or Claim came from, typically the Export file it was read from. Kept beside the Kind for provenance and for the generic explorer; never used by cards or tools.
_Avoid_: Source type, file type, original kind

**Provenance**:
Where one Event or Claim came from: the Export, the file path inside it, the record's position in that file and, when the record lists several things, the item among them. Every Event and Claim has exactly one Provenance, and every citation the agent gives is a Provenance. What the user sees when they click through is the record's own label/value pairs plus this location.
_Avoid_: Source (in this sense), origin, reference, citation (for the stored thing), evidence

**Timeline**:
The Home screen's list of the Library's Events, newest first, read a page at a time and narrowed to one Kind on request. Claims never appear in it, even when they carry an "as of" time; opening any entry shows the record's own label and value pairs, its Raw kind and its Provenance, and every time reads in the device's time zone.
_Avoid_: Feed, history, activity log, event list

### Insights

**Insight card**:
One of a fixed set of eight deterministic summaries computed from the library with no model involved: for example advertisers holding the user's contact info, logins by device, content viewed per hour. Cards are what the user sees when no agent is available.
_Avoid_: Widget, stat, metric, dashboard tile, insight (alone)

### Agent

**Tool**:
One typed, read-only question the agent may ask the library, such as counting Events of a Kind in a date range or listing the top Entities. Tools are the only way the agent reaches data.
_Avoid_: Function, action, skill, capability, query (for the agent-facing thing)

**Tool trace**:
The ordered list of Tool calls and their outputs that produced one answer, shown to the user alongside the answer. Every number in an answer must appear in its Tool trace.
_Avoid_: Log, reasoning, chain of thought, steps

**Payload inspector**:
The screen that shows the exact text that would leave the device before any cloud model call, and lets the user refuse. Only Tool outputs ever appear in it; never Export records.
_Avoid_: Preview, consent dialog, privacy prompt

**Explorer**:
The screen that shows any Export file as its raw records, including files no Kind was assigned to. The fallback for anything the Import did not understand.
_Avoid_: JSON viewer, raw view, debug view, file browser

### Data rights

**Article item**:
One of the nine things GDPR Article 15 says a controller must provide on request: purposes, categories of data, recipients, retention period, rights to rectify and erase, right to complain, source of the data, automated decision-making and profiling, and a copy of the data itself.
_Avoid_: Requirement, criterion, check, rule

**Evidence rule**:
A deterministic statement, declared by a Source, of what in an Export counts toward one Article item: the presence of a named file, or the presence of Claims or Events of a given Kind.
_Avoid_: Heuristic, matcher, check

**Verdict**:
The outcome of applying the Evidence rules for one Article item to one Import: Covered, Partial or Missing, together with the Provenance of whatever evidence was found.
_Avoid_: Status, score, result, grade

**Gap report**:
The nine Verdicts for one Import, computed with no model involved. It states what the Source did and did not provide; it is not legal advice.
_Avoid_: Compliance report, audit, scorecard, GDPR check

**Access request**:
The follow-up letter drafted from a Gap report, asking the Source for each Missing or Partial Article item by its article letter.
_Avoid_: Complaint, letter (alone), DSAR, follow-up
