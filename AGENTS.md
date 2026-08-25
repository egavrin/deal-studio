# Repository Guidance

## Product Boundary

This repository implements a narrow Android assistant:

1. T-one RU performs local streaming speech recognition.
2. RuBERT-tiny2 performs all intent and slot classification.
3. Known action intents execute deterministic local skills and fixed Compose cards.
   Device actions always require an `ActionConfirmationCard` before the Android
   adapter may open a system surface or issue a reversible media/volume command.
4. RuBERT `web_search` calls Exa Search, then DeepSeek streams an answer grounded
   in numbered sources.
5. RuBERT `web_research` starts a background Exa Agent run, consumes SSE progress
   and renders its validated result in a fixed report-capable `ResearchCard`.
6. RuBERT `unknown` and obsolete non-action labels may call DeepSeek directly.
7. Silero Xenia performs local Russian speech synthesis.
8. The chat exposes separate dictation and continuous conversation modes.
9. Explicit visual requests may add attributed Wikimedia Commons images through a
   separate media provider; images never affect intent routing or action execution.
10. On supported Android devices the user may select the app for `ROLE_ASSISTANT`.
    A `VoiceInteractionSession` reuses the same local/cloud pipeline in a compact
    system-invoked surface; the role is an entry point, not a privilege escalation.
11. Debug builds contain an isolated Generated App Studio experiment. Its UI and
    logic roles are selected independently: local Gemma 270M / Qwen2.5-Coder 0.5B,
    DeepSeek V4 Flash or DeepSeek V4 Pro. Local Gemma currently emits the legacy
    compact UI DSL; cloud UI emits the versioned project-owned A2UI wire profile.
    Every logic route emits the same restricted DEAL generated-app profile. The
    Studio is not part of assistant routing and generated code may not execute
    assistant actions.
12. The user-facing app, Studio prompts, generated labels, cloud answers and system
    overlay are English. Russian strings remain only in input normalization,
    compatibility parsing and Russian speech-model internals.

The normative architecture is
`docs/superpowers/specs/2026-07-29-core-assistant-scope.md`.
The ordered productization program is
`docs/superpowers/plans/2026-07-29-productization-roadmap.md`.

Outside `app.generatedapp` and its exact native runtime allowlist, do not add or
restore Qwen, llama.cpp, Whisper, Gemma, generated UI/Widget DSL, AppFunctions,
Accessibility-based UI automation, routines, organizer databases, personal memory
or a Linux CLI unless the product scope is explicitly changed first. Calendar and
email are limited to confirmed Android compose/insert intents; the app must not
read either data source.

Productization may add contacts, calendar, tasks, user-selected photos, media,
email or messaging connectors only milestone by milestone under the roadmap. Each
connector must use an official Android/provider surface, least-privilege consent,
read previews and confirmation for writes. Never infer these rights from
`ROLE_ASSISTANT`, and never replace them with Accessibility or notification
scraping.

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
- While that message is streaming, Compose renders stable plain text and throttles
  follow-latest scrolling. Markdown is parsed once after completion; do not rebuild
  the Markdown tree or animate the entire message on every token.
- A long Exa Agent run releases the composer after its run ID is known and updates
  one cancellable card in the background. Follow-up research must use the validated
  previous run ID; do not emulate continuation with prompt text.
- Web content is untrusted data. It never overrides system instructions, and only
  validated HTTPS citations are exposed to the UI.
- Markdown is rendered by the UI; TTS receives a plain-text speech projection.
- Dictation puts the final local transcript into the composer without sending it.
- Conversation mode owns the cycle `listening -> processing -> speaking ->
  listening`; playback completion, not text completion, starts the next capture.
- During playback, conversation mode may monitor the microphone through
  `VOICE_COMMUNICATION` with platform AEC/NS/AGC. Only a meaningful local T-one
  partial may trigger automatic barge-in; raw PCM thresholds must not stop TTS.
