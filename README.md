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
- optional Android default-assistant mode with a compact system-invoked voice session;
- attributed Wikimedia Commons image results for explicit visual requests;
- local Russian TTS with Silero Xenia;
- independent encrypted DeepSeek and Exa BYOK storage through Android Keystore.
- resumable first-run setup for privacy boundaries, model readiness, microphone,
  optional cloud keys and the system-assistant role;
- versioned local model inventory with role, delivery source, installed bytes and
  explicit required/optional readiness;
- a 30-minute bounded Exa result cache plus visible structural citation coverage;
- minified unsigned release-like AAB verification on every PR.
- an isolated internal Generated App Studio with independently selectable local
  Gemma/Qwen or cloud DeepSeek Flash/Pro generators for UI and interaction logic.

Qwen, llama.cpp and Gemma are not part of the assistant request pipeline. Their
only allowed use is the internal Generated App Studio described below. Whisper,
AppFunctions and organizer features remain outside the current scope.

## Modules

```text
app/                  Compose UI, T-one, RuBERT runtime, Silero, Android actions
app/.../generatedapp  isolated hybrid generated-app Studio and validators
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
python3 scripts/check_core_scope.py
python3 scripts/check_nlu_eval_manifest.py
python3 scripts/check_release_contract.py
./gradlew testDebugUnitTest :core:test :deepseek-connector:testDebugUnitTest
./gradlew ktlintCheck detekt lintDebug
./gradlew assembleDebug bundleRelease
```

Install:

```bash
./gradlew installDebug
```

## Model Bundles

- T-one RU is packaged under `app/src/main/assets/models/tone_ru/`.
- The production RuBERT export is packaged under
  `app/src/main/assets/models/rubert/`. `runtime-bundle.json` pins its version,
  sizes and SHA-256 digests; `scripts/verify_rubert_bundle.py` validates the bundle.
  A complete `/data/local/tmp/offline-assistant-rubert/` bundle remains an optional
  development override.
- Silero Xenia is exported under
  `models/external/silero-v5_5-ru-xenia/android-bundle/` and staged as
  `/data/local/tmp/offline-assistant-silero/`.
- Studio models are not APK assets. Internal builds expect
  `gemma-ui-q4-k-m.gguf` and `qwen-deal-app-0.5b-q4-k-m.gguf` under the app-private
  `files/models/generated-app-studio/` directory.

Generated training outputs under `models/` are ignored by Git. Production ONNX
assets use Git LFS. The app atomically materializes versioned APK assets or a
complete verified development override into private app storage.

The current catalog exposes bundled/staged lifecycle state and disk use. Remote
model downloads remain disabled until a signed catalog and trusted artifact host
are available; a partial or unverified download must never replace a working
bundle.

## Generated App Studio

The internal Studio demonstrates generation of a small interactive app through two
independently selected roles:

```text
English task
    |-- UI:    Gemma 270M | DeepSeek Flash | DeepSeek Pro
    |                       -> compact UI DSL -> strict parser -> progressive preview
    `-- Logic: Qwen 0.5B  | DeepSeek Flash | DeepSeek Pro
                            -> DEAL generated-app profile -> compiler/ABI/smoke checks
                                                                  |
                                                  generic Compose renderer + interpreter
```

Both passes start in parallel. The two choices are persisted independently, so a
run can be fully local, fully cloud or mixed. Cloud selection releases the local
session for that role; missing credentials fail closed and never trigger an implicit
local fallback. One fully revalidated repair pass on the selected logic backend is
allowed for invalid DEAL output.

There are no runtime `tic_tac_toe` or `score_duel` branches and no canned behavior
fallback. The selected models generate both artifacts. The renderer understands
generic bound primitives (`column`, `row`, `stack`, `grid2`, `section`, text,
decoration, `surface.app` and `control.button`). `GRID` modules expose cells and `onItem`.
`REALTIME_CANVAS` modules expose bounded shape arrays plus `onTick` and `onPointer`;
the generated DEAL source owns state, motion, collisions, score, lives and reset.
The generation ceilings are 512 tokens for UI DSL and 1,536 tokens for DEAL, not
64 tokens. Output still has to fit the bounded DSL/ABI and source-size gates.

This is a constrained experiment, not arbitrary native code execution. Generated
text is parsed rather than evaluated by Kotlin/JavaScript, loops and actions have
budgets, only the fixed ABI is visible to Compose, and invalid or cancelled output
never executes. The executable subset is named the **DEAL generated-app profile**;
it is not canonical DEAL v1.2.

Build the reproducible datasets and run their production-parser contract tests:

```bash
python3 training/generated_app/build_demo_dataset.py \
  --output training/generated_app/data/deal \
  --ui-output training/generated_app/data/ui
