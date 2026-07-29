# Default Assistant Product Integration

**Status:** implementation and focused OPPO role acceptance complete
**Date:** 2026-07-29  
**Target:** Android 16 first, API 26 minimum, API 37 target

## Implementation Status

Implemented in the current branch:

- process-owned `AssistantConversationCoordinator` shared by Activity and service
  surfaces;
- process-level T-one, RuBERT and Silero warmup/release ownership in the active
  Activity/session process;
- `VoiceInteractionService`, `VoiceInteractionSessionService`,
  `VoiceInteractionSession` and a compatible local `RecognitionService`;
- lower-screen Compose overlay with live transcript, stable streaming text, final
  Markdown, fixed
  cards, route badges, text input, Stop, microphone, dismiss and full-chat actions;
- direct `ROLE_ASSISTANT` request with OEM settings fallbacks and role refresh;
- microphone onboarding and opt-in text-only `AssistStructure` context;
- bounded redaction, memory-only context lifetime and explicit “ЭКРАН” indicator;
- prompt-injection delimiters and tests for the DeepSeek context path.

Host tests, static analysis, release compilation and debug packaging pass. API 37
emulator acceptance confirms role selection, system-key invocation, the compact
overlay, text submission through the shared RuBERT route, correct navigation-bar
insets and five repeated invoke/dismiss cycles without an application crash.
Focused OPPO CPH2765 checks now cover the production RuBERT/DeepSeek visual route,
real Silero speaker-to-T-one microphone echo rejection, ColorOS IME placement,
launcher/application invocation, current-screen context, DeepSeek dismissal and
ten repeated invoke/dismiss sessions. The compact-to-expanded overlay has dedicated
unit and instrumentation render tests. Broader release-matrix coverage remains in
`docs/testing/core-device-acceptance.md`.

## Product Decision

Add an optional system-assistant mode based on Android `ROLE_ASSISTANT` and
`VoiceInteractionService`.

The reason is product access and continuity, not privilege escalation:

- invoke the assistant from any app with the system power/home/gesture shortcut;
- show a compact conversational surface without switching to the full application;
- preserve the existing local T-one -> RuBERT -> deterministic action path;
- preserve grounded Exa/DeepSeek answers when network features are enabled;
- optionally answer questions about the current screen with explicit consent.

The role does not grant AppFunctions, unrestricted application control, contacts,
calendar, photos, notifications, microphone, camera or location. Those remain
separate platform capabilities and permissions.

This plan intentionally excludes background wake-word listening and arbitrary UI
automation.

## Competitive Study

### Alice AI

Observed product patterns:

- onboarding offers becoming the default digital assistant;
- power, home and corner gestures open Alice over the current application;
- the assistant accepts text and voice without requiring a switch to the main app;
- voice conversation supports interruption and session context;
- the surface can render answers, generated images and camera-derived results;
- deterministic device and Yandex-ecosystem actions include music, smart home,
  weather, timers, alarms and dialing;
- the mobile application still requires internet for its primary assistant;
- wake-word activation is not available outside the application or from the locked
  screen.

Relevant sources:

