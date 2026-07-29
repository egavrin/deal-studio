# Offline Assistant

Android technology demo with one deliberately narrow assistant pipeline:

```text
Microphone -> T-one RU -> transcript
                         |
Text --------------------+
                         v
                  RuBERT-tiny2
             /        |          |          \
      local action  search    research    unknown
          |        Exa auto   Exa Agent   DeepSeek SSE
      local skill      \       fixed card      |
                       DeepSeek grounded -------+
                              |
              Markdown + sources + fixed cards
                         |
                  Silero Xenia TTS
```

Voice recognition, intent classification, slots, local actions, UI rendering and speech
synthesis run on device. RuBERT also distinguishes direct chat, current web search
and explicit research. Network routes run only after the user enables them and
provides independent BYOK keys.

## Core Features

- streaming Russian ASR with T-one RU and sherpa-onnx;
- local RuBERT-tiny2 intent and slot inference through ONNX Runtime;
- time, weather mock/cache, timer, alarm, reminder, note, calculator, open-app and help;
- confirmed dial, SMS/email compose, navigation, calendar insert, media, volume,
  settings and HTTPS-link actions;
- fixed Compose result cards for those commands plus clarification, permission and error;
- streaming Markdown DeepSeek answers for open-ended questions;
- primary-source-biased Exa Search retrieval followed by grounded DeepSeek synthesis;
- cancellable SSE-driven Exa Agent research with a report view and follow-up runs;
- inline citations, persisted visual source cards, source previews and related questions;
- editable local voice dictation and a continuous listen-answer-speak conversation mode;
- attributed Wikimedia Commons image results for explicit visual requests;
- local Russian TTS with Silero Xenia;
- independent encrypted DeepSeek and Exa BYOK storage through Android Keystore.

Qwen, llama.cpp, Whisper, Gemma, generated Widget DSL, AppFunctions and organizer
features are intentionally outside the current scope.

## Modules

```text
app/                  Compose UI, T-one, RuBERT runtime, Silero, Android actions
core/                 platform-neutral contracts, routing, normalization and skills
deepseek-connector/   restricted DeepSeek, Exa and Wikimedia transports
benchmark/            startup, chat, microphone and streaming macrobenchmarks
training/             narrow RuBERT training/export/evaluation pipeline
docs/                 current scope, device checklist and visual references
```

## Build

Prerequisites: JDK 17, Android SDK/Build Tools 37 and an ARM64 Android device for
connected validation.

```bash
./gradlew testDebugUnitTest :core:test :deepseek-connector:testDebugUnitTest
./gradlew ktlintCheck detekt lintDebug
./gradlew assembleDebug
```

Install:

```bash
./gradlew installDebug
```

## Model Bundles

- T-one RU is packaged under `app/src/main/assets/models/tone_ru/`.
- RuBERT is trained/exported to `models/generated/rubert/` and staged on a device as
  `/data/local/tmp/offline-assistant-rubert/`.
- Silero Xenia is exported under
  `models/external/silero-v5_5-ru-xenia/android-bundle/` and staged as
  `/data/local/tmp/offline-assistant-silero/`.

Model weights generated under `models/` are ignored by Git. The app materializes
verified staged bundles into private app storage.

Train the narrow RuBERT classifier:

```bash
python3 training/rubert/train_export.py \
  --dataset training/rubert/synthetic_intents.jsonl \
  --output-dir models/generated/rubert

python3 training/rubert/evaluate_export.py \
  --model-dir models/generated/rubert \
  --output build/rubert-host-eval.jsonl
```

## DeepSeek

Open Settings in the app, save a DeepSeek API key and enable “Сложные вопросы”.
The key is encrypted with Android Keystore. It is never stored in source, Gradle
properties, logs or chat history.

DeepSeek receives requests classified as `unknown` or outside the current local
action registry, plus a bounded window of the visible conversation. It returns
Markdown over SSE. The UI renders that Markdown while Silero receives clean speech
text. DeepSeek cannot select an action, generate command JSON or generate UI.
Explicit requests such as
“Покажи фотографии Красной площади” may independently attach attributed Wikimedia
Commons images to that answer.

## Exa Search And Research

Settings contains an independent Exa API-key field. The value is encrypted with an
Exa-specific Android Keystore alias and is never stored in source, Gradle properties,
logs or chat history.

- `web_search`: Exa `auto` retrieves up to six bounded, primary-source-biased
  highlights; DeepSeek streams a Russian answer grounded in those numbered sources.
- `web_research`: Exa Agent runs asynchronously with `low` effort, SSE progress and
  a bounded summary/findings schema. The composer is released after the run starts;
  its `ResearchCard` remains cancellable and opens a shareable report. Follow-up
  research continues through Exa `previousRunId`.

Local action intents never use Exa. Source page contents are treated as untrusted,
only validated HTTPS links are shown, and the app does not use Exa `/answer`.

## Voice UX

- Tap the microphone inside the composer to dictate. The local T-one transcript
  stays in the composer for review and editing until Send is pressed.
- Tap the waveform button to start a conversation. The app alternates between
  listening, processing and local Silero playback while keeping the normal chat,
  streamed text, fixed cards and images visible.
- Tap the microphone in the conversation dock to end the current utterance or
  interrupt speech; tap the red call button to leave conversation mode.
- While Silero is speaking, a local T-one monitor uses Android
  `VOICE_COMMUNICATION` with available AEC/NS/AGC. A meaningful ASR partial stops
  speech and becomes the next turn; manual interruption remains available.

## Architecture

The normative scope and routing rules are in
[`docs/superpowers/specs/2026-07-29-core-assistant-scope.md`](docs/superpowers/specs/2026-07-29-core-assistant-scope.md).
The measured path from the current Russian classifier to multilingual or 200+
intents is defined in
[`docs/testing/intent-model-evaluation.md`](docs/testing/intent-model-evaluation.md).
The complete MASSIVE-to-Android capability matrix and the distinction between the
default assistant role and AppFunctions agent privileges are captured in
[`docs/research/massive-android-appfunctions-reference.md`](docs/research/massive-android-appfunctions-reference.md).
Physical-device acceptance is intentionally kept separate in
[`docs/testing/core-device-acceptance.md`](docs/testing/core-device-acceptance.md).
