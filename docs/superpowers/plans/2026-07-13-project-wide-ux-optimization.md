# Project-Wide UX Optimization Plan

Date: 2026-07-13

Status: implemented and measured on Pixel 10; remaining experiments are listed explicitly below

## Goal

Improve the perceived speed, responsiveness, reliability and clarity of the complete offline assistant flow:

`microphone -> streaming ASR -> RuBERT action or Qwen answer -> widget/text -> local TTS`

The work must be measured as user-visible latency. Optimizing an isolated model is insufficient if recording shutdown, UI updates, audio playback or model co-residency still cause pauses and crashes.

## Current Baseline

Measured on Pixel 10:

- live voice timer command: about 2.6 seconds total;
- ASR: about 2.0 seconds;
- RuBERT NLU: about 0.6 seconds;
- warmed Qwen TTFT: 1.3-4.1 seconds, about 2.1 seconds median;
- warmed first visible Qwen token to first audible `AudioTrack` frame: 998, 1029 and 1129 ms, 1029 ms median;
- complete Qwen answer: about 5-29 seconds;
- warmed Silero synthesis: about 0.2-0.3 seconds per chunk;
- first Silero synthesis: about 0.5 seconds;
- warmed model co-residency: about 1.35-1.45 GB PSS.

## Implementation Status

Completed on 2026-07-13:

- recorder finalization is asynchronous and the T-one streaming path no longer creates a full WAV by default;
- Qwen deltas are published in 40 ms batches, generation is cancellable in native code, the composer remains usable, and streamed text is finalized into one message;
- auto-scroll follows only while the user is near the bottom and exposes a jump-to-latest affordance otherwise;
- `AudioTrack` stays alive through PCM playback; TTS reports readiness/errors, first audible PCM, supports per-message replay, global auto-speech, phrase-level early start, one-chunk synthesis prefetch and a bounded PCM phrase cache;
- first-token-to-audio telemetry starts in the first visible Qwen token callback, crosses the 40 ms UI buffer through `AssistantSpeech`, and stops on the playback-head callback; the repeatable warm-model Pixel benchmark is `QwenUiSmokeTest.measuresFirstTokenToFirstAudioWithWarmModels`;
- T-one has trailing-silence endpoint detection, explicit voice pipeline states and stable/mutable partial-transcript rendering;
- T-one/RuBERT warm first, Qwen and Silero warm after the first frame on capable devices, and Android memory-pressure callbacks release heavy contexts for later foreground rewarm;
- RuBERT uses a cached ONNX session and tensors are closed deterministically. Pixel provider/thread evaluation selected CPU with two threads;
- a generic Russian cardinal-number ITN handles numeric calculator, duration and spoken-time slots without command-specific transcript rewrites;
- the app has a real persisted in-app timer, bounded persisted chat history, reminder rescheduling after reboot/time changes and cached weather fallback with explicit source;
- device diagnostics cover permissions, storage, memory class/low-RAM state and audio-effect availability;
- R8/resource shrinking, Startup/Baseline Profiles and a Macrobenchmark module are enabled for release.

Measured release results and artifact locations are recorded in `docs/testing/2026-07-13-ux-optimization-results.md`.

Remaining validated experiments, not blockers for the stabilized PoC:

- expand ITN from cardinal numbers/spoken clock times to full Russian date and ordinal normalization;
- collect an opt-in real-speech/noise corpus and compare platform noise suppression/gain control before enabling either by default;
- benchmark Qwen immutable-prefix reuse, quantized KV formats and smaller context sizes against the fixed answer-quality gate;
- run a broader lower-memory device and sustained thermal matrix. Pixel repeated Qwen/TTS/cancel tests and release Macrobenchmarks pass, but one device is not a production matrix.

## P0: Remove Visible Freezes

1. Move recorder finalization off the main thread.
   - Do not join the recorder worker, convert the complete PCM buffer or write WAV from a Compose click handler.
   - Do not create a WAV file for the normal T-one streaming path. Keep optional audio capture only for debug/evaluation.

2. Batch Qwen UI deltas.
   - Do not call the main dispatcher synchronously for every generated token.
   - Buffer deltas and publish them every 30-50 ms or at stable text boundaries.

3. Add generation cancellation.
   - Keep the composer editable while an answer is generated.
   - Turn the primary action into Stop while Qwen is active.
   - Propagate cancellation into the native token loop.

4. Make auto-scroll conditional.
   - Follow a streaming answer only while the user is already near the bottom.
   - When the user scrolls up, preserve their position and show a new-answer affordance.

