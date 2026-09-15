# Pack v15 production-readiness evidence

`gate-status.json` is the versioned promotion ledger for Pack v15. Every gate is one of `PASS`,
`FAIL`, or `PENDING`. `promotionStatus` may become `PASS` only when every listed gate is `PASS` and
its evidence paths are recorded. The build rejects a premature promotion. Deterministic repository
tests do not turn device, cloud, visual, accessibility, or held-out measurements into a pass.

## Build isolated baseline and candidate APKs

The v14 baseline must come from a separate clean worktree at `5eb1bdd`; never switch or rewrite the
candidate checkout. The two test-only benchmark files are copied into that worktree so both variants
receive the same frozen requests and instrumentation contract. The copied harness uses reflection for
v15-only manifest fields and records them as `unavailable` on v14.

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export BASELINE_DIR=/tmp/deal-studio-v14-baseline
export CANDIDATE_DIR=/Users/egavrin/Documents/deal-studio
git -C "$CANDIDATE_DIR" worktree add --detach "$BASELINE_DIR" 5eb1bdd
mkdir -p "$BASELINE_DIR/app/src/androidTest/assets"
mkdir -p "$BASELINE_DIR/app/src/androidTest/kotlin/com/offlineassistant/app/generatedapp"
cp "$CANDIDATE_DIR/app/src/androidTest/assets/pack-v15-benchmark-v1.json" \
  "$BASELINE_DIR/app/src/androidTest/assets/"
cp "$CANDIDATE_DIR/app/src/androidTest/kotlin/com/offlineassistant/app/generatedapp/CanonicalPackV15GenerationDeviceTest.kt" \
  "$BASELINE_DIR/app/src/androidTest/kotlin/com/offlineassistant/app/generatedapp/"
(cd "$BASELINE_DIR" && ./gradlew -PDEEPSEEK_API_KEY="$DEEPSEEK_API_KEY" \
  :app:assembleDebug :app:assembleDebugAndroidTest)
(cd "$CANDIDATE_DIR" && ./gradlew -PDEEPSEEK_API_KEY="$DEEPSEEK_API_KEY" \
  :app:assembleDebug :app:assembleDebugAndroidTest)
```

The API key comes from the caller's environment and is not stored by the host runner. Verify and, if
desired, manually install the four build products before the paired run (the runner repeats `install
-r` before each measurement to make the selected variant explicit):

```bash
adb -s "$ANDROID_SERIAL" install -r "$BASELINE_DIR/app/build/outputs/apk/debug/app-debug.apk"
adb -s "$ANDROID_SERIAL" install -r "$BASELINE_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
adb -s "$ANDROID_SERIAL" install -r "$CANDIDATE_DIR/app/build/outputs/apk/debug/app-debug.apk"
adb -s "$ANDROID_SERIAL" install -r "$CANDIDATE_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
```

## Run the alternating paired benchmark

The default is five repeats. Odd repetitions run v14 then v15; even repetitions run v15 then v14.
Every named dataset case runs in both variants. A serial, all four APKs, and a caller-selected output
directory are mandatory.

```bash
"$CANDIDATE_DIR/tooling/deal-ui-pack/benchmarks/v15/run-paired-device-benchmark.sh" \
  --serial "$ANDROID_SERIAL" \
  --output "$CANDIDATE_DIR/build/pack-v15-benchmark/paired-$(date +%Y%m%d-%H%M%S)" \
  --baseline-app "$BASELINE_DIR/app/build/outputs/apk/debug/app-debug.apk" \
  --baseline-test "$BASELINE_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" \
  --candidate-app "$CANDIDATE_DIR/app/build/outputs/apk/debug/app-debug.apk" \
  --candidate-test "$CANDIDATE_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" \
  --repeats 5
```

The output contains `schedule.csv`, instrumentation output, and pulled per-run JSON/source/trace
artifacts. Individual install, generation, or pull failures are recorded and do not truncate the
remaining schedule; after all runs, the script returns nonzero if any outcome failed.
`visual-evidence.json` deliberately remains `PENDING`: the repository has no stable
benchmark navigation and screenshot API, so the host script does not manufacture visual evidence.

The frozen dataset contains five utility requests, two held-out requests, and the exact Chinese
medication regression request. For the independent stochastic gate, run
`CanonicalSurpriseSoakDeviceTest` with `-e dealStudioSoakRuns 20`; this retains every request,
canonical source pair, trace, metric row, and failure without changing the paired benchmark ledger.

## Required manual visual and accessibility evidence

For representative successful utility, held-out, and medication artifacts, retain screenshots and
the device configuration for all of the following:

- compact viewport: 320 dp wide, light theme, font scale 1.0;
- ordinary phone: 360–412 dp wide, light and dark themes, font scales 1.0 and 1.3;
- unfolded/expanded viewport: at least 673 dp wide, light and dark themes, font scale 1.0;
- one ordinary-phone light and dark pass at font scale 2.0 for clipping/reflow review;
- TalkBack labels and traversal order, 48 dp action targets, contrast, scrolling, Hero uniqueness,
  MetricGroup adaptation, ActionBar wrapping, widget projection, and card radius no greater than 8 dp.

Record physical pixel dimensions, density, resulting logical dp dimensions, theme, font scale, app
artifact digest, screenshot path, reviewer, and `PASS`/`FAIL`. Until this evidence exists, keep
`visualAccessibilityReview` and `deviceRuntime` as `PENDING`.

## Promotion rules

Update a gate to `PASS` only after its reproducible evidence is retained. Any observed product defect
sets the affected gate to `FAIL`; unavailable hardware, credentials, measurements, or manual review
remain `PENDING`. Add evidence paths to the ledger, then set `promotionStatus` to `PASS` only when
every gate is `PASS`. `generateDealUiPackSource` validates the ledger, pack/manifest coverage, bundle
metadata, and exact `toolchain.lock` version and pack digest, so stale or premature metadata blocks
the build.
