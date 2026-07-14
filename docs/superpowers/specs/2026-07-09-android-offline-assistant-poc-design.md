# Android Offline Assistant PoC Design Spec

Дата: 2026-07-09
Актуализировано: 2026-07-14
Статус: implementation-aligned design baseline; functional DoD принят, UX stabilization/release profiling выполнены на Pixel 10
Целевая платформа: Android, Kotlin, Jetpack Compose

## 1. Цель продукта

PoC должен доказать, что Android-приложение может работать как локальный голосовой и текстовый ассистент без обязательного облака для ASR, NLU и локальных LLM-ответов.

Пользовательский опыт строится вокруг чат-интерфейса:

- пользователь пишет текст или записывает голосовое сообщение;
- голос локально распознается выбранным ASR backend (whisper.cpp или sherpa-onnx);
- команда локально разбирается через fine-tuned RuBERT-tiny2;
- сложные/общие вопросы, которые RuBERT не классифицирует уверенно как действие, отвечаются локально через Qwen2.5 0.5B Instruct и llama.cpp;
- ассистент выполняет небольшой набор Android-действий;
- ответ возвращается не строкой, а структурированным `AssistantResponse`;
- UI отображает текстовый bubble и, если есть, визуальный result widget.
- видимый текст ответа опционально озвучивается локальным TTS без блокировки чата.

Linux CLI не является deliverable. При этом core-логика должна быть отделена от Android UI настолько, чтобы в будущем можно было переиспользовать intent schema, NLU pipeline, slot normalization, local answer provider, skill interfaces и response/widget contracts вне Android.

ASR/NLU boundary: the selected ASR backend returns a final transcript; RuBERT/rule NLU and generic slot normalization decide whether the transcript is an action. Streaming partials are UI preview data and must not execute commands. Do not add hard-coded fixes for one observed bad transcript. Recognition errors should be addressed through ASR configuration/model quality, representative evaluation data, or general language normalization rules that apply to a class of utterances.

### 1.1 Current Implementation Profile

Текущая реализация использует package `com.offlineassistant.poc` и два Gradle-модуля:

- `:core` — pure Kotlin contracts, assistant orchestration, NLU abstractions, slot normalization, storage/weather contracts and reusable domain logic;
- `:app` — Android UI, platform adapters, ONNX Runtime, AudioRecord, whisper.cpp JNI, sherpa-onnx, llama.cpp JNI, model materialization and Android persistence.

Build baseline: `minSdk 26`, `compileSdk/targetSdk 37`, AGP 9.2.1, Gradle 9.4.1, JDK 17, NDK 27.0.12077973 and CMake 3.22.1. Dependency verification uses committed SHA-256 metadata. CI executes static analysis plus combined `:app`/`:core` JVM coverage and the native APK build in parallel; the current line-coverage regression floor is 45%. Host and Android 17/16 KB runtime evidence is recorded in `docs/testing/2026-07-14-android-17-migration.md`.

Вертикальный путь text/voice -> local model -> structured response -> Compose widget реализован и проверяется unit- и connected-тестами на Pixel. Qwen работает только как answer provider, стримит plain text в одно assistant message и прогревается в фоне. Выбираемый ASR backend, RuBERT, Qwen и Silero выполняются локально.

Функциональный PoC и все machine-readable Definition-of-Done evidence groups завершены на Pixel 10:

- RuBERT slot-only evaluation прошла `8/8`, host aggregate report публикует intent accuracy `1.000`, macro F1 `1.000` и slot F1 `0.873`;
- Whisper manifest evaluation прошла `4/4`, Qwen quality/repeatability evaluation прошла `12/12`;
- runtime telemetry для Whisper/RuBERT/Qwen подтверждена реальными model runs;
- исторический live human Mic -> Whisper -> RuBERT -> system-passive `TimerCard` acceptance записан и проверен: ASR `1975 ms`, NLU `618 ms`, total `2594 ms`, confidence `0.983`, fallback `false`; текущий продуктовый таймер после этой проверки переведён на управляемый in-app runtime;
- `scripts/acceptance_status.py` и `scripts/final_dod_status.py` возвращают `complete: true`.

После persistent-context и prompt-prefill оптимизации финальный прогретый Pixel 10 benchmark измерил visible TTFT `1.32-4.08` seconds (median `2.07` seconds) вместо исходных `3.80-7.22` seconds (median `4.10` seconds). Усиленный 12-case quality gate, включая обязательное упоминание кеша для свежих offline-данных, прошёл `12/12`; полная генерация всё ещё зависит от длины ответа и в оптимизированных runs занимала примерно `5-29` seconds. Это измеренное ограничение PoC, а не незакрытый функциональный evidence gate.

Post-DoD extension implemented: on-device Silero `v5_5_ru` TTS with the `xenia` voice. The complete external split-ONNX bundle, Kotlin tokenizer/accentor/homograph frontend, streaming/cancellation contracts, Android inference and playback controller are enabled in `MainActivity` through a background-created gateway. Pixel 10 native synthesis, intent/chat integration and repeated Qwen plus TTS acceptance pass.

The 2026-07-13 stabilization pass adds asynchronous recorder finalization, 40 ms Qwen UI batching, native generation cancellation, conditional auto-scroll, T-one trailing-silence endpoint detection, stable/mutable partial transcript rendering, staged/adaptive model residency, memory-pressure release, first-visible-token/first-audible-PCM telemetry, early phrase-level TTS with one-chunk prefetch, bounded history, persistent in-app timer state, reminder rescheduling, cached weather fallback, generic cardinal-number/spoken-time ITN, R8/resource shrinking and Pixel-generated Startup/Baseline Profiles. Measured details are in `docs/testing/2026-07-13-ux-optimization-results.md`.

## 2. Goals

- Реализовать Android-only PoC с Kotlin и Jetpack Compose.
- Поддержать текстовый чат.
- Поддержать запись голосового сообщения.
- Выполнить локальное распознавание речи через выбираемый offline ASR backend; default — T-one RU Streaming, Whisper Base Q5_1 остается стабильным selectable fallback.
- Выполнить локальное intent + slot recognition через RuBERT-tiny2, экспортированный в ONNX.
- Выполнить локальные ответы на сложные/общие вопросы через Qwen2.5 0.5B Instruct GGUF и llama.cpp.
- Поддержать небольшой набор skills: время, таймер, будильник, напоминание, заметка, калькулятор, открытие приложения, help, clarification, error.
- Возвращать структурированный `AssistantResponse` с опциональным `WidgetPayload`.
- Отрисовать MVP widget cards в Compose.
- Сделать internal `WidgetPreviewScreen`.
- Сделать debug surface для transcript, intent, confidence, slots, normalized command, fallback usage, action result и latency.
- Работать офлайн для ASR, NLU и LLM-ответов.
- Озвучивать intent- и Qwen-ответы локальным голосом Xenia после прохождения отдельного Android runtime gate.

## 3. Non-Goals

- Не делать Linux CLI в рамках PoC.
- Не делать полноценного production weather provider. Погода в PoC может быть mock/cache/optional online и должна явно показывать источник.
- Не обучать большую production NLU-модель в Milestone 1.
- Не гарантировать управление системным Android alarm после делегирования в системное приложение. Если управление недоступно, показывается passive card. Таймер в текущей реализации работает внутри приложения.
- Не делать облачную зависимость обязательной для core flow.

## 4. High-Level Architecture

