# Unpacked

A privacy-first app that turns the personal-data archives people download from large platforms into a searchable local timeline, answers questions about that data with provenance, and reports what the archive leaves out compared with GDPR Article 15.

## Language

**Source**:
A platform whose personal-data archive Unpacked can read. Instagram is the first Source; Google Takeout is the planned second, already named in the model so that a Match has a second Source to pair across, but with no adapter yet, so nothing imports it and the Welcome screen does not offer it.
_Avoid_: Platform, provider, integration

**Export**:
The archive a Source hands to a user under their right of access, as downloaded, before Unpacked touches it.
_Avoid_: Dump, download, takeout, archive (as a generic noun)

**Sample Export**:
The synthetic Instagram Export checked into the repository at `sample/instagram-sample-export.zip` for anyone to try the app with: the generator's full output (ads, every timed file, every untimed file, the message threads with their media, one file no mapper knows), with nobody's data in it. Written by `./gradlew writeSampleExport` from the same generator the tests use, the same files whenever the generator is the same, and proven on every test run to hold exactly what the generator writes and to import with no Failed file. The twenty-question eval asks its questions over this same Export.
_Avoid_: Demo data, test export, example archive, fixture (for the zip)

**Import**:
One run of reading one Export into the user's local library. The unit the user deletes: removing an Import removes every Event, Claim and Entity it produced. An Import remembers where its Export was when it was read, the zip or the folder by absolute path (on Android, the picked document's URI written as a path ending in the zip's name), so the Explorer can open the Export's files again; nothing keeps the Export there, and when it has moved the user points at it again and the Import remembers the new place. On Android a running Import is hosted in a foreground service with a progress notification, so leaving the app does not stop it. On iOS the Export is picked with the system document picker and read in place under the scope that pick grants, which lasts until the app is relaunched; the Import runs while the app is in the foreground.
_Avoid_: Sync, load, ingestion (as the noun for the result), session

**Export owner**:
The person the Export is about, as the Source names them in the Export's own profile file. Read once before any file is parsed; it is what tells a message sent from one received.
_Avoid_: User (for this), account holder, me, self

**Library**:
The user's local store of every Import, held in one on-device database and read only through repository interfaces. The Library is what cards, the agent's Tools and the gap report query; only the Explorer goes back to the Export after its Import, reading one file at a time on demand and storing nothing of what it reads.
_Avoid_: Database, store, cache, local data (as the noun for the thing)

### Canonical model

**Event**:
Something that happened at a known moment, recorded by a Source: an ad shown, a post liked, a login, a follow. Every Event has a time.
_Avoid_: Activity, record, row, log entry, item

**Claim**:
A standing assertion a Source makes about the user with no moment of occurrence: an inferred interest, an advertiser holding the user's contact details, a location of interest. A Claim may carry an "as of" time saying when the Source last updated it; that does not make it an Event.
_Avoid_: Fact, inference, attribute, untimed event, metadata

**Entity**:
A named thing that recurs across Events and Claims and is worth counting or following: an advertiser, an account, a device, a network, a topic, a place, a message thread. An Entity belongs to exactly one Source; it is what that Source said, nothing more.
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

**Record**:
One entry of an Export file as the Source wrote it: the label and value pairs an Event or Claim was read from. A Record is what the user sees on clicking through, together with its Provenance; it is never a synonym for the Event or Claim itself.
_Avoid_: Row, entry, raw event, JSON object

**Timeline**:
The Home screen's list of the Library's Events, newest first, read a page at a time and narrowed to one Kind on request. Claims never appear in it, even when they carry an "as of" time; opening any entry shows its Record, its Raw kind and its Provenance, and every time reads in the device's time zone.
_Avoid_: Feed, history, activity log, event list

**Search**:
The screen that finds the Events and Claims whose Record values or Entity names contain every word the user types, each word matched from its start ("cour" finds "courier") without regard to case or accents. Hits are listed newest first by an Event's time or a Claim's "as of" time, undated Claims last, never by relevance (ADR 0004, 0010); each opens to the same Record detail as the Timeline, with its Provenance.
_Avoid_: Find, lookup, query (for the screen), filter (for this)

**Claims screen**:
The screen that shows what a Source believes about the user: one group per Claim Kind with its count, opened to list its Claims a page at a time, dated ones newest first by their "as of" time and undated ones after in file order. Each opens to the same Record detail as the Timeline.
_Avoid_: Beliefs, profile, inferences (for the screen), facts

**Entities screen**:
The screen that lists the Library's Entities with how many Events and Claims name each, narrowed to one Kind or ordered by name on request, every row and pane naming the Entity's Source because Entities are never merged across Sources (ADR 0002). Opening one shows its own timeline, newest first in pages, and its Claims, each opening to the Record detail.
_Avoid_: Contacts, people, directory, merged view

### Insights

