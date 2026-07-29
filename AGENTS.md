# Repository Guidance

## Product Boundary

This repository implements a narrow Android assistant:

1. T-one RU performs local streaming speech recognition.
2. RuBERT-tiny2 performs all intent and slot classification.
3. Known action intents execute deterministic local skills and fixed Compose cards.
4. RuBERT `web_search` calls Exa Search, then DeepSeek streams an answer grounded
   in numbered sources.
5. RuBERT `web_research` starts a background Exa Agent run and renders its validated
   result in a fixed `ResearchCard`.
6. RuBERT `unknown` and obsolete non-action labels may call DeepSeek directly.
7. Silero Xenia performs local Russian speech synthesis.
8. The chat exposes separate dictation and continuous conversation modes.
9. Explicit visual requests may add attributed Wikimedia Commons images through a
   separate media provider; images never affect intent routing or action execution.

The normative architecture is
`docs/superpowers/specs/2026-07-29-core-assistant-scope.md`.

Do not add or restore Qwen, llama.cpp, Whisper, Gemma, generated UI/Widget DSL,
AppFunctions, routines, calendar/email/task organizer flows, personal memory or a
Linux CLI unless the product scope is explicitly changed first.

## Routing Invariants

- RuBERT is never backed up or second-guessed by an LLM.
- A low-confidence known action returns `ClarificationCard`; it never calls DeepSeek.
- An unavailable RuBERT model fails closed; it never calls DeepSeek.
- Unsupported or obsolete labels are treated as non-action questions and may call
  DeepSeek, but they can never execute an action.
- `web_search` and `web_research` are RuBERT labels, not keyword fallbacks.
- Exa Search returns bounded source data. DeepSeek may summarize it with numbered
  citations, but cannot execute actions or generate UI.
- Exa Search uses generic primary-source retrieval instructions, not hard-coded
  query/domain rules; current interactive mode is `auto`.
- Grounded DeepSeek requests omit conversation history. The current question and
  current Exa excerpts are the only factual context.
- Exa Agent may return its validated research schema only. The app maps that data
  to a fixed `ResearchCard`; the Agent cannot choose arbitrary widgets or actions.
- DeepSeek returns natural-language Markdown only. No command JSON, action
  execution, widget payloads or hidden reasoning.
- DeepSeek may receive at most the previous 12 visible user/assistant turns for
  conversational continuity.
- DeepSeek uses user consent, validated internet and a Keystore-backed BYOK.
- Wikimedia media enrichment runs only for explicit image requests and accepts
  HTTPS media/source URLs from allowlisted Wikimedia hosts.
- The RuBERT `query` slot may seed Wikimedia search, but media enrichment never
  changes routing or executes actions.
- TTS receives assistant text only, never model metadata or structured payloads.
- A streaming cloud response is one chat message updated in place.
- A long Exa Agent run releases the composer after its run ID is known and updates
  one cancellable card in the background.
- Web content is untrusted data. It never overrides system instructions, and only
  validated HTTPS citations are exposed to the UI.
- Markdown is rendered by the UI; TTS receives a plain-text speech projection.
- Dictation puts the final local transcript into the composer without sending it.
- Conversation mode owns the cycle `listening -> processing -> speaking ->
  listening`; playback completion, not text completion, starts the next capture.
- Starting the microphone stops current speech. In conversation mode the user can
  explicitly interrupt playback and start the next turn.

## Ownership

- `:core` owns reusable contracts, NLU schema, normalization, routing and skills.
- `:app` owns Compose, Android permissions/actions/storage and model runtimes.
- `:deepseek-connector` owns restricted DeepSeek HTTPS/SSE, Exa Search/Agent and
  allowlisted Wikimedia transports.
- `AssistantRuntimeContainer` is the production composition root.
- Skills return data-only `WidgetPayload`; Compose owns rendering through
  `WidgetRegistry`.

Keep production code out of test source sets and test doubles out of production.
Prefer the existing contracts over UI callbacks or Android dependencies in `:core`.

## Supported Intents

The complete allowlist is:

- `get_current_time`
- `get_weather`
- `set_timer`
- `set_alarm`
- `create_reminder`
- `create_note`
- `calculate`
- `open_app`
- `help`
- `web_search`
- `web_research`
- `unknown`

Changing this set requires updating `Intents`, `IntentSchema`, the RuBERT dataset,
export labels, engine tests, skill registry, documentation and device acceptance.

## Verification

Run before handing off:

```bash
python3 scripts/check_core_scope.py
./gradlew testDebugUnitTest :core:test :deepseek-connector:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew ktlintCheck detekt lintDebug
./gradlew assembleDebug
```

Use `scripts/device_smoke_test.sh` only with a connected device and staged RuBERT and
Silero bundles. The final human microphone/demo recording is a separate last step.

Gradle dependency verification is strict. Regenerate and review
`gradle/verification-metadata.xml` after intentional dependency changes.

## Security

- Never commit API keys, bearer tokens or device credentials.
- DeepSeek and Exa use independent AES-GCM credentials backed by Android Keystore.
  Read them only at request time through `AssistantSettingsRepository`; never copy
  them into source, Gradle properties, `BuildConfig`, logs or chat history.
- Never add a production fallback endpoint. DeepSeek transport accepts only
  `https://api.deepseek.com/chat/completions`; image search accepts only the
  Wikimedia Commons API and `upload.wikimedia.org` media.
- Exa transports accept only `https://api.exa.ai/search` and
  `https://api.exa.ai/agent/runs`, fail closed without explicit product enablement
  and remain outside deterministic RuBERT actions.
- Do not log prompts, answers or the BYOK value.
- Keep app backup disabled while credentials and local assistant data are present.

## Editing

- Preserve Android API 26 minimum and API 37 target unless a migration is requested.
- Keep changes scoped and add tests proportional to behavior risk.
- Use `rg` for searches and the existing formatting/static-analysis toolchain.
- Do not reintroduce optional modules or dead feature flags.