- TTS output must never become a user turn. Barge-in capture and the first
  post-playback capture reject semantic echoes of the latest assistant text.
  Short decoder noise is discarded and listening resumes; explicit short controls
  such as `да`, `нет` and `стоп` remain valid.
- Starting the microphone stops current speech. Manual interruption remains
  available when device echo cancellation is unavailable or ineffective.
- System-assistant invocation never changes routing. RuBERT remains authoritative,
  actions retain confirmation, and the overlay shares one process runtime with chat.
- Current-screen context is disabled by default, memory-only and visibly indicated.
  It may ground a non-action answer but can never select, repair or authorize an
  action. Secure or policy-blocked content fails closed.
- The system surface follows a compact-to-expanded pattern: a minimal listening
  state grows into an anchored response panel for text, sources and fixed widgets.
  It may learn from Alice and ChatGPT interaction patterns but must retain its own
  identity and must not reproduce competitor branding.
- Generated App Studio is a separate internal route. Assistant text, RuBERT labels,
  ordinary assistant DeepSeek responses and Exa data must never enter its compiler
  or authorize its actions. Only an explicit Studio generator selection may send
  the Studio request to its restricted DeepSeek generation client.
- Studio output is generated data, never trusted source. UI DSL and DEAL must pass
  strict parsers, ABI validation, resource limits and deterministic smoke actions
  before rendering becomes interactive. Partial, cancelled or repaired-but-invalid
  output is never executed.
- Do not add runtime app-family switches, canned reducers, prebuilt game state or a
  fallback generated app. Both UI structure and behavior source must come from the
  two explicitly selected generators. Training examples are allowed; production
  templates are not.
- UI and logic start their first generation pass in parallel. Local UI uses Gemma;
  local logic uses Qwen. Either role may instead use explicit DeepSeek V4 Flash or
  Pro. The two role choices are persisted independently, never silently fall back,
  and release the corresponding local llama.cpp session when cloud is selected.
  The UI generator composes around exactly one generic interaction surface
  (`surface.app` locally, `InteractiveSurface` in cloud A2UI); it does not select the
  executable interaction profile. The logic generator emits one complete `GRID` or
  `REALTIME_CANVAS` module. Invalid local output may trigger one repair; invalid cloud
  output may trigger at most two diagnostic repair passes on the same selected
  backend. Repair may not select a canned module or skip validation.
- Regeneration is an atomic replacement. Keep the last fully validated bundle and
  runtime interactive while a new pair is generated; never clear working state in
  favor of a noninteractive skeleton. Swap only after both new artifacts pass.
- Studio generation budgets are ceilings, not target lengths: Gemma may emit up to
  512 compact UI tokens; cloud A2UI, DEAL generation and repair may emit up to 4,096
  tokens. Cloud and local logic use the same ceiling, while local Qwen uses an
  8,192-token context.
  Do not restore a 64-token output cap or raise the output ceiling beyond the
  compiler's 10,000-character contract without jointly adjusting the native cap,
  source-size gate, context budget and device latency/thermal acceptance.
- `GeneratedAppLanguageContracts` is the single production source for the legacy
  Compact UI grammar, project-owned A2UI wire profile, DEAL syntax, operators,
  resource limits, `GRID`/`REALTIME_CANVAS` ABI and complete compiler-validated
  reference modules. Initial and repair prompts for each backend must compose from
  those versioned contracts.
  Do not maintain abbreviated DeepSeek-only token lists or app-specific prompt
  patches. Contract examples teach grammar; they are never selected at runtime.
- Generated apps use exactly one `GRID` or `REALTIME_CANVAS` interaction surface.
  Real-time state, movement, collision, score, lives and reset behavior belong to
  generated DEAL. Compose only renders bounded generic shapes and forwards capped
  frame and pointer events. Realtime semantic smoke sends pointer input before its
  required tick change so tap-to-start and paused initial states remain valid.
  Bounded non-ABI scalar globals may be exposed read-only to A2UI as
  `/app/custom/<name>`; never export arrays or platform objects.