**Insight card**:
One of a fixed set of eight deterministic summaries computed from the Library through its repository interfaces, with no model and no database of its own (`core:insights`): the advertisers holding the user's contact details; the ads shown; the content viewed per day with its busiest hour and weekday in the device's zone; the logins by device and network; the follows and unfollows by month; what the Source inferred (its location and interest Claims, with the places and topics named most); the activity beyond the app (links visited and off-platform settings); and the messages sent and received by month, as counts and nothing else (ADR 0003). A card that counts by month lists every month from its first to its last in the device's zone, quiet months included. Every number on a card opens what it was counted from: the Timeline narrowed to a Kind, the Claims screen on a group, or one Entity. Cards read Kinds only, never a Source's own labels (ADR 0001), and a card that needs a distinction no Kind makes gets a Kind, as the off-platform settings did. A card over a Library with nothing of its Kinds is empty, not an error. Cards are what the user sees when no agent is available.
_Avoid_: Widget, stat, metric, dashboard tile, insight (alone)

### Agent

**Tool**:
One typed, read-only question the agent may ask the Library, and the only way the agent reaches data. There are eight, plain Kotlin over the repository interfaces in `core:agent` with no model involved: listImports; countEvents by Kind, date range, Source and optionally spread per day or month; topEntities by Kind, count and range; entityTimeline for one Entity; searchText; findMatches by Entity Kind; gapReport for an Import; and chartSpec, which describes a chart from a count over time or a ranking, hands a chart back as it is, and refuses any other shape with a typed reason, and which the agent calls itself once the model has answered (see Chart). Inputs are what a model writes, names and `YYYY-MM-DD` days as text, each checked into a typed error rather than thrown; days are read in the device's zone. Every output carries the Provenance of the records it summarises, listed in full or the newest few of a count, and never a Record's label and value pairs or an Entity's attributes, because Tool outputs are all a cloud model may receive (ADR 0006).
_Avoid_: Function, action, skill, capability, query (for the agent-facing thing)

**Tool trace**:
The ordered list of Tool calls and their outputs that produced one answer, shown to the user alongside the answer: each call's input as the Tool read it and its output as the model saw it, a refused call included with its reason. Every number in an answer must appear in its Tool trace, a refusal's wording included.
_Avoid_: Log, reasoning, chain of thought, steps

**Chart**:
The line or bar chart drawn under an answer whose Tool outputs include a count over time or a ranking (spec story 36): a line over days or months from a countEvents series, bars across Entities from a topEntities ranking, both axes named. Once the model has answered, the agent's strategy calls chartSpec on the latest output of the most recently called Tool that has such a shape, unless the model charted one itself; that call joins the Tool trace as the last step, spends none of the Step cap, and every number drawn is the number the trace shows. A question whose outputs have no such shape gets no chart and no refusal.
_Avoid_: Graph, visualization, plot, diagram

**Ask screen**:
The screen where the user asks the agent a question in their own words and reads the answer with its citations, its Tool trace, its Chart when it has one, and the marker saying where the model ran ("local" for a model on this device). The agent behind it (`core:agent`, on Koog) classifies the question to choose the Tools it is offered, lets the model call them, composes the answer with citations, and validates it; a question that is still calling Tools at the step cap ends with a bounded-steps message and the trace so far. When no model answers, the screen says why and points to the Insight cards on Home and the Gap report, which carry the app without one.
_Avoid_: Chat, assistant, conversation, Q&A

