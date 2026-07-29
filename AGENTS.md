# Repository Guidance

## Product Boundary

This repository implements a narrow Android assistant:

1. T-one RU performs local streaming speech recognition.
2. RuBERT-tiny2 performs all intent and slot classification.
3. Known action intents execute deterministic local skills and fixed Compose cards.
4. Only RuBERT `unknown` may call DeepSeek for a streaming plain-text answer.
5. Silero Xenia performs local Russian speech synthesis.

The normative architecture is
`docs/superpowers/specs/2026-07-29-core-assistant-scope.md`.

Do not add or restore Qwen, llama.cpp, Whisper, Gemma, generated UI/Widget DSL,
AppFunctions, routines, calendar/email/task organizer flows, personal memory or a
Linux CLI unless the product scope is explicitly changed first.

## Routing Invariants

- RuBERT is never backed up or second-guessed by an LLM.
- A low-confidence known action returns `ClarificationCard`; it never calls DeepSeek.
- An unavailable RuBERT model fails closed; it never calls DeepSeek.
- Unsupported or obsolete labels fail closed.
- DeepSeek returns natural-language text only. No command JSON, action execution,
  widget payloads or hidden reasoning.
- DeepSeek uses user consent, validated internet and a Keystore-backed BYOK.
- TTS receives assistant text only, never model metadata or structured payloads.
- A streaming cloud response is one chat message updated in place.

## Ownership

- `:core` owns reusable contracts, NLU schema, normalization, routing and skills.
- `:app` owns Compose, Android permissions/actions/storage and model runtimes.
- `:deepseek-connector` owns the restricted HTTPS/SSE transport.
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
- Never add a production fallback endpoint. DeepSeek transport accepts only
  `https://api.deepseek.com/chat/completions`.
- Do not log prompts, answers or the BYOK value.
- Keep app backup disabled while credentials and local assistant data are present.

## Editing

- Preserve Android API 26 minimum and API 37 target unless a migration is requested.
- Keep changes scoped and add tests proportional to behavior risk.
- Use `rg` for searches and the existing formatting/static-analysis toolchain.
- Do not reintroduce optional modules or dead feature flags.
