---
status: accepted
date: 2026-11-26
---

# The provider choice is a preference, the Gemini key lives in the platform's secure storage, and the Payload inspector gates the executor

The Ask agent gains the cloud model ADR 0006 allows: Google Gemini Flash on its free tier, through Koog's Google client,
offered on every platform once the user pastes a key. Three decisions shape how.

The user's choice among local, cloud and off is a plain preference (`PreferenceStore`, bound per app: the JDK's
per-user preferences, SharedPreferences, NSUserDefaults). The key is a secret and goes through `SecureKeyStore`, bound
per app to what the platform protects: the macOS Keychain through the `security` tool, an owner-only file in the app's
data directory on a desktop without one, the Android Keystore sealing the key with AES-GCM into a SharedPreferences
file of its own, and the iOS Keychain through the Security framework. `AgentSettings` in `core:agent` owns the rules
that join them: a platform with no local model offers cloud and off only and starts off; saving a key turns the cloud
on; removing it falls back to local where there is one, else off. Settings reads whether a key is saved and where it
is kept, and never reads the key back onto the screen.

The provider the agent is given is `SwitchingModelProvider`, which reads the choice at every connection and goes to
the platform's `LocalModelProvider`, to `GeminiModelProvider`, or answers `Off`. The Gemini provider reads the key at
every connection and probes the cloud by listing its models, so a missing, unreadable or rejected key and a spent
quota are typed reasons (`NoKey`, `KeyUnreadable`, `KeyRejected`, `QuotaExceeded`, `CloudUnreachable`) before a
question is sent, and the same reading of the HTTP status turns a refusal mid-question into the agent being
unavailable rather than a broken answer.

The Payload inspector is a gate on the prompt executor, not on the HTTP client: for a cloud model the agent wraps the
executor so that the first request of a question is rendered (`CloudPayload`: the model, every message in full, every
Tool with its parameters) and handed to a `PayloadInspector` the Ask screen implements by showing it and waiting; a
refusal throws out of the executor and the question ends with `PayloadRefused` and nothing sent, an approval covers
the rest of that question. The rendering is what the agent hands the client, so it is exact up to protocol framing,
and it is the same object a test serialises over the fixture to prove no Record field or Export path is in any request
of a question.

## Considered options

- One store for both the choice and the key: the choice would sit in the Keychain, prompting on every read on a Mac,
  or the key would sit in a plain preferences file. Two stores keep each where it belongs.
- Capturing the HTTP body in a Ktor interceptor for the inspector: exact to the byte, but the gate would then sit
  inside the client, differ per platform's engine, and be untestable without a mock engine; the executor is the one
  seam every model shares and the scripted model already drives it.
- Asking the inspector once per app run: the first request of the first question holds no Tool output, so the user
  would never see what the later ones carry. Once per question shows the request that matters and keeps the choice
  near the question.
- Validating the key only when a question fails: the Ask screen would say the agent is ready and then fail on the
  first question. One `models` call per check is the price of saying why before.
- Jetpack Security's `EncryptedSharedPreferences` on Android: deprecated, and the Keystore-sealed value is forty
  lines without it.

## Consequences

- Koog's Google client is published one step behind the rest of Koog, as `1.3.0-beta` built on the `1.3.0` core; the
  version catalog pins it apart. Each target adds the Ktor engine its platform uses in `core:agent`, and the Koog
  Ktor factory is passed explicitly so no classpath lookup is needed on iOS.
- Koog's Jackson bridge, a JVM-only extra, is excluded from the Android app: it will not dex at minSdk 24 and nothing
  on Android calls it.
- The key crosses to the macOS `security` tool as an argument, so it is briefly visible to other processes of the
  same user while the tool runs; the Keychain protects it at rest.
- `ModelInfo.locality` marks every answer `local` or `cloud` on the Ask screen, and the model line under the question
  says what leaves the device for each.
- The acceptance criterion with a live Gemini key is not exercised in CI; the payload boundary, the gate, the refusal
  and every typed reason are tested against scripted models and fakes.
