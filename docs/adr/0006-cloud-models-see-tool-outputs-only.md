---
status: accepted
date: 2026-10-24
---

# A cloud model only ever receives Tool outputs, shown first in the Payload inspector

The agent runs on Ollama locally by default (desktop only) and on Google Gemini Flash's free tier as an opt-in on every platform. Google states that free-tier content may be used to improve its products, so the app never sends Export records, Event or Claim rows, or message content to a cloud model: the prompt may contain only the user's question and the aggregated outputs of read-only Tools, and the user sees that exact payload in the Payload inspector before the first cloud call. No OpenAI provider is included; Mistral is not in scope. The agent does only question answering; the Access request letter is a deterministic template filled from the Gap report.

## Consequences

- A test asserts that the serialized cloud request contains no field from an Export record.
- Without Ollama (iOS, Android, or desktop with Ollama stopped) and without a Gemini key, the Ask screen explains why the agent is off and the Insight cards and Gap report carry the app.
- The mapping assistant and model-written letters are not planned; either may return in Week 7 if the tool-use eval scores well.
