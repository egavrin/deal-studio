# Android Offline Assistant PoC

This file provides guidance to AI agents working in this repository.

## Repository Identity

- **Name:** Android Offline Assistant PoC
- **Purpose:** Android-only technology demo for fully local voice input, deterministic command execution, general-question answers and speech output.
- **Primary stack:** Kotlin, Jetpack Compose, C++/JNI, ONNX Runtime, llama.cpp, whisper.cpp and Python training/evaluation tools.
- **Platform:** `minSdk 26`, `compileSdk 37`, `targetSdk 37`, ARM64 Android; Pixel 10 is the measured reference device.

The current PoC Definition of Done is complete. Treat planned vNext work as proposed until it is implemented and accepted. Sources of truth:

- current product architecture: `docs/superpowers/specs/2026-07-09-android-offline-assistant-poc-design.md`;
- current measured UX baseline: `docs/testing/2026-07-13-ux-optimization-results.md`;
- approved UI direction: `docs/superpowers/specs/2026-07-09-ui-polish-blue-reference-design.md`;
- proposed vNext roadmap: `docs/superpowers/plans/2026-07-14-local-personal-operator-vnext.md`.

## Scope

This repository is an Android-only proof of concept for an offline assistant. Keep reusable assistant logic in `:core`; keep Android UI, platform adapters, JNI bindings, and model materialization in `:app`.

Do not implement or revive a Linux CLI deliverable.

## Repository Layout

```text
app/                 Android UI, ViewModels, storage, platform adapters and JNI runtimes
benchmark/           Macrobenchmark and baseline-profile generation
core/                Reusable Kotlin contracts, NLU, normalization, skills and speech planning
docs/                Specifications, plans, design references and acceptance evidence
scripts/             Host/device acceptance orchestration
training/            RuBERT data, training, export and evaluation
tools/               ASR fixture and TTS export/staging utilities
third_party/          Pinned upstream whisper.cpp submodule
models/               Ignored generated/external model bundles
```

Do not treat `core/src/**/skills` as Codex skill directories. They contain product `Skill` implementations and tests.

## Build, Test, and Run

Prerequisites are JDK 17, Android SDK 37, Build Tools 37.0.0, NDK 27.0.12077973, CMake 3.22.1, Python 3, Git LFS and `adb` for device work.

Canonical commands:

```bash
./gradlew test
./gradlew assembleDebug
./gradlew ktlintCheck detekt lintDebug
./gradlew :app:koverVerifyCi :app:koverXmlReportCi
./gradlew installDebug
./gradlew :app:connectedDebugAndroidTest
scripts/run_full_acceptance.sh --host-only
scripts/run_full_acceptance.sh
```

After cloning, run `git lfs pull` and `git submodule update --init --recursive`. External Qwen, generated RuBERT and exported Silero bundles are required for the corresponding connected tests; see `README.md` for exact paths.

Before opening a pull request, run `./gradlew ktlintCheck detekt lintDebug :app:koverVerifyCi :app:koverXmlReportCi :app:compileReleaseKotlin`, `./gradlew assembleDebug`, and the relevant Python/native policy tests. The Kover variant combines `:app` and `:core` JVM unit coverage and enforces a 45% line floor. Gradle dependency verification is strict; when intentionally changing dependencies, regenerate and review `gradle/verification-metadata.xml` rather than bypassing verification. Do not push directly to `main`; repository rules require the aggregate `PR Quality / quality` check after parallel `fast quality` and `native APK` jobs pass.

## Required Architecture

