# Core Offline Assistant Scope

**Status:** normative
**Date:** 2026-07-29
**Platform:** Android, ARM64, `minSdk 26`, `targetSdk 37`

## Goal

Deliver a stable vertical assistant demo around five capabilities:

1. local speech recognition;
2. local intent and slot classification;
3. a small deterministic action allowlist;
4. cloud answers for genuinely open-ended requests;
5. local speech synthesis.

The product is intentionally not a general personal operator or generated-UI
platform.

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

unknown
  -> DeepSeek HTTPS/SSE
  -> one streaming plain-text message
  -> GenericAnswerCard source marker

Any assistant text
  -> Silero Xenia / ONNX Runtime
  -> chunked local playback
```

## Routing Contract

| RuBERT result | Route | Cloud allowed |
| --- | --- | --- |
| supported action, high confidence | local normalizer + skill | no |
| supported action, low confidence | clarification | no |
| unsupported historical label | explicit error | no |
| model unavailable | explicit error | no |
| `unknown` | DeepSeek plain-text answer | yes, with consent + BYOK + network |

DeepSeek is not a fallback parser. It cannot repair RuBERT, emit command JSON,
execute actions or generate widgets.

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
| `help` | `help_card` |
| `unknown` | DeepSeek text + `generic_answer_card` source |

Infrastructure cards are `clarification_card`, `permission_card` and `error_card`.
The registry contains no other widget types.

## Model Responsibilities

### T-one RU

- streaming Russian ASR only;
- receives 16 kHz microphone PCM and feeds sherpa-onnx;
- emits partial transcript updates and endpoint events;
- context stays resident for repeated voice commands.

### RuBERT-tiny2

- sole intent and slot classifier;
- exact ten-label export contract matching the allowlist above;
- runs locally through ONNX Runtime;
- low confidence is handled deterministically.

### DeepSeek

- open-ended natural-language answers only;
- SSE text streaming into one mutable chat message;
- no reasoning trace, JSON, DSL or actions;
- invoked only for `unknown`;
- optional and unavailable offline.

### Silero Xenia

- local Russian TTS;
- consumes visible assistant text;
- may start on a stable clause before the complete cloud answer;
- playback highlights the current text range.

## State And Permissions

- chat history, notes, reminders and timers use private SharedPreferences stores;
- the DeepSeek key uses Android Keystore-backed AES-GCM;
- microphone requires `RECORD_AUDIO`;
- reminder notifications require `POST_NOTIFICATIONS`;
- alarms use Android clock intents;
- no calendar, contacts, camera, notification-listener or role permissions.

## UI

The app has two destinations: Chat and Settings.

Chat includes message history, partial transcript, processing route, composer,
microphone/send/stop action, auto-scroll, TTS replay and fixed result cards.
Settings includes model readiness, RuBERT threshold, automatic TTS, DeepSeek consent
and BYOK, plus local data cleanup.

The three images under `docs/design/references/` remain visual references. They are
not evidence for removed generated-widget or organizer functionality.

## Out Of Scope

- Qwen and llama.cpp;
- Whisper and selectable ASR backends;
- Gemma and local widget planning;
- generated Widget DSL and cloud widget generation;
- AppFunctions and assistant-system role integration;
- tasks, calendar, email, routines, notification digest and personal memory;
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
