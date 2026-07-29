# Productization Roadmap

**Status:** active
**Date:** 2026-07-29
**Depends on:** `docs/superpowers/specs/2026-07-29-core-assistant-scope.md`

## Product Objective

Turn the working local-first assistant vertical into a product candidate without
weakening its routing or privacy guarantees. The product must remain useful when
cloud services are disabled, make every data boundary visible and recover from
missing models, permissions, network and interrupted work without reinstalling.

The work is intentionally ordered. First-run, model delivery and observability are
foundations for every later capability. New personal-data connectors are added only
after their permission and confirmation contracts exist.

## Current Implementation Status

Completed in the first productization increment:

- versioned, resumable onboarding and recovery entry from Settings;
- typed model catalog/inventory with role, version, delivery and disk use;
- immutable 48-case/21-intent Russian NLU regression manifest and CI check;
- core conversation-context safety contract with TTL and action-family protection;
- 30-minute/20-query Exa source cache and visible structural citation status;
- production-or-unsigned release signing, minified AAB CI and release contract
  checks.

Still requires subsequent increments:

- signed remote model catalog, downloads, cancellation, verification and rollback;
- real-ASR/accent/OOD/calibration partitions and multilingual model comparison;
- a learned new-command/continuation signal wired to the context contract;
- contacts/calendar/tasks/photo/media/email/message connectors and permission UI;
- semantic claim-to-source verification, contradiction handling and editable
  persisted research reports;
- complete chat/overlay visual convergence and accessibility screenshot matrix;
- protected signing, migration fixtures, OEM/device soak and staged rollout.

## Non-Negotiable Architecture

```text
voice -> local ASR ┐
typed text --------+-> local structured NLU -> deterministic action + fixed UI
                   |
                   +-> explicit search/research route -> Exa -> DeepSeek -> cited answer
                   |
                   +-> open-ended route -> DeepSeek -> streamed Markdown

assistant text -> local TTS
```

- The local NLU result is authoritative for actions.
- An LLM never repairs an intent, manufactures an action or bypasses confirmation.
- Personal sources use official Android APIs, scoped permissions and visible user
  consent. `ROLE_ASSISTANT` is an entry point, not a privilege escalation.
- API keys remain Keystore-backed BYOK and are never put in source or build output.
- Model artifacts have an explicit identity, version, digest and compatibility
  contract before remote delivery is enabled.

## Milestone 1: Trustworthy First Run

Deliver:

- a resumable first-run flow explaining on-device and cloud processing;
- local model readiness and storage summary before voice setup;
- microphone permission requested in context, not on app launch;
- optional DeepSeek/Exa setup and optional default-assistant selection;
- a final readiness summary with recoverable failures;
- a Settings action to run setup again without deleting user data.

Acceptance:

- a user can reach text chat without granting microphone or configuring cloud;
- voice is never shown as ready when ASR or TTS assets are missing;
- denying a permission leaves an actionable recovery path;
- onboarding completion is versioned so later mandatory migrations can re-open only
  the required step.

## Milestone 2: Model Lifecycle

Deliver:

- a model catalog with stable IDs, role, version, expected files, bytes, digest,
  minimum app/runtime version and required/optional status;
- install state: bundled, missing, downloading, verifying, ready, incompatible or
  failed;
- download progress, cancellation, atomic verification and retry;
- storage totals and per-model removal for optional models;
- signed catalog and HTTPS artifact hosting before production remote delivery;
- rollback to the last verified compatible version.

Acceptance:

- an interrupted install never replaces the last good model;
- a digest mismatch fails closed and deletes the untrusted temporary artifact;
- the app remains usable in text/cloud mode while optional local voice models are
  unavailable;
- app update and model update are independently reversible.

The current build still packages T-one and RuBERT and stages Silero during
development. Milestone 2 starts by exposing catalog/state/storage contracts; remote
download remains disabled until a trusted artifact host and signed manifest exist.

## Milestone 3: NLU Quality And Scale

Deliver:

- immutable clean, real-ASR, synthetic-ASR, ambiguity and OOD evaluation splits;
- per-intent and per-slot metrics, calibration curves and false-action reporting;
- frozen production thresholds generated from calibration data;
- at least 100 real device transcripts retained only with explicit test consent and
  anonymized before entering the repository;
- a benchmark of RuBERT-tiny2, multilingual DistilBERT, mmBERT-small and an XLM-R
  quality ceiling;
- one multilingual encoder for Russian and English before expanding toward 200
  intents.

