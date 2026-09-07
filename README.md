# DEAL Studio

DEAL Studio is an Android environment for generating, running, refining and saving small interactive
applications from a natural-language request. Generated behavior is written in DEAL, presentation is
written in Deal UI, and only compiler-checked canonical applications reach the native Compose
runtime.

Examples include utilities, trackers, widgets, dashboards and small touch-controlled games. The
system is intentionally general: production code must not contain branches, components or validators
specialized for acceptance scenarios.

> **Status:** internal alpha. Canonical generation, native rendering, persistence, fullscreen and
> home-screen surfaces exist. Generation reliability, repair quality, latency and visual acceptance
> are still under active validation; this is not a production release.

## Product Flow

```text
natural-language request
  -> DEAL Streaming Compiler
  -> compact compiler-owned Agent Surface
  -> selected cloud model calls DEAL construction API
  -> production-checked app.deal
  -> compiler-extracted AppInterface
  -> selected cloud model calls Deal UI construction API
  -> production-checked app.dealui
  -> checked portable UI IR
  -> native Compose renderer + bounded DEAL runtime
  -> preview, fullscreen app, saved library and home-screen widget
```

The model calls a narrow compiler API rather than submitting a JSON application plan or arbitrary
Android code. DEAL and Deal UI compilers own semantic validation and diagnostics. Studio orchestrates
provider calls, stores canonical sources, runs the accepted behavior and renders checked UI.

At runtime no LLM request is required:

```text
Compose control -- nominal action --> DEAL handler
DEAL handler -- replacement AppState --> Compose recomposition
```

## Current Capabilities

- Sequential canonical generation: DEAL behavior first, Deal UI second against the exact extracted
  AppInterface.
- Independent model selection for behavior and UI.
- DeepSeek Flash and Pro providers.
- Cerebras Qwen 27B and GPT-OSS 120B providers.
- Compiler-owned construction and scoped semantic repair through the embedded Streaming Compiler.
- Native Compose rendering from checked Deal UI IR.
- Per-application themes, adaptive layout, semantic controls, icons, charts, canvas and pointer input.
- Fullscreen execution using the same runtime session as Studio preview.
- Canonical source inspection and copy support for `app.deal` and `app.dealui`.
- Saved application library with source-bound durable state and live previews.
- Generated-app activity, launcher shortcut and bounded interactive home-screen widget projection.
- Natural-language refinement with atomic rollback to the previous runnable revision on failure.
- Generation metrics for latency, compiler rounds, repair passes and provider token usage.
- **Surprise me** generation plus internal smoke/soak scripts for varied applications.

The current component contract is generated from the tracked Deal UI pack v13:
[`tooling/deal-ui-pack/deal-studio-v13.dealui-pack`](tooling/deal-ui-pack/deal-studio-v13.dealui-pack).

## Architecture

```text
app/                    Studio shell, canonical orchestration, runtime and Compose renderer
deepseek-connector/     DeepSeek and Cerebras streaming tool-call transport
tooling/deal-android-bridge/
                        reflection/Dex adapter to pinned compiler and runtime APIs
tooling/deal-ui-pack/   versioned source-of-truth component pack
scripts/                toolchain builds, smoke runs and protocol benchmarks
artifacts/              retained internal experiment output; not product source
```

Important implementation entry points:

- `GeneratedAppStudioViewModel.kt` owns the product session state machine.
- `CanonicalGeneratedAppCompiler.kt` hosts the provider-neutral Streaming Compiler request loop.
- `CanonicalGeneratedAppRefiner.kt` applies natural-language revisions atomically.
- `CanonicalDealToolchain.kt` loads the pinned compiler bridge.
- `CanonicalDealUiRuntime.kt` maps checked Deal UI nodes to native Compose.
- `GeneratedAppActivity.kt` runs a saved application fullscreen.
- `GeneratedAppWidgetProvider.kt` projects supported canonical UI onto Android widgets.

The generated application's canonical artifacts are exactly:

```text
app.deal
app.dealui
metadata
```

AppInterface, semantic graphs, checked UI IR and runtime instances are derived data and are rebuilt
when needed.

## Build

Prerequisites:

- JDK 17;
- Android SDK and Build Tools 37;
- an ARM64 Android device for connected compiler/runtime tests;
- pinned local DEAL, Deal UI and Streaming Compiler checkouts when rebuilding the embedded toolchain.

Build the debug application:

```bash
./gradlew :app:assembleDebug
```

Install and open it:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.dealstudio.app.debug/com.offlineassistant.app.DealStudioActivity
```

The release application id is `com.dealstudio.app`; debug builds use
`com.dealstudio.app.debug`. The Kotlin package namespace is still
`com.offlineassistant` for migration compatibility.

## Provider Keys

Studio uses bring-your-own-key storage backed by Android encrypted preferences. Debug builds may
provision disposable development keys from `local.properties` or Gradle properties:

```properties
DEEPSEEK_API_KEY=replace-with-development-key
CEREBRAS_API_KEY=replace-with-development-key
```

Release builds do not embed provider credentials. Never commit real API keys, request traces
containing credentials or populated `local.properties` files.

## Pinned Compiler Toolchain

The Android compiler bridge is rebuilt with:

```bash
scripts/build_deal_android_toolchain.sh
```

Its lock records DEAL, Deal UI and Streaming Compiler revisions, component-pack identity, Java/build
inputs and the resulting DEX digest. The embedded artifact is loaded read-only and verified before
use.

The current Android runtime is a bounded interpreter for the synchronous generated DEAL subset. It
is a host implementation detail, not a second language definition. Syntax, type checking, semantic
editing and diagnostics remain owned by the upstream compilers.

## Validation

Run host checks:

```bash
./gradlew :app:testDebugUnitTest :deepseek-connector:testDebugUnitTest
./gradlew :app:ktlintCheck :app:detekt :app:lintDebug
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```

Run focused connected checks after installing both APKs:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r \
  -e class 'com.offlineassistant.app.generatedapp.CanonicalDealToolchainDeviceTest,com.offlineassistant.app.generatedapp.CanonicalDealUiTouchDeviceTest' \
  com.dealstudio.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The deterministic acceptance matrix includes medication, exam, health, todo, weather,
tic-tac-toe, Arkanoid and chess, plus rotating held-out requests. Compiler acceptance alone is not a
product pass: the result must launch, respond to touch, survive save/restore and refinement, adapt to
supported widths, and remain usable in light and dark modes.

## Safety Boundary

Generated source is untrusted data. Studio does not evaluate arbitrary Kotlin, JavaScript or native
code and does not emit arbitrary APKs. Candidates pass pinned parsers, type and capability checks
before execution. The runtime imposes step, call-depth and collection bounds. A failed or cancelled
generation keeps the previous runnable application.

Saved applications remain inside DEAL Studio. A launcher icon opens `GeneratedAppActivity`; a home
screen widget is a constrained Android projection whose actions resolve to the same nominal DEAL
handlers as the full application.

Normative engineering constraints are documented in [AGENTS.md](AGENTS.md). The external generation
engine is maintained in
[`egavrin/deal-streaming-compiler`](https://github.com/egavrin/deal-streaming-compiler).
