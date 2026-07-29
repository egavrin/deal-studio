# Core Offline Assistant Scope

**Status:** normative
**Date:** 2026-07-29
**Platform:** Android, ARM64, `minSdk 26`, `targetSdk 37`

## Goal

Deliver a stable vertical assistant demo around these capabilities:

1. local speech recognition;
2. local intent and slot classification;
3. a small deterministic action allowlist;
4. cloud answers for genuinely open-ended requests;
5. local speech synthesis.
6. editable voice dictation and continuous voice conversation;
7. attributed image results for explicit visual requests.
8. grounded current-information answers through Exa Search;
9. explicit background research through Exa Agent.
10. confirmed Android compose/navigation/settings/media actions;
11. inline citations, source previews and related search questions;
12. automatic foreground conversation barge-in with echo cancellation.
13. optional Android default-assistant invocation through a compact
    `VoiceInteractionSession` that reuses the same pipeline.

The product is intentionally not a general personal operator or generated-UI
platform.

The default-assistant role is a product entry point. It does not grant AppFunctions,
arbitrary application control or permission to read personal data.

## Runtime Flow

```text
AudioRecord
  -> T-one RU / sherpa-onnx (streaming transcript)
  -> RuBERT-tiny2 / ONNX Runtime (intent + slots)

Typed text
  -> RuBERT-tiny2 / ONNX Runtime (intent + slots)

Known action, confidence >= threshold
  -> deterministic normalization and validation
  -> local Skill
  -> AssistantResponse(text, fixed WidgetPayload)

Known action, confidence < threshold
  -> ClarificationCard

web_search
  -> Exa Search auto / primary-source-biased bounded highlights
  -> DeepSeek HTTPS/SSE grounded in numbered sources
  -> one streaming Markdown message + source cards

web_research
  -> Exa Agent asynchronous run / low effort + SSE events
  -> fixed validated summary + findings schema
  -> one background ResearchCard + report + follow-up through previousRunId

unknown or obsolete label outside the local action registry
  -> DeepSeek HTTPS/SSE
  -> bounded visible conversation history
  -> one streaming Markdown message
  -> GenericAnswerCard source marker

Explicit visual request
  -> Wikimedia Commons search after text generation
  -> RuBERT `query` slot used as the preferred media-search topic
  -> allowlisted HTTPS image attachments with source and license

Any assistant text
  -> Silero Xenia / ONNX Runtime
  -> chunked local playback
```

## Routing Contract

| RuBERT result | Route | Cloud allowed |
| --- | --- | --- |
| supported action, high confidence | local normalizer + skill | no |
| supported action, low confidence | clarification | no |
| unsupported historical label | DeepSeek Markdown answer | yes, with consent + BYOK + network |
| model unavailable | explicit error | no |
| `web_search` | Exa Search + grounded DeepSeek answer | yes, with consent + both BYOK keys + network |
| `web_research` | asynchronous Exa Agent + fixed ResearchCard | yes, with consent + Exa BYOK + network |
| `unknown` | DeepSeek Markdown answer | yes, with consent + BYOK + network |

DeepSeek is not a fallback parser. It cannot repair RuBERT, emit command JSON,
execute actions or generate widgets.
Exa Search is retrieval only. Exa Agent emits one bounded research schema which the
app maps to a fixed card; it cannot choose actions or arbitrary UI. Neither web route
is implemented with keyword matching.

## Intent Allowlist

| Intent | Result |
| --- | --- |
| `get_current_time` | text |
| `get_weather` | `weather_card` using mock/cache data |
| `set_timer` | `timer_card` |
| `set_alarm` | `alarm_card` plus Android alarm delegation |
| `create_reminder` | `reminder_card` plus local notification |
| `create_note` | `note_card` |
| `calculate` | `calculator_card` |
| `open_app` | `open_app_card` |
| `dial_phone` | confirmed system dialer |
| `compose_message` | confirmed SMS composer |
| `compose_email` | confirmed email composer |
| `start_navigation` | confirmed system map route |
| `create_calendar_event` | confirmed calendar insert surface |
| `control_media` | confirmed media key |
| `set_volume` | confirmed reversible stream-volume command |
| `open_setting` | confirmed allowlisted system settings surface |
| `open_url` | confirmed validated HTTPS browser intent |
| `help` | `help_card` |
| `web_search` | DeepSeek Markdown + numbered source cards |
| `web_research` | `research_card` + numbered source cards |
| `unknown` | DeepSeek Markdown + `generic_answer_card` source |

Infrastructure cards are `clarification_card`, `permission_card`, `error_card`,
`research_card` and `action_confirmation_card`.
The registry contains no other widget types.

## Model Responsibilities

### T-one RU

- streaming Russian ASR only;
- receives 16 kHz microphone PCM and feeds sherpa-onnx;
- emits partial transcript updates and endpoint events;
- context stays resident for repeated voice commands.

### RuBERT-tiny2

- sole intent and slot classifier;
- exact 21-label export contract matching the allowlist above;
- runs locally through ONNX Runtime;
- low confidence is handled deterministically.

RuBERT-tiny2 is the current Russian production baseline, not a permanent model
choice. Multilingual input or expansion toward 200+ intents requires a measured
model migration under `docs/testing/intent-model-evaluation.md`. The runtime
contract remains local intent + slots + calibrated confidence + explicit
out-of-domain handling. No candidate may introduce keyword routing, transcript
patches or LLM repair.