```text
User text / voice message
        |
        v
Input Controller
        |
        |-- if text -------------------------|
        |                                    v
        |-- if voice --> AudioRecord --> selected local ASR backend --> transcript
                                             |
                                             v
Assistant Engine
        |
        v
NLU Pipeline
        |
        |-- RuBERT-tiny2 intent + slot filling
        |
        v
Confidence / routing decision
        |
        |-- high confidence --> slot normalization + validation
        |
        |-- unknown / low confidence --> Qwen2.5 0.5B Instruct via llama.cpp --> plain text answer
        |
        v
SkillRegistry execution for RuBERT/rule actions only
        |
        v
AssistantResponse
        |
        |-- text response
        |-- optional WidgetPayload
        |-- optional DebugInfo
        |
        v
Chat UI
        |
        |-- message bubble
        |-- widget registry lookup by widget.type
        |-- rendered Compose widget card
        |
        |-- AssistantSpeech --> sentence chunks --> local Silero TTS --> AudioTrack
```

Главное архитектурное правило: Assistant Engine возвращает данные, а не UI. Он знает о `WidgetPayload(type, payload)`, но не знает, как выглядит `TimerCard`, `WeatherCard` или любой другой widget.

## 5. Module Boundaries

Текущая реализация намеренно использует два Gradle-модуля и логические package boundaries внутри них:

```text
android-offline-assistant-poc/
  settings.gradle.kts
  build.gradle.kts
  app/
  core/
  training/
  docs/
  scripts/
```

Физическое разделение на большее число модулей не требуется для PoC. Границы ниже обязательны на уровне dependencies и packages, чтобы их можно было вынести в отдельные модули без переписывания контрактов.

### 5.1 `:core`

Pure Kotlin module without Android UI dependencies.

Owns:

- intent schema and NLU contracts;
- normalized command and slot normalization contracts;
- `AssistantResponse`;
- `WidgetPayload`;
- `DebugInfo`;
- local answer provider/fallback bridge contracts;
- `Skill` and `SkillResult` contracts;
- concrete reusable skills and `SkillRegistry`;
- note/reminder storage contracts;
- weather provider contract and mock provider;
- `AssistantEngine` orchestration and reusable deterministic action logic;
- serializable JSON contracts.

Allowed dependencies:

- Kotlin stdlib;
- kotlinx.serialization;
- coroutines if needed for interfaces.

Disallowed:

- Compose;
- Android `Context`;
- Android platform APIs;
- whisper.cpp;
- llama.cpp;
- ONNX Runtime Android.

`:core` does not render UI and does not call Android APIs directly. Android behavior enters through injected storage, weather, local-answer and platform-facing contracts.

Current implementation note: concrete MVP skills live under `com.offlineassistant.core.skills`, are registered by supported intent, and are invoked by `AssistantEngine` only after deterministic normalization/validation. Android platform effects remain in injected app-layer adapters; this is not a reason to move action parsing into Qwen.

### 5.2 `:app`

Android application module. It owns four logical areas.

#### Android platform adapters

- permissions;
- `Intent`-based app launch;
- alarm delegation and in-app timer actions;
- notification scheduling;
- SharedPreferences-backed note/reminder stores for the PoC;
- package manager lookup;
- Android resource and model materialization.

#### ASR integration

- persisted voice-model catalog and selection in Settings;
- bundled single-file and multi-file ASR model discovery/materialization;
- `AudioRecord` PCM capture, direct chunk delivery for streaming ASR and optional WAV writing for non-streaming/debug paths;
- optional PCM chunk delivery to streaming-compatible ASR sessions;
- whisper.cpp JNI bridge;
- sherpa-onnx offline Zipformer and online T-one adapters;
- reusable recognizer/context cache and background warm-up;
- transcription result mapping;
- latency/error reporting.

#### NLU and LLM integration

- RuBERT-tiny2 ONNX Runtime adapter and tokenizer/slot decoding;
- Qwen2.5 0.5B Instruct GGUF materialization and llama.cpp JNI;
- background Qwen model and `32768` context warm-up;
- serialized persistent-context reuse with per-request llama memory reset and CPU prompt batch `512`;
- compact plain-text prompt assembly with conditional topic policies and token streaming;
- UTF-8 stream-boundary buffering before JNI UTF-16 string creation;
- plain text output sanitization;
- error mapping that never executes model-generated actions.

#### Compose UI

- `MainChatScreen`;
- `DebugScreen`;
- `SettingsScreen`;
- `WidgetPreviewScreen`;
- message bubbles;
- widget registry;
- concrete widget cards;
- UI state and ViewModels.

Compose consumes `AssistantResponse` and `WidgetPayload`. It does not parse user intent directly and does not know model output formats.

### 5.3 Future Extraction Rule

If project scale requires more Gradle modules, packages may be extracted into `core-contracts`, `core-engine`, `core-nlu`, `core-skills`, `android-platform`, `android-asr-whisper`, `android-llm-llamacpp` and `android-ui-compose`. Such extraction must preserve the existing contracts and must not be a prerequisite for PoC acceptance.

## 6. Core Data Contracts

Use Kotlin serializable data classes. JSON payloads should be accepted and emitted through kotlinx.serialization `JsonObject`.

External JSON should use stable lower snake/camel values compatible with the product examples. Kotlin enum names may stay uppercase internally, but serialized API values should be `success`, `clarification_required`, `permission_required`, and `error`.

```kotlin
@Serializable
data class AssistantResponse(
    val status: ResponseStatus,
    val text: String,
    val intent: String? = null,
    val widget: WidgetPayload? = null,
    val debug: DebugInfo? = null
)

@Serializable
enum class ResponseStatus {
    @SerialName("success")
    SUCCESS,

    @SerialName("clarification_required")
    CLARIFICATION_REQUIRED,

    @SerialName("permission_required")
    PERMISSION_REQUIRED,

    @SerialName("error")
    ERROR
}

@Serializable
data class WidgetPayload(
    val type: String,
    val payload: JsonObject
)

@Serializable
data class DebugInfo(
    val transcript: String? = null,
    val intent: String? = null,
    val confidence: Double? = null,
    val nluSource: NluSource? = null,
    val slots: JsonObject? = null,
    val normalizedCommand: JsonObject? = null,
    val fallbackUsed: Boolean = false,
    val fallbackReason: String? = null,
    val actionResult: String? = null,
    val latencyMs: LatencyBreakdown? = null
)

@Serializable
data class LatencyBreakdown(
    val asr: Long? = null,
    val nlu: Long? = null,
    val fallbackLlm: Long? = null,
    val normalization: Long? = null,
    val skillExecution: Long? = null,
    val total: Long
)
```

`fallbackUsed` and `fallbackLlm` are legacy compatibility names in the current debug contract. They mean that the local answer-provider route was used; they do not mean that Qwen parsed or executed an Android command.

Example response:

```json
{
  "status": "success",
  "text": "Поставил таймер на 5 минут.",
  "intent": "set_timer",
  "widget": {
    "type": "timer_card",
    "payload": {
      "timer_id": "uuid",
      "duration_seconds": 300,
      "remaining_seconds": 300,
      "label": "чай",
      "state": "running"
    }
  }
}
```

## 7. Intent Schema

Initial intent set:

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

Each intent has:

- stable string id;
- required slots;
- optional slots;
- clarification rule;
- skill handler id;
- widget type mapping if successful.

Example schema shape:

```kotlin
@Serializable
data class IntentDefinition(
    val id: String,
    val requiredSlots: List<String>,
    val optionalSlots: List<String>,
    val skillId: String,
    val successWidgetType: String? = null
)
```

## 8. NLU Pipeline

### 8.1 Text Input

Text messages go directly to `AssistantEngine.handleText(input: String)`.

### 8.2 Voice Input

Voice messages follow:

```text
AudioRecord -> PCM buffer -> whisper.cpp -> transcript -> AssistantEngine.handleText(transcript)
```

The transcript is shown in the chat as a local preview before/while Assistant Engine runs.