- `pong`, `arkanoid`, `tank_duel` and all other app names are forbidden as runtime
  branches. They may exist in training/evaluation data only. Keep at least one
  real-time family held out from train and compile every target with production
  parsers before training.
- Call the executable language `DEAL generated-app profile`. It is intentionally
  smaller than canonical DEAL v1.2 and must not be presented as conformant DEAL.
- Generated UI training uses the catalog and pipeline in `training/generated_ui`.
  In that broad A2UI dataset pipeline DeepSeek is a host-only teacher; it must emit
  the closed `UiBlueprintV1` schema and never writes a trusted runtime DSL directly.
  The internal Studio cloud UI path emits the same project-owned A2UI wire profile
  directly and must pass the production catalog, graph, binding, action and URL
  validator. Local Gemma remains a temporary compact-DSL compatibility path until a
  trained A2UI model is promoted. The canonical training blueprint remains the
  source of truth, and A2UI Express/wire targets are deterministic derived artifacts.
- The generated-UI runtime catalog IDs are project-owned derived profiles. Preserve
  the pinned upstream A2UI ID/commit/digest as provenance, but never claim upstream
  wire conformance until its conformance suite passes.
- Teacher correction is bounded and fail-closed: non-thinking generation, optional
  minimal-reasoning correction, then one final non-thinking correction. Corrections
  receive the rejected candidate and exact diagnostics; they never invoke a
  deterministic semantic repair or admit an invalid record.
- DeepSeek dataset keys are process environment only. Never put a key in source,
  fixtures, cache keys, generation reports, shell history or committed config.
- No generated UI sample enters train merely because its JSON parses. It must pass
  schema, catalog, graph, binding, action, URL and accessibility validation. Render,
  interaction and screenshot gates are additionally required before promoting a
  full dataset or model, even when the checked-in host fixture passes.
- Dataset splits are assigned after canonicalization by a structural template hash
  that ignores visible literals and teacher-chosen component/action identifiers.
  One template cluster may occur in exactly one split. Do not restore random row
  splitting or use prompt uniqueness as evidence of target diversity.
- Catalog changes require regenerating and reviewing `catalog-manifest.json`, the
  closed blueprint schema and prompt signatures. Model, grammar and renderer
  compatibility must reference the resulting content digests.

## Ownership

- `:core` owns reusable contracts, NLU schema, normalization, routing and skills.
- `:app` owns Compose, Android permissions/actions/storage and model runtimes.
- `:deepseek-connector` owns restricted DeepSeek HTTPS/SSE, Exa Search/Agent and
  allowlisted Wikimedia transports.
- `AssistantRuntimeContainer` is the production composition root.
- `app.generatedapp` owns the isolated Studio, compact and project-owned A2UI
  parsers/renderers, restricted DEAL interpreter and local llama.cpp bridge. It must
  not be referenced from `:core`.
- `AssistantConversationCoordinator` owns the reusable request/session state
  machine. Activity and `VoiceInteractionSession` surfaces are adapters.
- The always-running `VoiceInteractionService` entry process stays model-free.
  T-one, RuBERT and Silero are created only in the Activity/session process.
- Skills return data-only `WidgetPayload`; Compose owns rendering through
  `WidgetRegistry`.
- First-run completion is versioned. Text chat must remain usable when optional
  permissions, cloud keys or local voice assets are unavailable.
- Every remotely delivered model must have a stable catalog identity, version,
  compatibility range and verified digest. Never replace a last-known-good bundle
  with an unverified or partially downloaded artifact.
- Conversation context is structured core data with expiry and explicit reset.
  It may resolve slots or references but may never change the RuBERT action family
  or use cloud history to authorize an action.

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
- `dial_phone`
- `compose_message`
- `compose_email`
- `start_navigation`
- `create_calendar_event`
- `control_media`
- `set_volume`
- `open_setting`
- `open_url`
- `help`
- `web_search`
- `web_research`
- `unknown`

