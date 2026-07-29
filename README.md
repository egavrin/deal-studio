# Offline Assistant

Android technology demo with one deliberately narrow assistant pipeline:

```text
Microphone -> T-one RU -> transcript
                         |
Text --------------------+
                         v
                  RuBERT-tiny2
                    /        \
          known action       unknown
               |                |
        local skill        DeepSeek SSE
               \                /
                text + fixed card
                         |
                  Silero Xenia TTS
```

Voice recognition, intent classification, slots, local actions, UI rendering and speech
synthesis run on device. Only open-ended `unknown` requests may use DeepSeek, after the
user enables it and provides a BYOK key.

## Core Features

- streaming Russian ASR with T-one RU and sherpa-onnx;
- local RuBERT-tiny2 intent and slot inference through ONNX Runtime;
- time, weather mock/cache, timer, alarm, reminder, note, calculator, open-app and help;
- fixed Compose result cards for those commands plus clarification, permission and error;
- streaming plain-text DeepSeek answers for open-ended questions;
- local Russian TTS with Silero Xenia;
- encrypted DeepSeek BYOK storage through Android Keystore.

Qwen, llama.cpp, Whisper, Gemma, generated Widget DSL, AppFunctions and organizer
features are intentionally outside the current scope.

## Modules

```text
app/                  Compose UI, T-one, RuBERT runtime, Silero, Android actions
core/                 platform-neutral contracts, routing, normalization and skills
deepseek-connector/   restricted HTTPS/SSE plain-text answer transport
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

DeepSeek receives only requests classified as `unknown`. It returns plain text over
SSE. It cannot select an action, generate command JSON or generate UI.

## Architecture

The normative scope and routing rules are in
[`docs/superpowers/specs/2026-07-29-core-assistant-scope.md`](docs/superpowers/specs/2026-07-29-core-assistant-scope.md).
Physical-device acceptance is intentionally kept separate in
[`docs/testing/core-device-acceptance.md`](docs/testing/core-device-acceptance.md).