- [Alice AI default assistant help](https://alice.yandex.ru/support/ru/assistant/alice-app)
- [Yandex default assistant announcement](https://yandex.ru/company/news/30-06-2026-02)
- [Alice AI voice conversation and interruption](https://alice.yandex.ru/support/ru/assistant/chat/voice)

### Perplexity Assistant

Observed product patterns:

- onboarding promotes the default-assistant role from inside the application;
- power, home and corner gestures invoke a Perplexity layer over the device;
- the product is positioned around voice questions, search and multi-step tasks;
- sessions are presented as a layer integrated with applications rather than a
  replacement launcher;
- public documentation does not specify which actions use Android intents, partner
  APIs or other integrations;
- public Android documentation does not establish offline behavior or promise
  access to arbitrary screen content.

Relevant source:

- [Perplexity Android Assistant help](https://www.perplexity.ai/help-center/en/articles/10450852-how-to-use-the-perplexity-android-assistant)

### Android Platform

The reusable platform primitives are:

- `VoiceInteractionService` for the selected assistant service;
- `VoiceInteractionSessionService` and `VoiceInteractionSession` for the system
  invocation surface;
- `AssistStructure`, `AssistContent` and an optional screenshot for current-screen
  context;
- `RoleManager.ROLE_ASSISTANT` for status inspection;
- system settings for user-controlled assistant selection.

The assistant role is exclusive. Selecting this application replaces the previous
holder for system invocation but does not uninstall it.

Relevant sources:

- [Android roles](https://source.android.com/docs/core/permissions/android-roles)
- [AOSP assistant role permissions](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/main/PermissionController/res/xml/roles.xml#108)
- [VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)
- [VoiceInteractionSession](https://developer.android.com/reference/android/service/voice/VoiceInteractionSession)

## Product Positioning

The target experience combines the strongest competitor patterns while preserving
the current product boundary:

| Capability | Alice | Perplexity | Offline Assistant target |
|---|---|---|---|
| System gesture invocation | Yes | Yes | Yes |
| Compact overlay | Yes | Yes | Yes |
| Local ASR | Not documented | Not documented | T-one RU |
| Local intent routing | Not documented | Not documented | RuBERT-tiny2 |
| Deterministic offline actions | Partial | Not documented | Yes |
| Local TTS | Not documented | Not documented | Silero Xenia |
| Grounded web answers | Search-backed | Core strength | Exa + DeepSeek |
| Barge-in | Yes | Not documented | Existing AEC/T-one gate |
| Screen context | Product examples imply it | Not documented | Explicit opt-in |
| Arbitrary UI automation | No public guarantee | No public guarantee | No |

The differentiator is a fast local control plane with an optional grounded cloud
knowledge plane.

## Latest System-Role Evidence

Validated on the API 37 16 KB ARM64 emulator on 2026-07-29:

- Android selected `OfflineAssistantVoiceInteractionService` as the active
  `ROLE_ASSISTANT` holder;
- the always-bound `:assistant_entry` process remained model-free and contained no
  view, activity or session objects;
- the system assistant key opened `OfflineAssistantVoiceInteractionSession`
  without launching `MainActivity`;
- the session rendered above the gesture navigation area and accepted text through
  the shared conversation coordinator;
- route diagnostics showed `LOCAL • RUBERT` for the local classifier path;
- five warm invoke/dismiss cycles ended with `mShown=false` and no application
  `FATAL`, ANR, model-contention or `AudioRecord` error.

The emulator invocation reported `mShowFlags=0x0`, so Android did not deliver an
`AssistStructure`. Current-screen context remains a physical-device/OEM acceptance
item rather than a host claim.

## User Experience

### Onboarding

Settings adds a `System assistant` section:

- current state: `Not selected`, `Selected` or `Unavailable`;
- primary action: `Open system assistant settings`;
- concise explanation that selection replaces the current power/home gesture
  handler;
- no claim that selecting the role grants access to all phone data;
- optional `Use current screen` toggle, off by default;
- optional `Speak answers` toggle reuses the existing speech setting.

The application first uses `RoleManager.createRequestRoleIntent` and verifies the
result when it resumes. If the OEM does not expose that flow, it falls back through
voice-input, default-app and general settings surfaces for ColorOS compatibility.

### Invocation

The hot path is:

```text
Power/home/corner gesture
  -> VoiceInteractionService
  -> compact VoiceInteractionSession
  -> immediate listening indicator
  -> local streaming T-one transcript
  -> local RuBERT route
       -> deterministic skill + confirmation/card
       -> Exa/DeepSeek streaming answer when explicitly routed and enabled
  -> local Silero speech
  -> listening again in conversation mode
```

The initial surface contains:

- assistant state and route indicator;
- live transcript;
- one primary microphone control;
- stop/end control;
- current response with streaming Markdown and fixed cards;
- an expand control that opens the existing full chat;
- an explicit current-screen chip when context is available and enabled.

The overlay should occupy only the lower portion of the screen initially. It may
expand when the response or card needs more space. It must not imitate Alice or
Perplexity branding.

Reference behavior captured from Alice and ChatGPT establishes a
compact-to-expanded direction:

- idle/listening is a minimal, immediately recognizable voice surface;
- text, sources and fixed widgets expand into one anchored response panel above
  the current application;
- the composer remains a separate stable control at the panel edge;
- expansion preserves visible background context instead of opening the full app;
- light/dark treatment follows the host theme and retains Offline Assistant's own
  shape, iconography and route indicators.

Streaming must not re-layout the complete panel on every token. Render plain text
during generation, throttle follow-latest scrolling and construct Markdown once
the response is final.

## Latest Physical Regression Evidence

Validated on OPPO CPH2765 on 2026-07-29:

- retrained RuBERT classifies `Покажи фотографии Кривого Рога` as `unknown` with
  confidence `0.9868`; the production route streamed one DeepSeek response and
  loaded three attributed Wikimedia images;
- a real Silero speaker -> microphone test completed the local weather response,
  returned to T-one listening and created no echoed user turn during the
  observation window;
- short decoder noise observed as `кога` is discarded before RuBERT while
  intentional short controls remain accepted;
- the composer ends at the ColorOS keyboard boundary and no longer jumps to the
  top or renders beneath the keyboard.

### Session States

```text
opening -> warming/listening -> finalizing -> routing
        -> local action -> result -> speaking -> listening
        -> cloud answer -> streaming/speaking -> listening
        -> error/offline/permission
        -> dismissed
```

Dismissal stops microphone capture, speech, cloud streaming and background UI work.
Background Exa research may continue only if the user explicitly chose that behavior
before dismissing the session.

### Current-Screen Questions

Examples:

- "Объясни, что написано на этом экране."
- "Кратко перескажи эту страницу."
- "Что означает эта ошибка?"

Rules:

1. Current-screen use is disabled by default.
2. The session never sends screen context to a network provider without a visible
   per-session indication.
3. `FLAG_SECURE`, policy-disabled, empty and malformed context fail closed.
4. Screen text and screenshots remain memory-only and are discarded on dismissal.
5. Screen context is never written to chat history, logs or analytics.
6. Context can inform a natural-language answer but cannot bypass RuBERT or authorize
   an Android action.
7. Screenshots are not requested or transported in this release.

The first release may support extracted text only. Screenshot/VLM support is a
separate gate because the current DeepSeek connector is text-only.

## Architecture

### Shared Conversation Coordinator

`ChatViewModel` currently owns conversation orchestration and is scoped to
`MainActivity`. Reusing it directly from a system service would create lifecycle
leaks or duplicate behavior.

Extract a platform-owned, lifecycle-neutral coordinator:

```text
AssistantRuntimeContainer
  -> AssistantConversationCoordinator
       -> AssistantEngine
       -> CancellableAnswerProvider
       -> AssistantSpeech
       -> ChatHistoryStore
       -> PlatformActions
       -> StateFlow<AssistantConversationState>

MainActivity ChatViewModel
  -> coordinator adapter

VoiceInteractionSession
  -> coordinator session adapter
```

The coordinator owns one request state machine and exposes explicit
`startSession`, `submitText`, `submitVoice`, `stop`, `dismiss` and action methods.
Compose surfaces observe immutable state. Android services and activities own their
own coroutine scopes and cancel them deterministically.

The refactor must preserve the existing chat behavior and tests before any role
components are added.

### Android Components

Add:

- `OfflineAssistantVoiceInteractionService`;
- `OfflineAssistantSessionService`;
- `OfflineAssistantVoiceInteractionSession`;
- `AssistantSessionLifecycleOwner`;
- `AssistantSessionScreen` rendered in a `ComposeView`;
- `DefaultAssistantStatusRepository`;
- `DefaultAssistantSettingsLauncher`;
- `AssistContextSanitizer`.

Manifest requirements:

- the voice interaction service is exported and protected by
  `android.permission.BIND_VOICE_INTERACTION`;
- service metadata references `res/xml/voice_interaction_service.xml`;
- the application does not request `BIND_VOICE_INTERACTION` as a user permission;
- no SMS, Call Log, Contacts, Calendar read, media or notification-listener
  permissions are added for this feature;
- API 37 screen-content support declares
  `READ_ASSIST_STRUCTURE_SCREEN_CONTENT` only when that path is implemented.

### Runtime And Memory

`MainActivity.onTrimMemory(TRIM_MEMORY_UI_HIDDEN)` currently releases the shared
transcriber. That policy is incompatible with a selected system assistant.

Replace activity-owned release with a process-level warmup policy:

- the always-running `VoiceInteractionService` entry stays in a separate,
  model-free process;
- RuBERT and T-one warm when the session service is created;
- Silero warms after listening starts, before an answer is expected;
- DeepSeek and Exa hold no local model memory;
- low-memory callbacks release T-one and Silero in that order while keeping the
  lightweight coordinator valid;
- one runtime instance is shared by full chat and assistant sessions;
- simultaneous full-chat and overlay requests are serialized or explicitly handed
  over, never duplicated.

### Routing And Permissions

System invocation does not change routing:

- RuBERT remains the sole intent and slot classifier;
- local action intents still require `ActionConfirmationCard`;
- low-confidence actions still clarify and never call DeepSeek;
- DeepSeek still returns natural-language Markdown only;
- screen context cannot select or execute actions;
- all existing BYOK and network-consent gates remain active.

Microphone permission remains a normal runtime permission. If it is absent, the
session opens in text mode and offers a system permission action.

## Delivery Plan

### PR 1: Shared Conversation Runtime

- extract `AssistantConversationCoordinator` from `ChatViewModel`;
- keep `ChatViewModel` as an Activity/Compose adapter;
- move shared warmup and release ownership out of `MainActivity`;
- add coordinator lifecycle, cancellation and concurrency tests;
- prove no behavior change in chat, voice, search, research, TTS or actions.

Acceptance:

- all existing checks pass unchanged;
- opening and closing ten coordinator sessions leaves no active capture, playback or
  network job;
- only one T-one, RuBERT and Silero runtime instance exists per process.

### PR 2: Role And System Entry

- add the voice interaction service/session components and metadata;
- add role status and OEM-safe settings navigation;
- render a minimal branded Compose overlay;
- support invoke, dismiss, expand-to-full-chat and text input;
- add a debug route/source indicator.

Acceptance:

- the application appears in ColorOS default digital assistant settings;
- selecting it makes the power/home gesture open the overlay;
- deselecting it restores the previous assistant without residual services;
- no extra dangerous permission is requested.

### PR 3: Product Voice Session

- connect local T-one streaming transcript;
- route through the existing RuBERT/skill/DeepSeek pipeline;
- connect Silero and existing barge-in behavior;
- implement permission, offline, loading and cancellation states;
- preserve one streaming message instead of final-response duplication.

Acceptance:

- gesture to visible listening state: p95 under 500 ms when the process is warm;
- gesture to first ASR partial: p95 under 1.2 s after speech begins on OPPO CPH2765;
- local intent result after final transcript: p95 under 500 ms;
- cloud answer shows its first visible chunk without blocking dismissal;
- user interruption stops speech and starts the next turn.

### PR 4: Explicit Screen Context

- receive and sanitize `AssistStructure` and `AssistContent`;
- show a visible context indicator and per-session control;
- support bounded text-only screen questions;
- discard context on dismissal;
- add API 37 permission/flag handling;
- defer screenshot/VLM transport unless a compatible provider is selected.

Acceptance:

- current-screen text improves an explicit question when available;
- secure or opted-out screens expose no content;
- no screen data appears in logs, persistence or unrelated prompts;
- screen context cannot cause an action to execute.

### PR 5: Hardening And Product Review

- test process death, lock screen, rotation, split screen and repeated invocation;
- test calls, audio focus, Bluetooth, wired headset and notification interruptions;
- verify dynamic type, TalkBack, cutouts and one-handed reachability;
- add baseline profiles and macrobenchmarks for system invocation;
- run a 50-session mixed local/cloud stability test;
- capture product screenshots after device acceptance; a presentation demo is
  optional and is not a correctness gate.

Acceptance:

- no crash or stuck microphone across 50 mixed sessions;
- no duplicate models or requests in memory traces;
- dismissal always releases audio focus and visible UI;
- accessibility has no unlabeled controls or focus traps;
- product review approves the compact and expanded session states.

## Test Matrix

Automated:

- role status repository with absent, selected and other-holder states;
- settings intent fallback order;
- coordinator cancellation and single-request ownership;
- session state reducer;
- assist-context size, redaction and secure-window handling;
- no-context-to-action invariant;
- manifest validation for forbidden dangerous permissions;
- API 26/29/34/36/37 compilation paths.

Physical device:

- OPPO CPH2765 / Android 16 / ColorOS;
- cold and warm system invocation;
- screen on, locked and unlocked;
- app foreground, another app foreground and launcher;
- offline local action;
- online grounded search;
- continuous voice and barge-in;
- role switch to and from Google Assistant;
- memory pressure and process recreation.

## Metrics

Record:

- gesture-to-overlay;
- gesture-to-listening;
- first ASR partial;
- final transcript;
- RuBERT completion;
- first cloud token;
- first visible answer text;
- first spoken audio;
- peak and steady-state PSS;
- session cancellation latency;
- successful invocation rate;
- permission and context opt-in rates.

Metrics must distinguish cold, warm and already-active process states.

## Security And Store Policy

- do not request SMS or Call Log merely because the role can grant them;
- do not claim AppFunctions support;
- do not use AccessibilityService for general UI control;
- do not persist screen context or screenshots;
- do not start network work until the existing consent and BYOK gates pass;
- show when screen context or cloud processing is active;
- preserve backup-disabled credential storage;
- document assistant-role behavior and data use in the privacy disclosure before
  distribution.

## Explicit Non-Goals

- background "always listening" wake word;
- lock-screen voice activation without a system gesture;
- arbitrary tapping, scrolling or text entry in third-party applications;
- bypassing action confirmation;
- reading inbox, calendar, contacts, photos or notifications;
- AppFunctions without privileged/known-signer access;
- replacing the launcher or notification shade;
- restoring removed local LLM or generated-widget runtimes.

## Scope Change Gate

Before PR 1 is merged:

1. update `docs/superpowers/specs/2026-07-29-core-assistant-scope.md`;
2. update `AGENTS.md` to permit the default-assistant integration;
3. keep AppFunctions, arbitrary UI automation and new sensitive permissions
   explicitly prohibited;
4. add the system-assistant device flow to
   `docs/testing/core-device-acceptance.md`.