- Local ASR backends are for speech recognition only: microphone/audio file -> transcript. The current catalog contains whisper.cpp and sherpa-onnx models.
- RuBERT-tiny2 is the classifier for user commands: intent + slots for Android actions.
- Qwen is for local answers to complex/general questions only.
- Qwen must not be used as a backup command parser for RuBERT.
- Qwen must not be prompted to generate structured command JSON.
- Qwen output must render as plain assistant text, optionally with `GenericAnswerCard`.
- On-device TTS speaks only the final user-visible assistant text. Never pass widget payload JSON, debug data, transcripts, model thinking tags or prompt content to speech synthesis.
- The selected TTS profile is Silero `v5_5_ru`, speaker `xenia`, 48 kHz. Preserve its generic Russian stress, homograph and sentence-intonation pipeline; do not add phrase-specific pronunciation rewrites.
- Streamed Qwen deltas must go through `SpeechChunker`, which acts as the adaptive speech planner and returns `PlannedSpeechChunk` values with exact source offsets and boundary type. Emit strong sentence boundaries immediately. Emit safe clause boundaries (`,`, `;`, `:`, en/em dash) after 28 characters only when the estimated queued/active speech buffer is below the 1,400 ms target. Before the first spoken phrase only, use a word-boundary fallback near 64 characters when punctuation is absent; after speech starts, prefer punctuation and reserve the 180-character continuation limit for malformed/unpunctuated output. Flush only the unspoken tail at finalization; never synthesize the full final answer again after streaming chunks.
- Keep synthesis and playback as an ordered two-stage pipeline: while one PCM chunk plays, at most the next chunk may be synthesized/prefetched. Preserve the rendezvous backpressure, generation ids and Stop behavior so early speech cannot reorder, duplicate or retain unbounded PCM.
- `AssistantSpeech` calls from `ChatViewModel` must be non-blocking. Synthesis and `AudioTrack` playback belong to the Android speech controller, and TTS playback must not keep `ChatUiState.isProcessing` true or disable the composer.
- `AssistantSpeechGateway` must share the Activity `ViewModelStore` lifetime with `ChatViewModel`. Do not own or close it from a Compose `remember`/`DisposableEffect`; configuration recreation must not leave the retained chat ViewModel connected to `NoOpAssistantSpeech`.
- `AudioTrackPcmPlayer` must use one persistent `MODE_STREAM` track and one transient audio-focus lease for the complete assistant response. A blocking `write` only transfers PCM into the track buffer; release the session only after the response-end marker has drained. Track phrase start/end against the playback head and expose the current source range to Compose.
- Do not trim, crossfade or otherwise rewrite Silero PCM to hide segmentation problems. Fix phrase planning and playback continuity first; audio post-processing requires measured model-padding evidence and dedicated signal-quality tests.
- Preserve first-token-to-audio telemetry semantics: start at the first visible Qwen token callback before UI batching and stop at the first real `AudioTrack` playback-head callback. The Pixel 10 warm baseline is `998/1029/1129 ms` (median `1029 ms`); report Qwen TTFT separately.
- Phrase highlighting must use the exact `PlannedSpeechChunk` source range. Do not fake word-level timing until normalized Silero tokens are explicitly aligned back to visible source text.
- A user Stop action suppresses the remainder of the current streaming message only. It must not stop Qwen text generation, disable automatic speech globally or allow later deltas from the same message to restart playback.
- Starting a new request or microphone recording must stop TTS, flush queued audio and release audio focus before `AudioRecord` starts.
- Do not add legacy PyTorch Mobile. The selected Silero Android runtime is split ONNX Runtime graphs plus the external JTransforms ISTFT; keep every native dependency 16 KB compatible.
- Qwen adapters should implement `LocalAnswerProvider` / `StreamingLocalAnswerProvider`; the legacy `FallbackParser` bridge may wrap that answer-only contract, but it must leave `intent` null and `slots` empty.
- Android actions and action widgets must come from RuBERT/rule NLU plus slot normalization and skill execution, not from LLM-generated intents.
- Keep concrete reusable action logic behind `SkillRegistry` in `:core`. `AssistantEngine` may orchestrate normalization, routing and `SkillResult` mapping, but do not move per-intent handlers back into Android UI or into Qwen prompts.
- Widget buttons that claim to perform Android actions must route through `PlatformActions` / Android adapters. Do not leave them as local-only chat feedback unless the product copy explicitly says the action is unavailable or passive.
- Android runtime-permission failures are user-visible product states. Use `PermissionCard` for missing `RECORD_AUDIO` and `POST_NOTIFICATIONS`; do not silently skip voice recording or reminder notification behavior.
- Weather data must flow through the pure Kotlin `WeatherProvider` contract in `:core`. Keep mock/cache/online source labels in the returned widget payload; do not hard-code weather payloads in Android UI or directly inside `AssistantEngine`.