### 8.3 RuBERT-tiny2

RuBERT-tiny2 produces:

- intent class;
- intent confidence;
- token-level slot labels;
- optional raw logits in debug builds only.

NLU output contract:

```kotlin
@Serializable
data class NluResult(
    val intent: String,
    val confidence: Double,
    val slots: JsonObject,
    val source: NluSource
)

enum class NluSource {
    RUBERT_TINY2,
    STUB,
    FALLBACK_LLM
}
```

`FALLBACK_LLM` is retained only as a legacy debug-source value used by the `FallbackParser` compatibility bridge. It must not be interpreted as an NLU authority for Android actions. Raw token spans and logits stay inside the ONNX adapter/debug path; normalized runtime slots cross the core boundary as `JsonObject`.

### 8.4 Routing Decision

Qwen should be used only as a local text answer provider when:

- intent confidence is below threshold;
- intent is `unknown`;
- the user asks a complex/general question rather than a supported Android action.

Qwen must not be used as a backup command parser for RuBERT. Android actions must come from RuBERT/rule NLU plus deterministic slot normalization and skill execution.

Current routing policy:

- the configurable action threshold defaults to `0.75`;
- a known intent at or above the threshold goes through deterministic slot normalization and validation;
- missing required slots on that trusted action path produce `ClarificationCard`;
- validation/execution failures produce `ErrorCard`;
- `unknown` or any intent below the action threshold goes to Qwen for a plain-text answer only;
- the answer adapter's `0.45` confidence guard validates whether an answer can be shown, not whether an Android action can run.

Thresholds must be configurable from `SettingsScreen` in internal/debug builds.

## 9. Qwen Local Answer Provider

Qwen2.5 0.5B Instruct via llama.cpp is used only as a local answer provider for complex/general questions. It is not a command parser and it is not a backup classifier for RuBERT.

The core contract for this path is `LocalAnswerProvider` / `StreamingLocalAnswerProvider`, returning `LocalAnswerResult`. The `FallbackParser` bridge exists for `AssistantEngine` compatibility, but answer-provider adapters must not populate command `intent` or `slots`.

Qwen prompt must demand ordinary text:

- Russian plain text answer;
- no JSON;
- no markdown/code fences;
- no XML/thinking tags;
- a completed answer of 2-4 sentences and normally no more than 600 characters;
- if fresh online data is needed, state that offline mode cannot verify it.

Validation rules:

- Qwen output never executes Android actions;
- if Qwen accidentally emits JSON, the UI must not show raw JSON to the user;
- empty/invalid text output produces `GenericAnswerCard` or `ErrorCard`;
- command-like text from a low-confidence RuBERT result may receive a plain text answer, but must not be converted into `WidgetPayload` for an action.

The LLM is not trusted as an executor and must not propose structured commands.

Current implementation profile:

- model: Qwen2.5 0.5B Instruct Q4_K_M GGUF;
- runtime: llama.cpp on Android CPU;
- model is side-loaded for the internal build and materialized into app-private storage;
- model weights are kept loaded between requests and warmed in a background coroutine;
- one serialized persistent llama context is created during warm-up, cleared between requests, streams visible token batches and finalizes the same assistant message;
- context size is `32768` tokens;
- configured generation ceiling is `8192` tokens;
- stop markers and sanitization suppress chat template markers, JSON wrappers and thinking tags;
- a shared native stop policy stops at four sentences or at a sentence boundary after 600 UTF-8 characters, has a 900-character emergency cap, and ends generation after 90 seconds;
- the stop policy is pure C++ and is covered by a host test as well as the Android native build.

The configured ceiling is a capacity limit, not a desired answer length. Prompt policy and native stopping keep normal UI answers bounded without reducing the model's context/generation capacity. Current connected evaluation proves repeated generation and plain-text streaming, but observed historical latency can exceed the performance target in Section 23; the new stop policy still needs a real-device latency rerun.

### 9.1 On-Device Speech Output

Speech output consumes the user-visible response after intent/skill/LLM processing. It is not part of NLU, does not inspect widget payloads and never influences action execution.

The selected profile is Silero `v5_5_ru`, speaker `xenia`, 48 kHz. Automatic Russian stress, homograph resolution, `yo` restoration and sentence intonation are model/frontend requirements, not optional UI polish. Phrase-specific pronunciation substitutions are prohibited.

`AssistantSpeech` is a pure non-blocking core boundary. `SpeechChunker` acts as an adaptive planner and converts Qwen deltas into `PlannedSpeechChunk(text, startOffset, endOffset, boundaryType)`. Complete sentences emit immediately. Clause punctuation after 28 characters is eligible when estimated queued plus active speech falls below the 1,400 ms target; a healthy audio buffer lets the planner wait for the stronger sentence boundary. Text without punctuation uses a word-boundary fallback near 64 characters only before the first spoken phrase; continuation text waits for punctuation unless it reaches the 180-character malformed-output guard. The shorter first-phrase thresholds are deliberate: measured first-audio latency reached 2,698 ms when a 45-character natural clause fell below the old 50-character limit, then improved to 1,390 ms after the clause fix. Keeping the continuation guard separate prevents audible pauses inside otherwise valid sentences. Finalization flushes only the unspoken tail. Intent responses use the same boundary but usually arrive as one final chunk.

The Android controller owns cancellation, ordered synthesis, PCM playback and transient audio focus. Synthesis and playback are separate bounded stages, allowing the next phrase to synthesize while the previous phrase plays without unbounded PCM buffering. One persistent `AudioTrack.MODE_STREAM` session accepts every PCM chunk in the response and drains only after an ordered response-end marker, eliminating track teardown/recreation between phrases. Playback-head callbacks publish the active chunk's exact visible-text range; Compose highlights that phrase and changes its speaker action to Stop. Word-level highlighting is intentionally deferred until normalized Silero tokens can be mapped reliably back to source text. Speech playback never controls `ChatUiState.isProcessing`. A user Stop suppresses later speech deltas only for that message, while a new request or microphone recording invalidates both stages and stops `AudioTrack` before `AudioRecord` starts. PCM trimming/crossfading is not part of the design.

Current runtime status: the reconstructed split-ONNX graph, Kotlin linguistic frontend and JTransforms ISTFT are the selected Android runtime. Sessions and repeated phrase PCM are cached, synthesis is cancellable, first audible PCM is measured, and `AudioTrack`/audio focus remain alive until submitted PCM has played. Legacy PyTorch Mobile is explicitly excluded.

## 10. Slot Normalization and Validation

Slot normalization converts raw model output into deterministic command data.

Examples:

- `на 5 минут` -> `duration_seconds = 300`
- `завтра в 7:30` -> ISO date/time using device timezone
- `чай` -> timer label
- `125 умножить на 37` -> expression `125 * 37`, display `125 × 37`
- `восемнадцать умножить на три` -> generic Russian cardinal ITN -> expression `18 * 3`
- `семь тридцать` in a time slot -> `07:30`
- `телеграм` -> app candidate list via package manager

Validation returns either:

- `NormalizedCommand`;
- `ClarificationRequest`;
- `ValidationError`.

```kotlin
@Serializable
data class NormalizedCommand(
    val intent: String,
    val slots: JsonObject,
    val originalText: String,
    val source: NluSource
)

@Serializable
data class ClarificationRequest(
    val question: String,
    val suggestions: List<String>,
    val pendingIntent: String,
    val partialSlots: JsonObject
)
```

## 11. Skill System

Skills are isolated executors. They receive normalized commands and return structured results.