### DeepSeek

- open-ended natural-language answers only;
- receives at most 12 previous visible user/assistant turns;
- SSE Markdown streaming into one mutable chat message;
- no reasoning trace, JSON, DSL or actions;
- invoked only for `unknown` or a label outside the current local action registry;
- optional and unavailable offline.

### Exa Search

- invoked only for the RuBERT `web_search` label;
- uses `auto` retrieval with up to six bounded, primary-source-biased highlights;
- passes source excerpts to DeepSeek as untrusted data;
- shows only validated HTTPS citations and never executes page instructions;
- exposes inline citation links, a bounded source preview and optional related
  questions from a separate schema-constrained Exa request.

### Exa Agent

- invoked only for the RuBERT `web_research` label;
- uses asynchronous runs, `low` effort, SSE progress and bounded summary/findings output;
- releases the composer after a validated run ID is received;
- exposes progress and independent cancellation through one fixed `ResearchCard`;
- completed grounding is authoritative; no arbitrary Agent-generated widget is accepted;
- completed reports can be shared and continued only through a validated
  `previousRunId`.

### Silero Xenia

- local Russian TTS;
- consumes visible assistant text;
- receives a plain-text projection of visible Markdown;
- may start on a stable clause before the complete cloud answer;
- playback highlights the current text range.

### Wikimedia Commons

- optional online image enrichment for explicit visual requests;
- uses the fixed Commons API endpoint without a user key;
- accepts previews only from `upload.wikimedia.org` and source pages only from
  `commons.wikimedia.org`;
- returns title, source link and license attribution as data-only media contracts;
- never influences RuBERT routing, local actions or DeepSeek text.

## State And Permissions

- chat history, notes, reminders and timers use private SharedPreferences stores;
- the DeepSeek key uses Android Keystore-backed AES-GCM;
- the Exa key uses a separate Android Keystore-backed AES-GCM slot;
- Exa has an independent explicit enable switch;
- microphone requires `RECORD_AUDIO`;
- reminder notifications require `POST_NOTIFICATIONS`;
- alarms use Android clock intents;
- no contacts, camera, notification-listener, calendar-read or email-read permissions;
- dial, SMS, email, map, calendar and browser flows open system-owned confirmation
  or composition surfaces rather than sending data silently.

## UI

The app has two destinations: Chat and Settings.

Chat includes message history, partial transcript, processing route, composer,
dictation, continuous conversation, auto-scroll, rendered Markdown, TTS replay,
fixed result cards, citations and attributed media.
Settings includes model readiness, RuBERT threshold, automatic TTS, DeepSeek consent
and BYOK, Exa consent and BYOK, plus local data cleanup.

The microphone inside the composer performs dictation: final text remains editable
and is not sent automatically. The separate waveform action enters conversation
mode. Its state machine is:

```text
listening -> finalizing/transcribing -> RuBERT routing -> processing
          -> streaming text -> local speech -> playback completed -> listening
                                      \-> AEC + T-one partial -> barge-in -> listening
```

The normal chat stays visible during the session. The conversation dock exposes
current state, explicit interruption and End. During TTS, a foreground monitor uses
Android `VOICE_COMMUNICATION` plus available AEC/NS/AGC. A meaningful local T-one
partial interrupts playback and continues as the next utterance. On unsupported
devices, explicit interruption and post-playback capture remain the fallback.

## System Assistant

On supported devices the user may select the app as `ROLE_ASSISTANT` in system
settings. Android then routes the power/home/corner assistant gesture to
`VoiceInteractionService`, which hosts a compact Compose
`VoiceInteractionSession`.

The session:

- shares `AssistantRuntimeContainer`, `AssistantConversationCoordinator`, model
  instances, history and speech runtime with the full chat;
- starts local streaming recognition after an explicit system invocation;
- preserves RuBERT routing, action confirmation, DeepSeek/Exa consent and BYOK;
- can expand into the full application without duplicating a request;
- releases capture, playback and foreground network work when dismissed.

Current-screen text is optional and disabled by default. When enabled, sanitized
`AssistStructure` text is held only for the active session and may be included as
untrusted context for a non-action answer. It is never persisted, logged or allowed
to affect action selection or authorization. `FLAG_SECURE`, policy-disabled and
empty structures produce no context. Screenshot/VLM processing is not part of the
current implementation.

The three images under `docs/design/references/` remain visual references. They are
not evidence for removed generated-widget or organizer functionality.

## Out Of Scope

- Qwen and llama.cpp;
- Whisper and selectable ASR backends;
- Gemma and local widget planning;
- generated Widget DSL and arbitrary cloud widget generation;
- AppFunctions and privileged cross-app execution;
- organizer databases, inbox/calendar reading, tasks, routines, notification digest
  and personal memory;
- Linux CLI.

## Acceptance

Host:

```bash
python3 scripts/check_core_scope.py
./gradlew testDebugUnitTest :core:test :deepseek-connector:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew ktlintCheck detekt lintDebug
./gradlew assembleDebug
```

Device acceptance is defined separately in `docs/testing/core-device-acceptance.md`.
The final live microphone/demo recording remains the last manual gate.