Low-confidence action commands must produce clarification/error UI and must not be repaired by Qwen. Route only requests classified as complex/general questions to Qwen, and never execute an action from Qwen output.

Persist the latest real runtime operation for Whisper, RuBERT and Qwen through `ModelRuntimeTelemetryStore`. Settings/Debug must distinguish file readiness from actual warm-up/inference/transcription/generation success, latency and error.

Do not add phrase-specific ASR correction hacks such as mapping one observed bad transcript to a command slot. Keep the raw transcript visible, let the generic NLU/slot normalizers process it, and improve the general ASR/NLU path through model choice, training/evaluation data, or language-level normalization rules that are valid beyond one captured mistake.

Persist ASR selection through `AssistantSettingsRepository` and construct the backend through `AudioTranscriberFactory`; do not branch on a model inside Compose. Streaming ASR partials are display-only. Only the finalized transcript may enter RuBERT, slot normalization, Qwen routing or skill execution.

Use `RussianInverseTextNormalizer` for language-level cardinal-number and spoken-clock normalization. Extend it by linguistic class with broad tests; do not put observed transcript strings or command-specific replacements into ASR adapters, RuBERT glue or skills. Full dates/ordinals are still an explicit follow-up, not a reason to add phrase patches.

Keep T-one endpoint detection independent of recognized command text. The energy-based trailing-silence detector may use audio timing and RMS plus sherpa's endpoint signal, but it must not inspect transcript words to decide when to stop.

Keep Qwen UI updates batched near 40 ms and propagate Stop into the native generation epoch. Never restore synchronous per-token main-thread dispatch or block the composer while generation runs. Auto-scroll may follow only while the user is near the bottom.

Model residency is staged: make ASR/RuBERT available first, then warm Qwen/Silero after the first frame on capable non-low-RAM devices. Android memory-pressure callbacks must release the persistent Qwen context and TTS runtime and rewarm only during a later foreground idle period.

The product timer is an in-app timer whose persisted payload contains `ends_at_epoch_ms`; pause/resume/cancel must update the same card. Chat history remains bounded to 100 persisted messages. Reminder scheduling must survive reboot/package replacement/timezone changes, and weather must preserve explicit `mock`/`cache` source semantics.

## UI Direction

Use the approved Blue Reference chat direction for the main Android UI. The reference mocks are stored in `docs/design/references/`, with the implementation spec in `docs/superpowers/specs/2026-07-09-ui-polish-blue-reference-design.md` and the execution checklist in `docs/superpowers/plans/2026-07-09-ui-polish-blue-reference.md`.

Keep the chat screen close to that direction: white surface, cutout-safe `Assistant` top bar without decorative no-op buttons, compact bottom navigation, pale-blue right-aligned user bubbles, assistant avatar next to assistant bubbles, compact input bar, blue action, and white bordered widget cards. The composer has one trailing circular action: microphone for an empty input, send for entered text, and stop while recording. Do not restore separate text buttons labelled `Mic` or `Send`.

Use product-facing labels in the chat and cards. Do not expose raw ISO timestamps, payload states such as `scheduled`, source ids such as `local_llm`, filesystem paths, or the English `Transcript preview:` prefix. Raw `debug:` diagnostics must stay out of the main chat and remain available through the debug/history surface. Settings should summarize model readiness and runtime state; detailed paths and adapter diagnostics belong to Debug.

## Verification

Before claiming behavior is fixed, run the smallest relevant targeted test first, then a broader check:

- Unit/core changes: `./gradlew test`
- App build: `./gradlew assembleDebug`
- Device behavior: `./gradlew :app:connectedDebugAndroidTest`

For Qwen routing changes, include tests that prove:

- action-like unknown text uses a plain answer prompt;
- prompts do not contain JSON-parser instructions;
- `FallbackKind.COMMAND` is not executed as an Android action;
- the demo complex-question flow renders `generic_answer_card`;
- repeated complex questions through the UI render separate `generic_answer_card` answers and do not crash or duplicate final output.

Keep Qwen's model capacity controls separate from UI answer policy: context is `32768`, generation ceiling is `8192`, and normal answers are requested as 2-4 sentences. Native semantic stopping, the emergency character cap and timeout live in `answer_stop_policy.h`; run `python3 scripts/test_qwen_generation_policy.py` after changing them. Do not reduce the token ceiling as a substitute for correct stopping.