Acceptance:

- every promoted bundle passes ONNX parity and immutable host evaluation;
- device gates include warm p50/p95, cold load, peak RSS, 50 mixed calls and thermal
  stability;
- no regex, keyword or transcript-specific fallback is introduced to hide model
  errors;
- confidence is calibrated and OOD is explicit.

The detailed protocol remains
`docs/testing/intent-model-evaluation.md`.

## Milestone 4: Contextual Dialogue

Deliver:

- a core-owned `ConversationContext` with active command, referents, pending slots,
  expiry and explicit reset;
- deterministic continuation for missing slots and references such as “move it one
  hour later”;
- classification of new command versus continuation;
- a visible control to clear conversation context independently of chat history;
- tests for stale context, conflicting references and cloud/action boundary.

Acceptance:

- context may complete slots but cannot change an already classified action family;
- ambiguous references ask for clarification;
- expired or cleared context cannot execute an action;
- cloud conversation history never authorizes a local action.

## Milestone 5: Official Phone Connectors

Implement connectors in this order:

1. contacts lookup for dial/message composition;
2. calendar read plus confirmed create/update;
3. tasks/reminders through a product-owned provider or documented external API;
4. user-selected photos through Photo Picker;
5. media session control and user-selected playback provider;
6. email search/compose only through a provider API with explicit account consent;
7. messages only through official role/API surfaces available to the installed
   distribution.

Each connector requires:

- a separate capability contract in `:core`;
- Android implementation in `:app`;
- least-privilege permission rationale and denial state;
- read preview before any write;
- system confirmation for external side effects;
- audit-safe metadata without storing personal content in logs.

Do not add Accessibility automation, notification scraping or hidden account access.

## Milestone 6: Search And Research Quality

Deliver:

- claim-to-source coverage validation before showing a grounded badge;
- clear treatment of unsupported claims and source disagreement;
- stable source cards with image, publisher, date and cached preview;
- query/result cache with TTL and manual refresh;
- editable research report with persisted validated intermediate results;
- related questions that reuse the existing source set when possible;
- offline reopening of previously completed reports.

Acceptance:

- every numbered citation resolves to the source supporting the nearby claim;
- contradictory sources are shown, not silently collapsed;
- cached results are visibly dated;
- prompt injection in retrieved content cannot alter routing, actions or system
  instructions.

## Milestone 7: Unified Product UI

Deliver:

- one visual system for chat, system overlay, onboarding and Settings;
- compact message and result-card hierarchy matching the design references;
- stable streaming without whole-screen recomposition or flashing;
- explicit empty, loading, permission, offline, model-missing and retry states;
- keyboard/inset behavior on phone, foldable and tablet sizes;
- TalkBack labels, 200% font scale, contrast and reduced-motion support;
- screenshot tests for all fixed cards and critical state combinations.

Acceptance:

- composer remains attached above IME/navigation in every supported window mode;
- no content is hidden by cutouts, hinges or system bars;
- streaming updates one message in place and preserves reading position;
- screenshots pass the agreed phone/foldable matrix.

## Milestone 8: Release Engineering

Deliver:

- release signing supplied only by protected CI secrets;
- debug and release-like APK/AAB builds in CI;
- migration tests from every shipped data/schema version;
- baseline-profile verification and startup/frame benchmarks;
- 50 and 100 mixed-session soak scripts;
- battery, thermal, PSS and disk budgets on the reference device;
- OEM matrix covering Pixel/AOSP, OPPO/ColorOS and one Samsung device;
- staged rollout, crash/ANR monitoring and rollback instructions;
- update tests proving keys, settings and local data survive.

Release gates:

| Metric | Initial gate |
| --- | --- |
| crash-free mixed sessions | 100/100 |
| action false-positive rate | frozen by NLU benchmark manifest |
| warm intent latency | p95 within current physical-device baseline |
| chat frame health | no visible flashing; benchmark regression blocked |
| data migration | all historical fixtures pass |
| model integrity | catalog identity and digest verified |
| secrets | no credentials in APK, logs, artifacts or repository |

## Delivery Order

1. first run, catalog/state/storage and recovery UI;
2. NLU eval/calibration CI and context contracts;
3. contacts/calendar/photo-picker connectors;
4. search grounding/cache/report improvements;
5. visual convergence and accessibility matrix;
6. release-like CI, migration fixtures and physical-device soak.

Device-only acceptance is collected after host checks pass. A demo video is not a
correctness requirement.