```kotlin
interface Skill {
    val id: String
    val supportedIntents: Set<String>

    suspend fun execute(command: NormalizedCommand): SkillResult
}

@Serializable
data class SkillResult(
    val status: SkillStatus,
    val text: String,
    val widget: WidgetPayload? = null,
    val actionResult: String? = null
)

enum class SkillStatus {
    SUCCESS,
    CLARIFICATION_REQUIRED,
    PERMISSION_REQUIRED,
    ERROR
}
```

Initial skills:

- `TimeSkill`
- `WeatherSkill`
- `TimerSkill`
- `AlarmSkill`
- `ReminderSkill`
- `NoteSkill`
- `CalculatorSkill`
- `OpenAppSkill`
- `HelpSkill`
- `UnknownSkill`

Skills should not render UI and should not depend on Compose.

Current implementation status: all listed skills and `SkillRegistry` exist in `:core`. `AssistantEngine` dispatches normalized commands through the registry and maps `SkillResult` into `AssistantResponse`. Android effects are applied through `PlatformActions` in `ChatViewModel`, without changing response payloads or allowing Qwen to execute commands.

## 12. Android Action Strategy

### 12.1 Timer

Two allowed PoC implementations:

- in-app timer backed by coroutine/foreground notification;
- delegated system timer using Android intent.

If delegated system timer cannot be controlled in-app, `TimerCard` is passive and says:

```text
Таймер создан в системном приложении.
```

Selected PoC implementation: in-app timer payloads include `ends_at_epoch_ms`; Compose derives the countdown and pause/resume/cancel updates the same persisted card. The old external passive mode remains renderable for historical payload compatibility but is not the normal product path.

### 12.2 Alarm

Use the Android alarm intent for the PoC.

If editing/cancel is not available:

```text
Будильник создан в системном приложении.
```

Selected PoC implementation: delegate to Android `AlarmClock.ACTION_SET_ALARM`; editing remains in the system clock app.

### 12.3 Reminder

Use local store plus notification scheduling. Requires notification permission on modern Android.

If permission is missing, return `PermissionCard`.

### 12.4 Note

Store locally through `SharedPreferencesNoteStore` for the bounded PoC. Room/DataStore becomes necessary if the note collection, concurrency or migration requirements grow.

### 12.5 Calculator

Use deterministic parser/evaluator. Do not send calculator expressions to Qwen for final arithmetic unless the result is validated by local evaluator.

### 12.6 Open App

Use PackageManager to resolve app label/package. If multiple candidates match, return `OpenAppCard` with `confirmation_required`.

## 13. Compose UI Structure

Main screens:

- `MainChatScreen`
- `DebugScreen`
- `SettingsScreen`
- `WidgetPreviewScreen`

Current navigation uses Compose state and a bottom navigation bar. Jetpack Navigation Compose is optional while the app remains a single-activity PoC.

Visual source of truth for the user-facing chat is the approved Blue Reference direction in `docs/superpowers/specs/2026-07-09-ui-polish-blue-reference-design.md`, with supplied mocks under `docs/design/references/`. The main chat uses a white/light surface, centered `Assistant` top bar, compact bottom navigation, pale-blue right-aligned user bubbles, an assistant avatar, compact input controls and white bordered result widgets. Raw diagnostics belong to internal screens, not the conversation surface.

### 13.1 `MainChatScreen`

Required elements:

- chat message list;
- user text input;
- send button;
- microphone/voice button;
- recording state;
- local transcript preview;
- assistant text response;
- optional assistant widget card;
- loading state while ASR/NLU/LLM is running;
- conditional automatic scroll while the user is already near the bottom, plus a jump-to-latest affordance when they read older messages;
- an editable composer and Stop action while Qwen is generating;
- listening, recognizing, executing and local-answer preparation states.

Message components:

- `UserMessageBubble`
- `AssistantMessageBubble`
- `AssistantWidgetContainer`

Raw debug diagnostics must not be rendered as a footer in the main chat. Debug/internal builds expose them through `DebugScreen`/the `История` tab.

View state:

```kotlin
data class ChatUiState(
    val messages: List<ChatMessageUi>,
    val inputText: String,
    val isRecording: Boolean,
    val transcriptPreview: String?,
    val isProcessing: Boolean,
    val latestDebugInfo: DebugInfo?
)
```

### 13.2 `DebugScreen`

Shows latest and historical debug records:

- transcript;
- intent;
- confidence;
- raw slots;
- normalized command;
- fallback usage;
- fallback reason;
- action result;
- latency breakdown;
- model readiness;
- permission state.

DebugScreen must be hidden from normal release navigation unless explicitly enabled.

Current implementation: internal screens are enabled by `BuildConfig.DEBUG`; release navigation exposes only chat and settings.

Current implementation shows model file readiness, persisted last model operation/success/error/latency for ASR, RuBERT, Qwen and first audible TTS PCM, command diagnostics, permission/storage/memory-class status and audio-effect availability. Runtime telemetry refreshes when the relevant internal screen is entered or recomposed.

### 13.3 `SettingsScreen`

Settings:

- ASR model status;
- NLU model status;
- LLM model status;
- offline mode indicator;
- fallback threshold;
- clear chat history;
- clear notes/reminders;
- debug mode toggle in internal builds;
- optional online weather provider toggle if implemented.

Current implementation includes file/materialization readiness, persisted runtime model operation/success/error/latency, ASR model selection, automatic-speech control, fallback threshold, data clearing, device diagnostics and offline-mode information. An explicit in-app debug toggle remains follow-up work; internal navigation is currently controlled by `BuildConfig.DEBUG`.

### 13.4 `WidgetPreviewScreen`

Internal-only screen for design/product review.

Required previews:

- `WeatherCardPreview`
- `TimerCardPreview`
- `AlarmCardPreview`
- `ReminderCardPreview`
- `NoteCardPreview`
- `CalculatorCardPreview`
- `OpenAppCardPreview`
- `HelpCardPreview`
- `ClarificationCardPreview`
- `PermissionCardPreview`
- `ErrorCardPreview`
- `GenericAnswerCardPreview`

Preview data must be deterministic and not require ASR/NLU/LLM.

## 14. Widget System

```kotlin
interface AssistantWidgetRenderer {
    val type: String

    @Composable
    fun Render(payload: JsonObject)
}

class WidgetRegistry(
    private val renderers: Map<String, AssistantWidgetRenderer>
) {
    fun rendererFor(type: String): AssistantWidgetRenderer? = renderers[type]
}
```

`AssistantWidgetContainer` behavior:

- look up renderer by `widget.type`;
- render known widget;
- render `ErrorCard` or compact unknown-widget fallback for unknown types in debug/internal builds;
- avoid showing raw JSON to regular users.

Widget type constants:

- `weather_card`
- `timer_card`
- `alarm_card`
- `reminder_card`
- `note_card`
- `calculator_card`
- `open_app_card`
- `help_card`
- `clarification_card`
- `permission_card`
- `error_card`
- `generic_answer_card`

## 15. Required MVP Widgets

### 15.1 WeatherCard

Intent: `get_weather`

Payload:

```json
{
  "location": "Москва",
  "temperature_c": 21,
  "condition": "Облачно",
  "feels_like_c": 20,
  "humidity_percent": 64,
  "wind_mps": 3,
  "forecast": [
    {"time": "12:00", "temperature_c": 21, "condition": "cloudy"},
    {"time": "15:00", "temperature_c": 23, "condition": "partly_cloudy"},
    {"time": "18:00", "temperature_c": 20, "condition": "rain"}
  ],
  "source": "cache | mock | online",
  "updated_at": "2026-07-09T12:00:00+03:00"
}
```

UI:

- location;
- current temperature;
- condition icon;
- feels like;
- humidity;
- wind;
- small hourly forecast row;
- source indicator: cached/mock/online.

Offline no-cache response:

```text
Свежую погоду офлайн узнать нельзя. Могу показать последний сохраненный прогноз, если он есть.
```

### 15.2 TimerCard

Intent: `set_timer`

Payload:

```json
{
  "timer_id": "uuid",
  "duration_seconds": 300,
  "remaining_seconds": 300,
  "label": "чай",
  "state": "running",
  "ends_at_epoch_ms": 1783584300000
}
```

UI:

- large countdown;
- label;
- progress ring or progress bar;
- pause/resume button;
- cancel button;
- passive system-timer message if delegated externally.

### 15.3 AlarmCard

Intent: `set_alarm`

Payload:

```json
{
  "alarm_id": "external_or_local_id",
  "time": "07:30",
  "date": "2026-07-10",
  "label": "будильник",
  "repeat": null,
  "state": "scheduled"
}
```

UI:

- large alarm time;
- date or relative label such as `завтра`;
- alarm label;
- scheduled status;
- edit/cancel if supported;
- passive system-alarm message if delegated externally.

### 15.4 ReminderCard

Intent: `create_reminder`

Payload:

```json
{
  "reminder_id": "uuid",
  "text": "проверить духовку",
  "datetime": "2026-07-10T09:00:00+03:00",
  "state": "scheduled"
}
```

UI:

- reminder text;
- date/time;
- status;
- complete button;
- delete/cancel button.

### 15.5 NoteCard

Intent: `create_note`

Payload:

```json
{
  "note_id": "uuid",
  "text": "купить молоко",
  "created_at": "2026-07-09T12:00:00+03:00"
}
```

UI:

- note text;
- created time;
- copy button;
- edit button;
- delete button.

### 15.6 CalculatorCard

Intent: `calculate`

Payload:

```json
{
  "expression": "125 * 37",
  "display_expression": "125 × 37",
  "result": "4625"
}
```

UI:

- expression;
- large result;
- copy button;
- optional calculation steps later.

### 15.7 OpenAppCard

Intent: `open_app`

Payload:

```json
{
  "app_name": "Telegram",
  "package_name": "org.telegram.messenger",
  "state": "opened"
}
```

UI:

- app icon if available;
- app name;
- status;
- open button;
- choose alternative if multiple apps matched.

### 15.8 HelpCard

Intent: `help`

Payload:

```json
{
  "sections": [
    {
      "title": "Время и будильники",
      "examples": [
        "Сколько времени?",
        "Поставь таймер на 5 минут",
        "Разбуди меня завтра в 7:30"
      ]
    },
    {
      "title": "Заметки и напоминания",
      "examples": [
        "Запиши заметку купить молоко",
        "Напомни через час проверить духовку"
      ]
    }
  ]
}
```

UI:

- categorized command examples;
- tappable examples that insert text into chat input.

### 15.9 ClarificationCard

Payload:

```json
{
  "question": "На какое время поставить будильник?",
  "suggestions": [
    "На 7:30",
    "Завтра в 8:00",
    "Отмена"
  ],
  "pending_intent": "set_alarm"
}
```

UI:

- question text;
- suggestion chips;
- cancel option.

### 15.10 PermissionCard

Payload:

```json
{
  "permission": "POST_NOTIFICATIONS",
  "reason": "Чтобы создавать напоминания, нужно разрешение на уведомления.",
  "action": "request_permission"
}
```

UI:

- permission explanation;
- allow button;
- not now button.

### 15.11 ErrorCard

Payload:

```json
{
  "title": "Не получилось выполнить команду",
  "message": "Я понял команду, но не смог создать будильник.",
  "recoverable": true,
  "suggestions": [
    "Попробовать снова",
    "Открыть системный будильник"
  ]
}
```

UI:

- clear failure message;
- optional recovery actions.

### 15.12 GenericAnswerCard

Payload:

```json
{
  "title": null,
  "answer": "Короткий текстовый ответ модели.",
  "source": "local_llm"
}
```

UI:

- clean text block;
- local model indicator in debug/internal builds.

## 16. State Management

Current UI state stack:

- `ChatViewModel` owns `ChatUiState`;
- `AssistantEngine` is injected into `ChatViewModel`;
- `AndroidAudioRecorder` owns `AudioRecord` capture and WAV creation;
- `ChatViewModel` coordinates async transcription, assistant processing, streaming message updates and widget actions;
- `PlatformActions` maps Android-facing widget actions to platform adapters.

Dedicated `VoiceInputController` and `WidgetActionDispatcher` types may be extracted later if `ChatViewModel` grows further; their behavioral boundaries are already explicit.

Chat messages:

```kotlin
sealed interface ChatMessageUi {
    val id: String
    val createdAt: Instant
}

data class UserChatMessageUi(
    override val id: String,
    override val createdAt: Instant,
    val text: String,
    val source: InputSource
) : ChatMessageUi

data class AssistantChatMessageUi(
    override val id: String,
    override val createdAt: Instant,
    val text: String,
    val widget: WidgetPayload?,
    val debug: DebugInfo?
) : ChatMessageUi
```

Widget actions should not mutate payload JSON directly. They dispatch explicit events:

- `PauseTimer(timerId)`
- `ResumeTimer(timerId)`
- `CancelTimer(timerId)`
- `CompleteReminder(reminderId)`
- `DeleteReminder(reminderId)`
- `CopyNote(noteId)`
- `EditNote(noteId)`
- `OpenApp(packageName)`
- `ApplyClarificationSuggestion(text)`
- `RequestPermission(permission)`

## 17. Persistence

Selected PoC persistence:

- chat history: `SharedPreferencesChatHistoryStore`, bounded to the latest 100 messages;
- notes: `SharedPreferencesNoteStore` with serialized records;
- reminders: `SharedPreferencesReminderStore` plus `AlarmManager`/notification scheduling;
- timer state: persisted in widget payloads and resumed from `ends_at_epoch_ms`; alarm state is owned by the Android system app after delegation;
- debug logs: bounded in-memory list;
- weather: `CachingWeatherProvider` around the current mock upstream, with explicit mock/cache source and no-cache offline error behavior; a production online provider is not implemented.

SharedPreferences is accepted for this bounded internal PoC. Migrate to Room/DataStore before adding larger collections, concurrent updates or production migrations.

No sensitive audio or transcripts should be persisted by default unless a debug setting explicitly enables export.

## 18. Permissions

Expected permissions:

- `RECORD_AUDIO` for voice recording;
- `POST_NOTIFICATIONS` for reminders/timers on modern Android;
- exact alarm permission only if exact alarms are implemented;
- package visibility queries if app lookup requires them.

Permission failures are product states, not crashes. They should return `PermissionCard`.

## 19. Model Asset Strategy

### 19.1 Local ASR

The persisted Settings catalog currently exposes:

- `Whisper Base Q5_1` — stable selectable fallback, multilingual, 57 MiB;
- `Zipformer RU INT8` — approximately 27 MiB Russian offline candidate with fast final decoding;
- `T-one RU Streaming` — default 138 MiB Russian streaming CTC backend that emits partial transcripts while recording.

Whisper uses whisper.cpp; Zipformer and T-one use the 16 KB-compatible sherpa-onnx Android runtime. Assets are materialized into app-private files on `Dispatchers.IO`. The selected backend is created only by `AudioTranscriberFactory`. Native weights/recognizers are warmed and reused instead of loaded for each message.

Streaming partials update transcript preview only. `Stop` finalizes the same ASR session in the background, appends one final user message and then invokes RuBERT. Partial text can never trigger an intent or Android action.

The app must show model readiness in Settings/Debug:

- model file found;
- model load success/failure;
- last transcription latency;
- last error.