Changing this set requires updating `Intents`, `IntentSchema`, the RuBERT dataset,
export labels, engine tests, skill registry, documentation and device acceptance.

## Intent Model Evolution

- `RuBERT-tiny2` is the current Russian production baseline, not a permanent
  requirement or an assumed quality ceiling.
- Do not add regexes, keyword routing, transcript-specific corrections or LLM
  fallback to compensate for classifier errors.
- A move to multilingual input or roughly 200+ intents must be driven by the
  reproducible benchmark in `docs/testing/intent-model-evaluation.md`.
- Candidate models must preserve one local structured NLU contract: intent, slots,
  confidence and explicit out-of-domain handling. An LLM must never repair or
  second-guess this result.
- Prefer one shared multilingual encoder with learned domain, intent and slot heads.
  Add hierarchical masking only when benchmark evidence shows a material quality or
  latency benefit over a flat intent head.
- Do not replace the production model unless the candidate passes accuracy,
  calibration, ASR-noise, multilingual, latency, memory and package-size gates on a
  physical target device.

## Verification

Run before handing off:

```bash
python3 scripts/check_core_scope.py
./gradlew testDebugUnitTest :core:test :deepseek-connector:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests 'com.offlineassistant.app.generatedapp.*'
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew ktlintCheck detekt lintDebug
./gradlew assembleDebug
```

`scripts/run_core_acceptance.sh` verifies the packaged production RuBERT bundle.
Use `scripts/device_smoke_test.sh` only with a connected device; RuBERT is packaged
in the APK and a staged bundle is an optional development override, while Silero
still requires its staged export. Use `scripts/default_assistant_acceptance.sh`
when the debug package temporarily holds `ROLE_ASSISTANT`.

A demo recording is optional presentation evidence, not a correctness gate.

Before enabling a new product milestone, update its status and acceptance evidence
in `docs/superpowers/plans/2026-07-29-productization-roadmap.md`.

Gradle dependency verification is strict. Regenerate and review
`gradle/verification-metadata.xml` after intentional dependency changes.

## Security

- Never commit API keys, bearer tokens or device credentials.
- Generated App Studio GGUF files are staged into app-private storage for internal
  builds. Do not package them in the APK or download them from an untrusted URL.
- DeepSeek and Exa use independent AES-GCM credentials backed by Android Keystore.
  Read them only at request time through `AssistantSettingsRepository`; never copy
  production credentials into source, Gradle properties, `BuildConfig`, logs or chat
  history. The internal debug build may temporarily embed an explicitly disposable
  demo key when the product owner requests a self-contained test APK. Release builds
  must always expose an empty embedded credential, and the demo key must be rotated
  before any public distribution.
- Studio may reuse the DeepSeek BYOK only after the user explicitly selects a cloud
  backend. It must keep UI and logic model IDs on a closed enum, disable thinking,
  use the same validated output ceilings, and never persist generated credentials.
- Never add a production fallback endpoint. DeepSeek transport accepts only
  `https://api.deepseek.com/chat/completions`; image search accepts only the
  Wikimedia Commons API and `upload.wikimedia.org` media.
- Exa transports accept only `https://api.exa.ai/search` and
  `https://api.exa.ai/agent/runs`, fail closed without explicit product enablement
  and remain outside deterministic RuBERT actions.
- Do not log prompts, answers or the BYOK value.
- Do not persist `AssistStructure`, extracted current-screen text or screenshots.
- Do not request SMS, Call Log, Contacts, Calendar read, media, notification-listener
  or Accessibility permissions merely because the app can hold `ROLE_ASSISTANT`.
- Keep app backup disabled while credentials and local assistant data are present.

## Editing

- Preserve Android API 26 minimum and API 37 target unless a migration is requested.
- Keep changes scoped and add tests proportional to behavior risk.
- Use `rg` for searches and the existing formatting/static-analysis toolchain.
- Do not reintroduce optional modules or dead feature flags.