Keep the native Qwen model and its `32768` context persistent behind the existing generation mutex. Background warm-up must initialize both; each request must clear llama memory metadata before prefill instead of reallocating the context. The measured CPU prompt batch is `512`. Keep the common answer prompt compact, keep model-runtime guidance generic, and put recency/safety guidance in conditional topic policies. After changing context lifecycle, batching or prompt text, rerun `QwenFirstTokenBenchmarkTest`, the full 12-case `QwenAnswerEvaluationTest`, and the repeated `QwenUiSmokeTest`.

RuBERT uses one cached optimized ONNX session and closes every input/output tensor. Pixel 10 provider evaluation selected CPU with two intra-op threads; do not switch to XNNPACK/NNAPI or increase threads without rerunning `RubertRuntimeBenchmarkTest` and preserving the action evaluation gates.

llama.cpp token pieces may split a UTF-8 code point. Never pass a raw piece to JNI `NewStringUTF`: keep `Utf8StreamDecoder` buffering across callbacks and create Java strings through UTF-16 `NewString`. The native host policy test must retain split Cyrillic, supplementary-code-point and incomplete-tail coverage.

For async chat/voice pipeline changes, include tests that prove:

- the UI appends the recognized transcript before assistant processing continues;
- streaming tokens finalize into the same assistant message instead of duplicating the full answer;
- ASR/LLM exceptions render an `ErrorCard` and clear `isProcessing`.

For ASR/NLU changes, include tests or evaluation rows that prove the behavior generalizes across paraphrases. Do not accept a fix that only makes one manually observed Whisper transcript pass.

The production ASR native build is CPU-only. `-PasrVulkan=true` is a benchmark-only path requiring explicit SPIR-V/Vulkan header properties; the Pixel 10 trial failed transcript correctness and was much slower, so never enable it by default without a new correctness-first device evaluation.

For ASR evaluation changes, keep the audio manifest-driven. Regenerate synthetic Russian fixtures with `tools/generate_asr_eval_audio.sh`; the canonical manifest is `docs/testing/audio/asr-eval-manifest.jsonl`, and connected tests consume the copied assets under `app/src/androidTest/assets/asr_eval/`. Do not replace this with phrase-specific transcript corrections.

Treat `keyword_hit` in the Whisper manifest artifact as transcript diagnostics, not the product acceptance boundary. Equivalent transcripts such as `18*3` instead of `18 умножить на 3` are valid when the generic NLU path produces the expected intent and widget. Acceptance requires non-empty ASR output plus the expected downstream intent/widget for every case; never add phrase-specific product fallbacks to satisfy the eval.

For live human microphone acceptance, use `scripts/live_voice_acceptance.sh` when a phone is connected. The run must save the recorded Mic flow plus a chat UI dump proving `Таймер` and either `Поставил таймер` or the valid passive result `Таймер создан в системном приложении.`, then a separate scrolled `История` debug UI dump proving `transcript:`, `intent: set_timer`, `source: RUBERT_TINY2`, `fallback: false`, and `latency asr`. Do not count a voice demo as accepted if it only shows a widget without the debug/latency evidence.

For final project acceptance, prefer `scripts/run_full_acceptance.sh`. It runs host preflight, then `scripts/device_smoke_test.sh`, then `scripts/live_voice_acceptance.sh`, and verifies both normal and live-voice artifact bundles. If no phone is connected, `scripts/run_full_acceptance.sh --host-only` is allowed only as a local readiness check; it does not complete the product acceptance goal. Use `scripts/run_full_acceptance.sh --wait-for-device` when you want host preflight to run now and the connected phone stages to start automatically once adb sees a device.

Use `scripts/acceptance_status.py build/device-smoke` to inspect which acceptance evidence is currently present. Treat a non-zero status as expected while phone artifacts are missing; do not mark the goal complete until it reports `"complete": true` after a real connected run.

Use `scripts/final_dod_status.py --artifact-dir build/device-smoke` for the Definition-of-Done gate matrix. It maps the spec DoD items to required evidence groups and must report `"complete": true` before the active goal can be marked complete.

