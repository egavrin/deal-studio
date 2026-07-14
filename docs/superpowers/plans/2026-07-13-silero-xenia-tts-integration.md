# Silero Xenia On-Device TTS Integration

Date: 2026-07-13
Status: runtime integration complete; optional product controls and broader device matrix remain

## Fixed Product Profile

- Model: Silero `v5_5_ru`
- Speaker: `xenia` (`speaker_id=4`)
- Output: mono float PCM, 48 kHz
- Features to preserve: automatic stress, homographs, `yo` restoration, statement/question/exclamation intonation
- License boundary: public weights are non-commercial; this path is PoC-only until separately licensed or replaced

## Completed Foundation

- `AssistantSpeech` is a pure `:core` non-blocking boundary.
- `SpeechChunker` emits completed Qwen sentences, bounds punctuation-free chunks and never repeats the final full answer.
- `ChatViewModel` sends intent results and Qwen stream/final events through one message id.
- New requests, microphone start, chat clear and ViewModel teardown cancel queued speech.
- `AssistantSpeechController` serializes synthesis/playback off the UI thread.
- `AudioTrackPcmPlayer` owns transient audio focus and float PCM playback.
- The host harness produces six fixed Russian reference WAVs and exact frontend tensor fixtures.
- Split predictor/acoustic ONNX graphs run in stock ONNX Runtime across all six variable-length fixtures.
- Accentor and int8-embedding homosolver ONNX graphs preserve stress, `ё` and homograph decisions.
- `SileroIstft` reproduces the real PyTorch overlap-add fixture within `5e-5`.
- `SileroSpeechSynthesizer` and cached `OnnxSileroAcousticInference` provide warm-up, cancellation and telemetry boundaries.
- The Kotlin Basic/WordPiece tokenizer, accent rules, exception handling and homograph resolver match nine generated linguistic fixtures.
- `OnnxSileroLinguisticInference` caches the accentor and homosolver sessions and feeds the generic Kotlin frontend.
- `SileroNativeSmokeTest` records warm-up, repeated synthesis latency, real-time factor and PSS on a staged device bundle.
- `AssistantSpeechGateway` attaches the complete runtime after background creation without rebuilding `ChatViewModel` or blocking Compose.
- The gateway is owned by an Activity-scoped ViewModel so it has the same configuration-change lifetime as `ChatViewModel`; a recreated Activity replaces the speech controller without disconnecting the retained chat runtime.
- `AudioTrackPcmPlayer` keeps one streaming track and one audio-focus lease for the complete response, reports phrase boundaries from the playback head, drains all submitted PCM before normal release, and still releases immediately on cancellation.
- Pixel 10 connected acceptance passes for native PCM, real timer chat response and two sequential Qwen answers with separate TTS synthesis.
- The external 10-file Android bundle is 136,721,465 bytes and has per-file SHA-256 metadata.

## Runtime Gate Evidence

Run:

```bash
python3 tools/tts/silero_xenia_export_probe.py --probe-onnx
```

The source `torch.package` is 145,420,684 bytes. The full graph cannot be directly exported because Vocos produces a complex spectrum and performs ISTFT inside TorchScript. Eager reconstruction removes TorchScript-only attention control flow without ATen fallback. Host ONNX Runtime reports exact durations and maximum PCM difference `2.24e-4`. Accentor decision parity and homosolver logit parity also pass.

## Execution Order

1. Completed: port the Silero Basic/WordPiece tokenizer, accentor rules, exception handling and homograph replacement to Kotlin.
2. Completed: prove exact linguistic output, `sequence`, duration/pitch coefficients and sentence type IDs against fixed fixtures, including both meanings of `замок`.
3. Completed: Pixel 10 native smoke measured `564 ms` warm-up, `527 ms` first synthesis, `213-302 ms` repeats, maximum RTF `0.30` and `147,732 KB` incremental PSS.
4. Completed: wire the real synthesizer into `AssistantSpeechController` through `AssistantSpeechGateway` and warm it in app scope.
5. Expose `Озвучивать ответы`, speed and replay/stop controls after the default path is stable.
6. Partially completed: intent and repeated Qwen streaming plus synthesis pass on Pixel; extended airplane-mode, microphone interruption, lifecycle and thermal soak remain.

## Acceptance Gates

- No cloud calls and no platform network TTS.
- Xenia reference set has no missing/repeated words and preserves question intonation and homographs.
- Intent response is spoken once.
- Qwen starts speaking after the first complete sentence and never repeats the full answer.
- The composer remains usable during synthesis and playback.
- Microphone start stops playback before recording begins.
- Repeated Qwen plus TTS requests do not crash or leak an `AudioTrack`.
- Activity recreation followed by an intent response still triggers real Silero synthesis. Device tests cover one second of submitted PCM plus two ordered half-second chunks in one response-level `AudioTrack` session.
- APK native libraries pass the Android 16 KB page-alignment audit.
- Measured TTS/Qwen/Whisper co-residency fits the target phone without process death.