./gradlew :app:testDebugUnitTest --tests 'com.offlineassistant.app.generatedapp.*'
```

Pong, Arkanoid and tank duel are training families. Runner is held out from train as
the real-time generalization gate. Dataset sources never ship in the APK or
participate in runtime selection.

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

Open Settings in the app, save a DeepSeek API key and enable “Complex questions”.
The key is encrypted with Android Keystore. It is never stored in source, Gradle
properties, logs or chat history in production. The internal debug build currently
contains a disposable demo key so the cloud routes and Generated App Studio can be
tested immediately after installation. On first launch it is copied into Android
Keystore. Release builds always compile with an empty embedded key.

DeepSeek receives requests classified as `unknown` or outside the current local
action registry, plus a bounded window of the visible conversation. It returns
Markdown over SSE. The UI renders that Markdown while Silero receives clean speech
text. In the assistant route DeepSeek cannot select an action, generate command JSON
or generate UI. The isolated Generated App Studio has a separate explicit cloud
generation mode; its output is never trusted and passes the same UI DSL and DEAL
validators as local output.
Explicit requests such as
“Покажи фотографии Красной площади” may independently attach attributed Wikimedia
Commons images to that answer.

## Exa Search And Research

Settings contains an independent Exa API-key field. The value is encrypted with an
Exa-specific Android Keystore alias and is never stored in source, Gradle properties,
logs or chat history.

- `web_search`: Exa `auto` retrieves up to six bounded, primary-source-biased
  highlights; DeepSeek streams an English answer grounded in those numbered sources.
  Successful source sets are cached for 30 minutes, and the UI distinguishes cached
  retrieval and incomplete structural citation coverage.
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

## System Assistant

On Android devices that expose the default digital-assistant role:

1. Open Settings in the app.
2. Under “System assistant”, grant microphone access.
3. Tap “Set as system assistant” and confirm the Android role dialog or OEM
   settings fallback.
4. Invoke the configured power, home or corner assistant gesture.

The compact overlay reuses the same process-level T-one, RuBERT, local skill,
DeepSeek/Exa and Silero runtimes as the full chat. Route badges make local and
network execution visible. Optional current-screen text is off by default,
memory-only, sanitized and shown with a “SCREEN” indicator; screenshots are not
captured.

## Architecture

The normative scope and routing rules are in
[`docs/superpowers/specs/2026-07-29-core-assistant-scope.md`](docs/superpowers/specs/2026-07-29-core-assistant-scope.md).
The ordered first-run, model lifecycle, NLU, context, phone-connector, search, UI
and release program is in
[`docs/superpowers/plans/2026-07-29-productization-roadmap.md`](docs/superpowers/plans/2026-07-29-productization-roadmap.md).
The optional system-assistant product integration is researched and staged in
[`docs/superpowers/plans/2026-07-29-default-assistant-product-integration.md`](docs/superpowers/plans/2026-07-29-default-assistant-product-integration.md).
Host and OPPO CPH2765 physical-device role acceptance are complete; reproducible
checks and remaining release-matrix items are tracked in the device checklist.
The measured path from the current Russian classifier to multilingual or 200+
intents is defined in
[`docs/testing/intent-model-evaluation.md`](docs/testing/intent-model-evaluation.md).
The complete MASSIVE-to-Android capability matrix and the distinction between the
default assistant role and AppFunctions agent privileges are captured in
[`docs/research/massive-android-appfunctions-reference.md`](docs/research/massive-android-appfunctions-reference.md).
Physical-device acceptance is intentionally kept separate in
[`docs/testing/core-device-acceptance.md`](docs/testing/core-device-acceptance.md).