Current status: model selection persistence, background readiness/materialization, command-level ASR latency/error mapping and persisted last-transcription success/error/latency are implemented. Settings and Debug read the same runtime telemetry store. T-one is the product default; an existing explicit user selection remains persisted.

### 19.2 RuBERT-tiny2 ONNX

Training pipeline exports ONNX model and tokenizer assets.

Android loads:

- ONNX model;
- tokenizer vocab/config;
- intent label map;
- slot label map;
- normalization metadata version.

Selected distribution: generated bundle under `models/generated/rubert/`, staged for internal/device tests at `/data/local/tmp/offline-assistant-rubert`, then copied into app-private storage. The ONNX export exposes both `intent_logits` and `slot_logits`.

### 19.3 Qwen2.5 0.5B Instruct GGUF

Qwen GGUF is loaded by llama.cpp. The app must handle:

- missing model;
- insufficient memory;
- timeout;
- accidental JSON/think-tag output;
- user-visible fallback failure through `ErrorCard` or `GenericAnswerCard`.

Selected distribution: Q4_K_M GGUF under `models/external/qwen2.5-0.5b-instruct-gguf/`, staged at `/data/local/tmp/offline-assistant-qwen.gguf`, then copied into app-private storage. The large GGUF is not bundled into the APK.

Current status: missing-model and native exceptions map to user-visible errors, repeated calls are stable, production background warm-up creates a persistent model plus `32768` context, and warm-up/generation/first-visible-token telemetry is persisted. Native generation has cancellation, a 90-second timeout and sentence/character stop conditions. The 12-case real-device quality/repeatability gate passes; warmed visible TTFT measured `1.32-4.08` seconds (median `2.07` seconds), while complete-answer latency remains length-dependent at roughly `5-29` seconds.

### 19.4 Silero v5.5 RU Xenia

The external source manifest and generated Android bundle live under `models/external/silero-v5_5-ru-xenia/`. The 136,721,465-byte bundle remains outside the APK and is staged at `/data/local/tmp/offline-assistant-silero` before being copied into app-private storage.

The original package is approximately 139 MiB and contains Python text processing plus TorchScript acoustic, accent and homograph components. Distribution must include attribution and the public non-commercial license restriction. A commercial build requires separate licensing or an approved replacement model.

The reproducible exporter reconstructs the packaged TorchScript attention blocks as eager inference modules and emits stock-ORT graphs for duration/pitch, acoustic/Vocos, accentor and homosolver. The int8 homosolver embedding is gathered before dequantization so its graph is 38.6 MB instead of 117 MB. Vocos complex ISTFT is implemented outside ONNX with JTransforms 3.2 using the model's exact 2400-sample window and 600-sample hop.

Host ONNX Runtime parity passes six variable-length Russian fixtures: durations are exact, maximum final PCM difference is `2.24e-4`, accentor stress/`ё` argmax matches, and homosolver maximum logit difference is `2.4e-6`. The Kotlin ISTFT additionally matches an 85,200-sample PyTorch reference within `5e-5`. The Kotlin Basic/WordPiece tokenizer and full homograph-to-accent pipeline match nine fixed Silero linguistic fixtures, including multiple homographs, `ё` restoration and hyphenated words.

The reproducible command is `python3 tools/tts/silero_xenia_export_probe.py --probe-onnx --require-export --publish-dir models/external/silero-v5_5-ru-xenia/android-bundle`; disposable evidence remains under `build/tts-export-spike/`. Android neural inference, cached sessions, cancellation, PCM playback and readiness contracts are implemented and enabled. On Pixel 10, `SileroNativeSmokeTest` measured `564 ms` warm-up, `527 ms` first synthesis, `213-302 ms` repeated synthesis, maximum real-time factor `0.30` and `147,732 KB` incremental PSS. The real chat intent test and two sequential Qwen answer-plus-synthesis runs pass.

## 20. Training Pipeline

Training deliverables live under `training/`:

```text
training/
  data/
    synthetic_intents.jsonl
    eval_set.jsonl
  scripts/
    generate_synthetic_dataset.py
    train_rubert_tiny2.py
    export_onnx.py
    evaluate_nlu.py
  README.md
```

Dataset record shape:

```json
{
  "text": "Поставь таймер на 5 минут",
  "intent": "set_timer",
  "slots": [
    {"name": "duration", "value": "5 минут", "start": 18, "end": 25}
  ]
}
```

Evaluation must report:

- intent accuracy;
- macro F1;
- slot F1;
- per-intent confusion;
- validation pass rate after normalization.

Current status: training/export, joint intent/slot outputs and aggregate evaluation are implemented. `evaluate_export.py` writes per-case JSONL plus an adjacent `*-metrics.json` containing intent accuracy, macro F1, slot precision/recall/F1, per-intent metrics, confusion matrix and validation pass rate. The 2026-07-10 host result over 12 cases is: intent accuracy `1.000`, macro F1 `1.000`, slot F1 `0.873`, validation pass rate `0.917`, exact matches `11/12`, and strict regression gate `9/9`. The remaining miss is reported rather than hidden: the note case emits a reminder-text slot label.

## 21. Error Handling

Every failure maps to a structured response:

- ASR failure -> text explaining voice was not recognized, optional retry suggestion;
- NLU low confidence -> local plain-text answer route; trusted incomplete actions -> clarification;
- accidental Qwen JSON/think-tag output -> no execution, `ErrorCard` or safe generic answer;
- missing permission -> `PermissionCard`;
- Android action failure -> `ErrorCard`;
- unknown widget type -> debug-only unknown widget fallback;
- missing model -> `ErrorCard` with settings action in internal builds.

Actions must be idempotent where possible. For example, retrying a reminder creation should not create duplicates if the command has an operation id.

Current status: note/reminder stores use stable record ids, so complete/edit/delete widget mutations are idempotent. The PoC has no automatic create-command retry and therefore does not introduce command-level operation ids; a future transport that retries requests must add and preserve one instead of deduplicating by natural-language text.

If ASR returns plausible but wrong text, the app should show the transcript and fail safely or route to the normal NLU/LLM path. It must not silently reinterpret that text through a phrase-specific fallback.

## 22. Security and Privacy

- Audio processing runs locally.
- NLU runs locally.
- Qwen local answer generation runs locally.
- No cloud is required for core ASR/NLU/LLM behavior.
- No API keys are embedded in source or APK.
- Debug exports must not be enabled by default.
- Raw audio should not be persisted by default.
- RuBERT action output is not trusted until deterministic slot normalization and validation succeeds.
- Qwen output is display-only text and is never eligible for Android action execution.

## 23. Performance Targets

Initial PoC targets on a modern Android phone:

- text command to response with RuBERT path: under 500 ms after model warmup;
- voice transcription for short commands: target under 3 seconds for short utterances;
- Qwen local answer: target under 10 seconds for a short answer after warm-up;
- UI remains responsive during ASR/NLU/LLM work;
- all model work runs off the main thread.

Use coroutines and dedicated dispatchers:

- `Dispatchers.Default` for CPU normalization/parsing;
- native worker thread for whisper/llama as appropriate;
- avoid blocking Compose state updates.

Current measured status on the connected Pixel:

- action and widget paths remain responsive and run model work off the main thread;
- repeated Qwen calls no longer crash or duplicate the final message;
- the current 12-question Qwen quality artifact passes all cases; optimized warmed visible TTFT is `1.32-4.08` seconds with median `2.07` seconds, while complete-answer latency remains roughly `5-29` seconds depending on answer length;
- the connected RuBERT slot-only artifact passes `8/8` cases;
- Base Q5_1 and Small Q5_1 both pass the connected Whisper manifest at `4/4` expected intent/widget cases. Base measured `3.49-3.83` seconds (median `3.63` seconds); selected Small measured `8.31-15.41` seconds (median `13.62` seconds, mean `12.74` seconds), about `3.5x` slower by median. Small peak process memory during the run was approximately `450 MB PSS` / `537 MB RSS`;
- after caching the active Whisper context, warmed Base on the timer fixture measured median `2.223` seconds with 4 threads. Six threads measured `3.013` seconds and eight measured `11.819` seconds, so the production CPU setting remains 4;
- Zipformer RU INT8 decoded the four fixed WAV files in `38-134 ms` after warm-up and produced semantically correct Russian text for weather, timer, calculator and note. The word-numeral gap is now handled by a reusable Russian cardinal-number/spoken-time ITN component; full date/ordinal ITN remains follow-up work;
- T-one RU native streaming emits partial and final Russian transcripts on Pixel and is now the product default. Broader noisy-speech command evaluation remains required before production release;
- Whisper Small remains documented as a historical comparison but is no longer bundled or selectable: its `181 MiB` cost and `13.62`-second median were not justified;
- an opt-in ggml-vulkan build was compiled for API 28, packaged with 16 KB alignment and tested on Pixel 10. The first short-command result took about 25 seconds and failed transcript correctness, so Vulkan is explicitly disabled in the production build;
- the new host-tested native policy keeps the `8192`-token capacity ceiling while requesting 2-4 sentences, stopping at semantic boundaries, applying a 900-character emergency cap and ending generation after 90 seconds;
- cached RuBERT ONNX inference preserves action correctness across CPU/XNNPACK/NNAPI; Pixel medians were CPU/2 `7.04 ms`, CPU/4 `9.71 ms`, CPU/6 `7.00 ms`, XNNPACK `12.74 ms`, NNAPI `10.48 ms`, so production uses CPU/2;
- minified release startup with the generated Baseline Profile measured `341.26 ms` median versus `347.71 ms` without compilation in five cold iterations; chat frame and Perfetto artifacts are retained under `benchmark/build/outputs`;
- therefore the Qwen `<10 seconds` target is not yet met consistently and remains a connected stabilization requirement rather than an achieved characteristic.

## 24. Testing Strategy

### 24.1 Unit Tests

Cover:

- intent schema validation;
- slot normalization;
- date/time parsing;
- calculator evaluation;
- Qwen answer prompt and output sanitizer;
- skill result widget payloads;
- widget registry lookup;
- error mapping.

### 24.2 Android Instrumented Tests

Cover:

- Compose rendering of each widget with sample payload;
- permission flow UI state;
- app launcher candidate matching where feasible;
- selected SharedPreferences persistence and reminder scheduling;

### 24.3 Preview Tests

WidgetPreviewScreen must render all required cards from deterministic sample payloads.

### 24.4 Evaluation Set

Fixed command set:

- simple supported commands;
- ambiguous commands;
- incomplete commands;
- noisy speech-style phrases;
- unsupported requests;
- local-answer routing candidates.

Milestone 6 acceptance should include latency logs and model outputs for this set.

## 25. Milestones

Status summary as of 2026-07-10:

- Milestone 1 — complete;
- Milestone 2 — complete, including concrete skills and registry dispatch;
- Milestone 3 — complete, including connected slot/command evaluation;
- Milestone 4 — complete, including recorded live-human microphone acceptance;
- Milestone 5 — complete functionally with connected quality/repeatability and latency evidence;
- Milestone 6 — complete; performance targets that were not met remain documented PoC constraints.

### Milestone 1: Android Chat Shell + Widget Framework

Status: complete.

Deliver:

- new Kotlin Compose Android project;
- Compose chat UI;
- message history;
- text input;
- send button;
- microphone placeholder;
- recording state placeholder;
- assistant response bubble;
- `AssistantResponse` contract;
- `WidgetPayload` contract;
- widget registry;
- `AssistantWidgetContainer`;
- fake assistant response path;
- `WidgetPreviewScreen`;
- fake `TimerCard`, `WeatherCard`, `CalculatorCard` rendering.

Acceptance:

- user can send text;
- assistant can return text + widget payload;
- UI renders at least fake `TimerCard`, `WeatherCard`, `CalculatorCard`;
- widget previews work.

### Milestone 2: Text-Only Assistant Engine

Status: complete. `Skill`/`SkillResult`, all MVP concrete skills and `SkillRegistry` live in `:core`; `AssistantEngine` dispatches normalized commands through the registry.

Deliver:

- intent schema;
- NLU stub or first trained model adapter;
- slot normalizers;
- `Skill` interface;
- `SkillResult` with widget support;
- `TimeSkill`;
- `TimerSkill`;
- `AlarmSkill`;
- `CalculatorSkill`;
- `NoteSkill`;
- `ReminderSkill`;
- `HelpSkill`;
- `UnknownSkill`.

Acceptance:

- text commands produce structured responses;
- supported skills return correct widgets;
- incomplete commands return `ClarificationCard`;
- failed commands return `ErrorCard`.

### Milestone 3: NLU Training Pipeline

Status: complete. ONNX intent+slot export, Android CPU inference, aggregate metrics and connected slot/command gates are implemented and verified; the strict slot-only Pixel evaluation passes `8/8`.

Deliver:

- dataset format;
- synthetic dataset;
- RuBERT-tiny2 fine-tuning for intent + slots;
- ONNX export;
- Android ONNX Runtime integration;
- debug display for intent/slots/confidence.

Acceptance:

- trained model runs locally on Android CPU;
- NLU output drives real skill execution;
- debug screen shows model output.

### Milestone 4: Whisper Voice Input

Status: complete. Real `AudioRecord` input, selectable local Whisper/Zipformer/T-one transcription, stable/mutable transcript preview, endpoint detection, async engine routing and widget output are present. The recorded historical live-human timer flow verifies transcript, RuBERT intent and stage latency; the current default is T-one and the current timer is an in-app controllable card.

Deliver:

- `AudioRecord` recording;
- whisper.cpp Android integration;
- local transcription;
- transcript preview;
- voice message to Assistant Engine path.

Acceptance:

- user records a voice command;
- app transcribes locally;
- recognized intent executes;
- result widget is shown.

### Milestone 5: Qwen Local Answers

Status: complete for the PoC. Plain-text prompt, sanitization, app-scope background warm-up, streaming into one message, repeated-call stability, non-execution of Qwen output, runtime telemetry and native stop/timeout safeguards are present. The connected 12-case quality gate passes; `<10 seconds` is not consistently met and remains a measured optimization target.

Deliver:

- llama.cpp Android integration;
- Qwen2.5 0.5B Instruct GGUF model loading;
- plain-text answer prompt;
- output sanitization so raw JSON/think tags are not shown;
- `GenericAnswerCard` rendering for local-model answers.

Acceptance:

- unknown/low-confidence non-action requests go to Qwen;
- Qwen answers render as streamed assistant text and/or `GenericAnswerCard`;
- Qwen is not prompted for structured command JSON;
- Qwen output never executes Android actions.

### Milestone 6: Polish and Evaluation

Status: complete. Blue Reference UI direction is implemented across the shared Compose theme, navigation, chat composer, Settings, and result widgets; debug/runtime telemetry, permission/error flows, aggregate RuBERT evaluation, connected device-smoke bundle and live-human voice evidence are verified. Qwen and batch Whisper performance targets remain optimization work.

Deliver:

- fixed command evaluation set;
- latency logging;
- widget visual polish;
- error/permission flows;
- offline mode behavior;
- internal demo build.

Acceptance:

- app supports full text + voice command flow;
- core widgets look presentable;
- no cloud is required for ASR/NLU/LLM;
- weather is clearly marked as mock/cache/online;
- debug panel makes model and action behavior inspectable.

## 26. Definition of Done

