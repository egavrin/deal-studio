# Jev batch UI: device measurement, 2026-09-23

## Reproducible build and setup

- Android branch: `integration/ui-first-foundation` at base `3d3bec64b6fb8694afff64ebac179b8ad3cf9a8f`, with the local experimental changes described in `docs/ui-first-local-integration.md`.
- Streaming compiler: `da2729761ebe38c6c11c0e783a0c22d48f878675` (`egavrin/direct-deepseek-executor`). The Android DEX and `toolchain.lock` record this exact revision. DEX SHA-256: `ec2ae54e5b111794c08b03203bd71cc6795c44f7fc1e5fda6037b1cd52583b66`.
- DEAL: `f75cbd2b58fa02bfa8eab4e1e255452977528ea1`; Deal UI: `26219a63c7e3a4a8092ceebfb8bfee1dae5c19ea`; component pack: `deal-studio-dealui-pack-v19` (`788278f98e3a3226becdb297540c0a0a768136c6ca202e5c8dd7b07567c964b2`).
- Pixel 10, Android 17, ARM64, connected Wi-Fi; same physical device and connection for this run. The network was not traffic shaped or otherwise controlled. App and test APKs were updated with `adb install -r --user 0`, preserving Studio data and configured credentials.
- Each request used `StudioLivePromptDeviceHarnessTest` with `generationTimeoutMs=180000`. `FIRST_CHECKED_UI` is timestamped in the checked compiler callback; it is not an independently captured first Compose frame. Terminal success means the host accepted a fully checked DEAL/Deal UI pair. Values are from `UiFirstMetrics` and the instrumentation terminal output, in milliseconds.

## First pinned batch build

| Held-out request | First checked UI | UI route / fallback | Jev HTTP attempts | Engine time to accepted app | Terminal outcome |
| --- | ---: | --- | ---: | ---: | --- |
| Todo list, add and complete | 2,824 | batch / none | 2 | 83,211 | Runnable; 74,531 ms in DEAL generation, 4,129 ms local UI compiler, 3,761 ms Jev network |
| Reading tracker, list, progress and add form | 4,126 | batch / none | Not emitted on failure log | — | Failed after 84,380 ms (`RAW_IO`; frozen business admissions rejected) |
| Workout tracker, sessions, sets, chart and edit form | 14,803 | sequential fallback / `UNREPRESENTABLE_LAYOUT:SUMMARY_HISTORY` | 10 | 31,950 | Runnable; 15,129 ms in DEAL generation, 5,539 ms local UI compiler, 10,217 ms Jev network |
| Expense tracker, categories, totals and add form | 6,659 | sequential fallback / `UNREPRESENTABLE_LAYOUT:SUMMARY_HISTORY` | Not emitted on failure log | — | Failed after 21,011 ms (`UIF3001`) |
| Trip planner, list, itinerary and edit dialog | 3,193 | batch / none | Not emitted on failure log | — | Failed after 120,571 ms (`RAW_DEAL_REJECTED`) |

Across **all five** requests, including fallback and failed generations, the first checked callback p50 is **4,126 ms** and nearest-rank p95 is **14,803 ms**. Fallback share is **2/5 (40%)**. Full checked-pair acceptance is **2/5 (40%)**. The two accepted apps reached the engine terminal state in 31,950 and 83,211 ms; a distribution for successful apps alone would hide the three failures. This sample does **not** establish the target p50 ≤ 2 seconds or release quality. It also shows why the phone may still appear slow after the first preview: DEAL generation dominates the 83-second todo run.

## Follow-up build and visual observation

After that five-request run, a generic repair fix combined multiple incompatible SELECT variants from the same obligation into one REPAIR request. The follow-up compiler revision is `1b4222ca8002a858d08b9d205f6e72d57fe1632f`, and its embedded DEX SHA-256 is `d1538c6c2710b21c9b7687669b8fe256b964a076570ee4b991665e3794b0ec69`. A provider-free pinned-v19 test checks two incompatible `SUMMARY_HISTORY` variants and SELECT → REPAIR → LAYOUT within three attempts. This does not establish that the preceding device fallbacks had that exact cause.

The Android screen was also changed to put the checked preview above the prompt while it is generated. Before this change, a screenshot taken just after the todo `FIRST_CHECKED_UI` callback showed the input form and buttons above the fold; the preview required scrolling. After the change, a screenshot taken just after the workout callback showed the checked structure on the first screen. Its labels and values were typed `…` placeholders. **The callback measures a checked structural preview, not meaningful generated content or a runnable app.** The preview remains inert.

A single follow-up live workout run on that newer DEX reached its first checked structural preview at **5,530 ms**. The harness then timed out without a terminal app after **180,000 ms**. This exploratory run is outside the five-request distribution above. It does not demonstrate a lower fallback rate, better p50, or a quality improvement. A repeat todo run on the previous DEX also timed out after 180,000 ms, illustrating model/network variability. The Android copy and failure-log metric changes made after these captures do not change the measured compiler revision or the structure of the preview.

## Earlier sequential baseline observations

The same physical device and prompts were used for exploratory runs of the previously installed sequential build, but the old build did not emit the first-checked-preview callback timestamp. Todo reached terminal failure at 108,658 ms, reading at approximately 155,400 ms, and workout timed out at 180,000 ms. These runs are **not** a valid paired p50/p95 comparison of first verified UI: build provenance, network shaping, and old first-preview measurements are missing. No speedup percentage is claimed from them.

## Verification and remaining gate

- Offline pinned-v19 manifest test covers SELECT, composite checked groups, scoped variant REPAIR, LAYOUT replay, invalid choices, fallback and the three-attempt batch ceiling. Jev transport offline test accepts SELECT and REPAIR. The Android host batch contract reports 47 SELECT questions, 253 options and 28,830 request bytes; checked preview and freeze pass.
- `:app:testDebugUnitTest`, `:deepseek-connector:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:ktlintCheck`, `:app:detekt`, and `:app:lintDebug` passed with the pinned DEX. Twelve ARM64 tests passed across `CanonicalDealUiTouchDeviceTest`, `FrozenUiPreviewInertDeviceTest`, and `ManifestUiPreviewDeviceTest`.
- The requested `CanonicalDealToolchainDeviceTest` class is absent in this checkout, so that exact device gate cannot be recorded as passed. The five live runs expose two fallback cases and three full-generation failures. The old and new paths still need a controlled paired run with the same model, network profile, first-preview instrumentation, and a larger held-out set before the latency and quality gates can close.

Reproduction command shape (substitute the base64 encoding of each row's prompt and a lowercase evidence stem):

```bash
adb -s 59021FDCR003LL logcat -c
adb -s 59021FDCR003LL shell am instrument -w \
  -e class com.offlineassistant.app.generatedapp.StudioLivePromptDeviceHarnessTest \
  -e promptBase64 '<BASE64_PROMPT>' -e evidenceName batch-example \
  -e generationTimeoutMs 180000 \
  com.dealstudio.app.debug.test/androidx.test.runner.AndroidJUnitRunner
adb -s 59021FDCR003LL logcat -d -s UiFirstMetrics:I StudioLivePromptEvidence:I
```