**Citation**:
A marker such as `[3]` in an answer, naming one Provenance; clicking it opens the Record. Every Tool output marks the records it cites with such markers, numbered in the order a question's outputs first cite them, so the model only ever quotes a marker and never sees a record's location (the Gap report names the Export's files, as it does on its own screen), and the Ask screen resolves the marker back to the Provenance. A marker that names nothing is shown as plain text, not a link.
_Avoid_: Reference, footnote, source link, provenance (for the marker)

**Citation validator**:
The check every answer passes before it is shown: each number in the answer, read as written and compared canonically ("1,200" is 1200, "3.0" is 3, a date or time part by part), must appear in some Tool output of the question. An answer that fails is sent back to the model once with the numbers it could not find; one that fails again is shown flagged as unverified, with those numbers named, never hidden and never silently corrected.
_Avoid_: Fact check, grounding, hallucination filter

**Step cap**:
The most Tool calls one question may spend, eight today, counted over the whole question, retry included; the Chart's own call is the agent's, not the model's, and is not counted. At the cap the model gets its last results with no Tool on offer, so one that has what it needs still answers; one that asks for a Tool anyway is stopped, and the user sees that it was, with the calls it made.
_Avoid_: Iteration limit, timeout, budget

**Model provider**:
The binding (ADR 0008) that says which model answers and how to reach it, asked anew for every question so a model started or stopped since the last one, a key saved since, or a changed Settings choice is noticed. It follows the Provider choice: "local" is the platform's own model, Ollama on localhost on desktop, choosing the model named by `UNPACKED_OLLAMA_MODEL` when it is installed and calls Tools, else the first preferred family that does, and nothing on Android and iOS, which therefore do not offer the choice; "cloud" is Google Gemini Flash on its free tier with the Gemini key from secure storage, probed by listing the models Google serves to that key so a rejected key or a spent quota is known before anything is sent, the newest Flash model listed being the one used since Google retires them for new accounts; "off" answers nobody (ADR 0006, ADR 0012).
_Avoid_: Backend, LLM client, engine

**Payload inspector**:
The card on the Ask screen that shows the exact request about to leave the device for a cloud model and lets the user send it or refuse. It appears before the first request of every question asked of a cloud model and never for a local one; approval covers the rest of that question, whose later requests add only the Tool outputs the Tool trace shows afterwards, and a refusal ends the question with nothing sent. The request is rendered as readable JSON: the model, every message in full and in order, and every Tool offered with its parameters; the client adds protocol framing and nothing else. Only the question, the agent's instructions and Tool outputs can ever be in it, never Export records, which a test over the fixture proves (ADR 0006).
_Avoid_: Preview, consent dialog, privacy prompt

**Provider choice**:
What Settings lets the user pick for the agent: local, cloud or off. Kept as a plain preference, never beside the key. A platform with no local model offers cloud and off only and starts off; saving a Gemini key turns the cloud on, and removing it falls back to local where there is one, else off.
_Avoid_: Mode, backend setting, model setting

**Gemini key**:
The API key the user pastes to opt in to the cloud model (spec story 39), kept only in the platform's secure storage through one binding per app (ADR 0008, ADR 0012): the macOS Keychain, or a file only the user's account can read on another desktop; the Android Keystore sealing it into a SharedPreferences file of its own; the iOS Keychain. Settings says where it is kept, and whether one is saved, and never shows it again.
_Avoid_: Token, credential, API secret, password

**Settings**:
The screen with the Provider choice, the Gemini key and the Tested Exports, reached from Home and from the Ask screen when the agent is off.
_Avoid_: Preferences (for the screen), options, configuration

**Tested Export**:
One Export a Source's adapter was tested against: the Source, the format, the date it was downloaded and the choices made when requesting it. Each adapter declares its own list, the one place they are declared, and Settings shows them all so a user can judge whether their Export is likely to import (spec story 55).
_Avoid_: Supported format, compatibility, version

**Explorer**:
The screen that shows any Export file as its Records, including files no Kind was assigned to. The fallback for anything the Import did not understand: nothing in the Export is hidden. It lists every file of one Import's inventory, newest complete Import by default, each with its Raw kind, its result (Parsed with its counts, Skipped or Failed with the reason) and the media files counted and never opened. Opening a JSON file reads its Records from the Export now, through the Source's adapter with no Kind assigned, in the file's order; a file the Source cannot read in its dialects shows as plain JSON with why, a Failed file shows the reason and whatever was readable. When the Export is no longer where the Import remembers it, the screen says so and offers the platform's chooser to pick it again.
_Avoid_: JSON viewer, raw view, debug view, file browser

### Data rights

**Article item**:
One of the nine things GDPR Article 15 says a controller must provide on request: purposes, categories of data, recipients, retention period, rights to rectify and erase, right to complain, source of the data, automated decision-making and profiling, and a copy of the data itself.
_Avoid_: Requirement, criterion, check, rule

**Evidence rule**:
A deterministic statement, declared by a Source's adapter, of what in an Export counts toward one Article item and how far: the presence of files matching a name pattern, or the presence of Claims or Events of a given Kind, each establishing Covered or only Partial when found.
_Avoid_: Heuristic, matcher, check

**Verdict**:
The outcome of applying the Evidence rules for one Article item to one Import: the strongest Coverage its satisfied rules establish, Covered, Partial or Missing when none is, together with what each satisfied rule found: the matching files as the inventory lists them, or the count of records of the Kind and the Provenance of the first few, which open to Record detail.
_Avoid_: Status, score, result, grade

**Gap report**:
The nine Verdicts for one Import, computed with no model involved by one pure function in `core:insights` over the Import's file inventory and Kind counts, so the same Import always yields the same report and a Source that declares no rules yields nine Missing Verdicts. The screen lists the nine Article items in the article's order for the chosen Import, newest complete one by default, each with its Verdict, a sentence on what it asks for and its evidence, under the one line saying the report is a tool and not legal advice. It states what the Source did and did not provide.
_Avoid_: Compliance report, audit, scorecard, GDPR check

**Access request**:
The follow-up letter drafted from a Gap report, asking the Source for each Missing or Partial Article item by its article letter, in the article's order, with whether it was not provided or provided only in part; a report with every item Covered yields a short letter saying nothing is outstanding. A deterministic text template in `core:insights` filled from the Verdicts with no model involved (ADR 0006), so the same report always yields the same letter. Its screen opens from the Gap report over the chosen Import, shows the letter, copies it to the clipboard, and on a platform that binds a saver (ADR 0008; desktop's save dialog today) hands it out as a text file. The user adds their own name; the app never writes it.
_Avoid_: Complaint, letter (alone), DSAR, follow-up, export (for the letter: that word is the archive)