Current status: complete. Host preflight, connected device smoke, repository audit and live-human microphone evidence groups are present and verified. The 40-item machine-readable gate reports every requirement as proven.

PoC is complete when:

- Android app runs offline for ASR, NLU and LLM answers;
- user can type messages;
- user can record voice messages;
- Whisper transcribes locally on CPU;
- RuBERT-tiny2 NLU runs locally on CPU;
- Qwen2.5 0.5B Instruct local answer generation runs locally on CPU;
- app supports current time;
- app supports timer;
- app supports alarm;
- app supports reminder;
- app supports note;
- app supports calculator;
- app supports open app;
- app supports help;
- app supports unknown/clarification;
- assistant responses can include visual widgets;
- implemented widgets include `WeatherCard`;
- implemented widgets include `TimerCard`;
- implemented widgets include `AlarmCard`;
- implemented widgets include `ReminderCard`;
- implemented widgets include `NoteCard`;
- implemented widgets include `CalculatorCard`;
- implemented widgets include `OpenAppCard`;
- implemented widgets include `HelpCard`;
- implemented widgets include `ClarificationCard`;
- implemented widgets include `PermissionCard`;
- implemented widgets include `ErrorCard`;
- widget preview screen exists;
- debug panel shows transcript;
- debug panel shows intent;
- debug panel shows confidence;
- debug panel shows slots;
- debug panel shows normalized command;
- debug panel shows fallback usage;
- debug panel shows action result;
- debug panel shows latency;
- Linux CLI is not required;
- core assistant logic is separated enough for a future non-Android port.

## 27. Historical First Implementation Slice

The project originally started with Milestone 1 only.

Reasoning:

- it establishes the response/widget contract before model integration;
- it makes UI review possible through `WidgetPreviewScreen`;
- it avoids blocking on ONNX/whisper/llama native integration;
- it lets Milestone 2 plug real skills into an already validated chat/widget shell.

Milestone 1 should include enough fake assistant behavior to exercise:

- text-only response;
- timer widget response;
- weather widget response;
- calculator widget response;
- error widget response;
- clarification widget response.

## 28. Resolved Technical Decisions

The PoC uses the following decisions:

- package name: `com.offlineassistant.poc`; debug application id: `com.offlineassistant.poc.debug`;
- Gradle structure: `:core` plus `:app`, with package boundaries preserved for future extraction;
- timer: in-app persisted countdown with pause/resume/cancel; alarm: Android system intent with `com.android.alarm.permission.SET_ALARM`, `EXTRA_SKIP_UI=true` and passive result card after delegation;
- ASR distribution: bundled internal assets and app-private materialization; selection is persisted and backend creation is registry/factory driven;
- ASR runtime policy: one active cached Whisper context, reusable sherpa recognizers, 4 Whisper CPU threads, background materialization/warm-up, and final-only NLU execution;
- RuBERT/Qwen distribution: side-loaded staging for internal builds/tests and app-private materialization;
- weather: mock upstream plus local cache contract, always displaying explicit `source=mock|cache` and `updated_at`;
- internal screens: controlled by `BuildConfig.DEBUG`; no separate product flavor is required for the PoC;
- notes/reminders: SharedPreferences-backed stores for bounded PoC data;
- Qwen: answer-only Q4_K_M model, background warm-up, streamed plain text, `32768` context and `8192` generation ceiling;
- Qwen UI answer policy: 2-4 sentences, normal 600-character boundary, 900-character emergency cap and 90-second native timeout without reducing the `8192`-token capacity ceiling;
- model runtime telemetry: persisted last operation/success/error/latency shared by Whisper, RuBERT and Qwen adapters and shown in Settings/Debug;
- TTS: Silero `v5_5_ru` Xenia at 48 kHz, sentence-streamed through `AssistantSpeech`; initialized and warmed off the UI thread through `AssistantSpeechGateway`, with no legacy PyTorch Mobile/cloud fallback;
- model residency: staged after first frame, retained only on capable non-low-RAM devices, released on Android memory pressure and rewarmed on a later foreground idle period;
- release performance: R8/resource shrinking, filtered generated Startup/Baseline Profiles and Macrobenchmark startup/chat journeys;
- connected eval artifacts: emitted as tagged logcat JSONL so instrumentation APK teardown cannot erase them before host collection;
- final live-human voice recording: completed after implementation stabilization as the last acceptance step; artifacts are stored under `build/device-smoke/offline-assistant-live-voice*`.

## 29. Risks

- Native model integration and model distribution can dominate build/test time. Mitigation: keep pure contracts independently testable and use explicit model readiness/error states.
- Alternative Russian ASR models can emit word numerals where downstream skills require canonical values. Mitigation: the reusable cardinal-number/spoken-time ITN handles current numeric slots; full dates/ordinals remain an evaluation item. Phrase-specific transcript rewrites remain prohibited.
- Streaming ASR can expose unstable partial text. Mitigation: partials are preview-only and only a finalized transcript crosses the ASR/NLU boundary.
- Qwen2.5 0.5B Instruct is significantly slower than the initial target for some complete answers. Mitigation: background model/context warm-up, persistent context reuse, batch-512 prompt prefill, compact conditional prompt policy, streaming, reliable stop conditions and latency evaluation without changing its answer-only role.
- Qwen may accidentally produce JSON/think-tag text. Mitigation: plain-answer prompt, output sanitization and safe non-execution.
- Silero v5.5 originates as a Python/TorchScript package. Mitigation: the reproducible split-ONNX bundle and Kotlin frontend have host parity coverage plus completed Pixel 10 runtime acceptance.
- TTS can compete with Whisper and Qwen for memory and audio focus. Before persistent Qwen context, Pixel 10 cold-start co-residency measured about `971 MB` total PSS. Keeping the full 32K Qwen context warm reduced TTFT but raised observed idle co-residency to approximately `1.35-1.45 GB` PSS; the Pixel survived repeated Qwen plus synthesis. Mitigation remains serialized generation/synthesis, cancellation generations, transient audio focus, stop-before-microphone behavior and a broader lower-memory device matrix before production use.
- Android alarm APIs vary by device and permission state. Mitigation: support passive alarm cards when delegated externally; keep timer control in-app.
- Weather is not truly offline unless cached. Mitigation: label source clearly and provide no-cache offline message.
- Widget payloads can drift from renderers. Mitigation: typed sample payloads, preview screen, registry tests.
- Instrumentation/log volume may remove evaluation rows before host collection. Mitigation: run each RuBERT/Qwen/Whisper artifact producer separately and extract its tagged logcat JSONL immediately before starting the next instrumentation run.
- A fixed exact-case NLU gate can hide poor aggregate generalization. Mitigation: publish intent accuracy, macro F1, slot precision/recall/F1, confusion and post-normalization validation metrics over the fixed held-out set; retain the strict regression subset separately.

## 30. Spec Review Checklist

- Linux CLI is explicitly excluded.
- Core logic is separated from Android UI.
- Assistant response is structured, not string-only.
- Widget rendering is owned by Compose UI registry.
- All required MVP widgets are listed with payload and UI behavior.
- All six milestones are represented.
- Offline ASR/NLU/LLM answer requirements are represented.
- Debug panel requirements are represented.
- Weather offline limitation is represented.
- Permission/error/clarification flows are represented.
- Current module layout, model distribution and platform-action choices are explicit.
- Qwen is explicitly answer-only and cannot back up RuBERT command parsing.
- Concrete skills are registry-dispatched outside the Android UI.
- Aggregate RuBERT metrics and runtime model telemetry are explicit.
- Qwen answer capacity, semantic stopping and timeout are separate documented controls.
- Measured PoC constraints and post-DoD optimization targets are explicit rather than hidden.