5. Fix and instrument TTS playback.
   - Keep one response-level streaming `AudioTrack` alive until all submitted PCM has actually played.
   - Plan source-addressable phrases using strong/weak punctuation and the live buffered-audio estimate.
   - Highlight the exact phrase range reported by the playback head and expose per-message Stop.
   - Do not use PCM trimming as a substitute for correct segmentation or transport continuity.
   - Preserve immediate cancellation for microphone start, a new request and lifecycle shutdown.
   - Surface TTS readiness and failures in the internal debug UI.

## P1: Voice Interaction

1. Enable and tune endpoint detection/VAD for T-one.
   - Automatically finalize after a measured trailing-silence interval while preserving manual stop.
   - Measure false endpoint and missed endpoint rates on real commands.

2. Expose useful pipeline states.
   - Use user-facing states such as listening, recognizing, executing and preparing a local answer.
   - Do not expose model internals in the normal chat UI.

3. Improve partial transcript presentation.
   - Visually distinguish stable text from the mutable partial suffix.
   - Keep partial transcripts display-only; execute only the final transcript.

4. Improve ASR quality generically.
   - Verify the T-one 8/16 kHz input contract with accuracy and latency tests.
   - Evaluate available noise suppression and gain-control paths.
   - Add generic Russian inverse text normalization for numbers, dates and times.
   - Collect an opt-in, local evaluation corpus of audio, raw transcript and corrected transcript.
   - Do not add command-specific transcript rewrites or phrase fallbacks.

## P2: Model Residency and Memory

1. Stage warm-up instead of warming all heavy runtimes concurrently.
   - Make T-one and RuBERT available first.
   - Start Qwen warm-up after the first rendered frame and keep it resident on capable devices.
   - Warm Silero after Qwen or when automatic speech is enabled.

2. Add an adaptive memory policy.
   - Use device memory class and low-RAM status to choose persistent or on-demand runtimes.
   - Release Qwen context, TTS and unused ASR sessions on Android memory-pressure callbacks.
   - Rewarm during foreground idle, not during an active interaction.

3. Reduce Qwen context cost after quality validation.
   - Cache the immutable system-prompt prefix.
   - Benchmark quantized KV cache formats.
   - Use task-appropriate context sizes without lowering the normal answer limit.
   - Prevent cross-request state leakage.

## P3: NLU, Actions and TTS

1. Keep one reusable Assistant Engine and cached inference sessions.
2. Benchmark ONNX Runtime CPU, XNNPACK and NNAPI providers per model and device.
3. Benchmark RuBERT thread counts and full graph optimization without changing accuracy thresholds.
4. Start TTS at complete sentence or safe clause boundaries, adapt weak-boundary commits to the live audio buffer and never speak streamed duplicates.
5. Cache PCM for repeated fixed assistant phrases; do not introduce phrase-specific NLU behavior.
6. Provide per-response speech controls plus a global automatic-speech setting.
7. Implement an in-app timer runtime so TimerCard countdown, pause and cancel are real actions.
8. Persist bounded chat history and pending operations without retaining raw microphone audio by default.

## P4: Product Reliability

1. Persist reminders reliably across process death, reboot and timezone changes.
2. Add cached weather with explicit source and freshness; keep mock weather clearly labelled.
3. Restore chat and scroll position after process recreation.
4. Add release-safe model readiness, permission and storage diagnostics.
5. Exercise repeated Qwen, TTS, microphone and action sequences under thermal and memory pressure.

## Measurement and Release Performance

Add Macrobenchmark/Baseline Profile coverage for these critical user journeys:

- cold start to first rendered frame;
- microphone tap to active recording;
- end of speech to final transcript;
- final transcript to action widget;
- Qwen request to first visible token;
- first visible sentence to first audible PCM;
- streaming answer scroll smoothness and frame jank.

For every metric, record cold, warm and repeated values plus failures. Keep a fixed on-device acceptance manifest so optimizations cannot silently trade correctness for speed.

Release work:

- enable R8 and resource shrinking for release builds;
- add Startup and Baseline Profiles for startup, chat, recording and streaming;
- inspect Macrobenchmark traces before and after each latency-oriented change;
- keep external model sizes, runtime PSS and thermal behavior in the release report.

## Recommended Implementation Order

1. Fix TTS playback lifetime and recorder finalization.
2. Batch Qwen deltas, add Stop generation and conditional auto-scroll.
3. Add end-to-end latency metrics and Macrobenchmark journeys.
4. Add T-one endpoint detection and generic ASR quality evaluation.
5. Implement staged warm-up and Android memory-pressure handling.
6. Benchmark ONNX providers, Qwen prefix/KV optimizations and release profiles.
7. Complete persistent actions, history and offline weather caching.
