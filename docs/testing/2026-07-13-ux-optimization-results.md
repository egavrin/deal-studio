# Project-Wide UX Optimization Results

Date: 2026-07-13
Device: Pixel 10, Android 16

## Result

The stabilization pass completed the user-visible P0 work, staged model warm-up, memory-pressure handling, real timer/history/reminder/weather persistence, TTS playback/replay, generic numeric ITN and release profiling. Qwen remains an answer-only plain-text model; RuBERT remains the only learned action classifier.

## Release Performance

Macrobenchmark artifact:

`benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/Pixel 10 - 16/com.offlineassistant.benchmark-benchmarkData.json`

- cold startup without compilation: `347.71 ms` median, `339.68-361.12 ms`;
- cold startup with Baseline Profile: `341.26 ms` median, `313.80-350.76 ms`;
- chat action frame count: `75` median across five iterations;
- frame CPU duration: P50 `3.71 ms`, P90 `7.69 ms`, P95 `11.59 ms`, P99 `109.02 ms` in the captured chat-action run;
- all 15 Perfetto iteration traces are stored next to the benchmark JSON.

The generated profiles are filtered to application code:

- `app/src/release/generated/baselineProfiles/baseline-prof.txt`: 1,556 lines, about 184 KiB;
- `app/src/release/generated/baselineProfiles/startup-prof.txt`: 655 lines, about 76 KiB;
- release APK contains compiled `assets/dexopt/baseline.prof` and `baseline.profm`;
- minified/resource-shrunk release APK: about 259 MiB;
- debug APK: about 278 MiB.

The Baseline Profile startup median improvement in this five-iteration run is about 1.9%. The distributions overlap, so this is evidence that the profile is installed and does not regress startup, not a claim of a large startup win.

## RuBERT Runtime Matrix

Each configuration ran the same four intent cases and preserved timer, weather, note and calculator intent correctness:

| Provider | Threads | Median | Min | Max |
| --- | ---: | ---: | ---: | ---: |
| CPU | 2 | 7.04 ms | 6.51 ms | 30.56 ms |
| CPU | 4 | 9.71 ms | 5.60 ms | 31.52 ms |
| CPU | 6 | 7.00 ms | 4.80 ms | 34.53 ms |
| XNNPACK | 4 | 12.74 ms | 10.74 ms | 22.62 ms |
| NNAPI | 4 | 10.48 ms | 7.61 ms | 21.26 ms |

Production uses CPU with two intra-op threads. It matches the fastest median range while using fewer CPU workers. XNNPACK and NNAPI are retained as benchmark options, not selected production providers.

## Residency And Memory Pressure

With T-one, cached RuBERT, persistent Qwen 32K context and Silero all warmed, the fresh debug process measured about `1.73 GiB PSS` / `1.85 GiB RSS`. App-private model storage was Qwen `469 MiB`, RuBERT `112 MiB`, T-one `138 MiB` and Silero `131 MiB`.

An Android `RUNNING_LOW` trim callback reduced the live process to about `1.16 GiB PSS` / `1.28 GiB RSS` and logged release of the persistent llama context without killing the activity. Returning to foreground recreated the context in `106 ms`; the process settled near `1.65 GiB PSS` / `1.77 GiB RSS`. Thermal status remained `0` throughout this check.

The full-stack residency is acceptable for this Pixel PoC but is too large to generalize to low-memory phones. The adaptive policy therefore avoids persistent heavy runtimes on low-RAM or memory-class-below-256-MiB devices and releases them on Android memory pressure.

## Verification

- `:core:test` and `:app:testDebugUnitTest` pass, including generic Russian cardinal-number/spoken-time normalization;
- T-one trailing-silence endpoint smoke passes on Pixel;
- repeated Qwen plus TTS, manual replay and Qwen cancellation/UI responsiveness pass on Pixel;
- Baseline Profile generation passes two journeys: startup and chat/recording/streaming;
- Macrobenchmark passes cold startup with/without profile and chat frame timing;
- release builds with R8, resource shrinking and the compiled profile.
- the final debug APK was installed and launched on Pixel; no crash-buffer entries were present, all external model directories were materialized and the final UI screenshot is `build/device-screenshots/ux-optimized-final-2026-07-13.png`.
- manual replay in that installed build synthesized the welcome response in `399 ms`, reported first audible PCM at `437 ms`, acquired/released assistant audio focus and left the crash buffer empty. Automatic speech defaults to enabled on a clean install.
- streamed Qwen speech now emits exact source ranges, uses safe clauses after 28 characters when the live speech buffer is below 1,400 ms, and keeps a word-boundary fallback near 64 characters only for starting an answer without punctuation. Continuations wait for punctuation with a 180-character emergency guard. The lower first-phrase thresholds reduced the measured first-audio delay for a 45-character opening clause from 2,698 ms to 1,390 ms. Synthesis and playback use separate ordered stages with rendezvous backpressure, while one persistent streaming `AudioTrack` consumes every phrase in the response without per-chunk teardown.
- the concurrency regression test passed `5/5`. Pixel Silero smoke accepted the early clause `Солнечный свет состоит из множества разных видимых цветов,`, synthesizing it in `368 ms` into `3,362 ms` of non-silent PCM.
- adaptive phrase planning, exact-range highlighting and response-level playback passed all JVM tests. Pixel `AudioTrackPcmPlayerTest` passed `2/2`; Silero native/chat tests passed; and a real `20.204 s` Qwen UI test proved that per-message Stop cancels current speech, leaves text generation running and suppresses TTS restart from later deltas. PCM trimming remains explicitly excluded.
- With Qwen and Silero already loaded and exercised once in the same process, three Pixel 10 runs measured first visible Qwen token to first audible `AudioTrack` playback at `998`, `1029` and `1129 ms` (median `1029 ms`, mean `1052 ms`). This metric excludes Qwen request-to-first-token latency. Its start timestamp is captured before the 40 ms UI batching layer and its end is the real playback-head callback. The reproducible gate is `QwenUiSmokeTest.measuresFirstTokenToFirstAudioWithWarmModels`.

## Remaining Experiments

- full Russian date/ordinal ITN;
- opt-in noisy real-speech corpus and correctness-first NS/AGC comparison;
- Qwen immutable-prefix, KV quantization and context-size quality/latency matrix;
- sustained thermal and lower-memory device matrix.

These are explicit follow-up experiments. They are not silently enabled and are not represented as completed optimizations.