For offline model acceptance, do not require `active_network_count=0`: real phones can keep a VPN or system network active. The required proof is that `scripts/device_smoke_test.sh` enables Android's OEM deny network chain and records `package_networking_com.offlineassistant.poc.debug=com.offlineassistant.poc.debug:deny` while the Whisper/RuBERT/Qwen local-model gate runs, then restores package networking.

For RuBERT export/training changes, do not stage an intent-only ONNX. The exported model must expose both `intent_logits` and `slot_logits`; run the training script export checks and `python3 training/rubert/evaluate_export.py --model-dir models/generated/rubert --output build/rubert-host-eval.jsonl`. When a phone is connected, rerun `RubertSlotEvaluationTest` plus `RubertCommandEvaluationTest` against the staged bundle.

The RuBERT evaluator must write both the per-case JSONL and adjacent `*-metrics.json`. Keep intent accuracy, macro F1, slot precision/recall/F1, per-intent results, confusion matrix, validation pass rate and the strict regression subset; do not replace aggregate reporting with a handful of exact examples.

Connected evaluation rows are exported through tagged logcat (`QwenAnswerEval`, `RubertSlotEval`, `WhisperAsrEval`). Run each producer class separately and collect its rows immediately before starting the next instrumentation run; a single combined suite can overflow logcat even before APK teardown. Do not depend on app-private files surviving instrumentation.

System alarm intents and the legacy passive timer adapter require `com.android.alarm.permission.SET_ALARM` (not `android.permission.SET_ALARM`). Use `AlarmClock.EXTRA_SKIP_UI=true` when the chat must remain foregrounded and return a passive system-action card. Do not route the normal product timer back through the system adapter.

For permission-flow changes, include tests that prove the widget payload names the requested permission and the UI requests that permission instead of hard-coding a different Android permission.

## Model Assets

Current staged model roles:

- Whisper Base Q5_1 (stable selectable fallback): `app/src/main/assets/models/whisper/whisper-base-multilingual-q5_1.bin`
- Zipformer RU INT8 (fast final Russian ASR candidate): `app/src/main/assets/models/zipformer_ru/`
- T-one RU (default streaming Russian ASR): `app/src/main/assets/models/tone_ru/`
- sherpa-onnx Android runtime: `app/libs/sherpa-onnx-static-link-onnxruntime-1.13.4.aar`
- RuBERT bundle: staged from `models/generated/rubert/` to `/data/local/tmp/offline-assistant-rubert`
- Qwen2.5 0.5B GGUF: staged from `models/external/qwen2.5-0.5b-instruct-gguf/` to `/data/local/tmp/offline-assistant-qwen.gguf`
- Silero v5.5 RU/Xenia TTS: complete split-ONNX bundle and Kotlin linguistic frontend under `models/external/silero-v5_5-ru-xenia/`; enabled through the background-created `AssistantSpeechGateway` after Pixel 10 native/chat/Qwen repeatability acceptance, while weights stay outside the APK

Do not add older/unused GGUF variants back into the repo unless explicitly requested.

## Generated And Vendored Files

- Treat `build/`, `.gradle/`, `app/.cxx/`, `app/build/`, and `core/build/` as disposable generated output.
- Keep `models/external/` and `models/generated/` outside Git. Never commit model caches, device-staged copies, APKs, local SDK paths, keystores or acceptance recordings.
- ASR assets under `app/src/main/assets/models/` and the sherpa AAR under `app/libs/` are intentional Git LFS objects. Do not replace an LFS pointer with a normal Git blob.
- Release changes must keep R8/resource shrinking enabled and preserve the filtered app-only profiles under `app/src/release/generated/baselineProfiles/`. Regenerate them with `./gradlew :app:generateBaselineProfile`; verify release startup/chat changes with `:benchmark:connectedBenchmarkReleaseAndroidTest` and inspect the Perfetto traces before claiming a latency win.
- Treat `third_party/whisper.cpp/` as a pinned Git submodule; avoid broad edits there unless the task is specifically about the native dependency. Record intentional upstream commit changes in the parent repository.

## Local Skills

There are no repo-local Codex skills. Product directories named `skills` are Kotlin source packages, not agent workflows. Use the root commands and documents above rather than introducing a repo-local skill unless a repeated, fragile repository-specific workflow clearly justifies one.
