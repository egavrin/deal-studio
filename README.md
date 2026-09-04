# DEAL Studio

DEAL Studio is an Android application that generates small interactive applications from a natural
language brief. DeepSeek authors a typed DEAL behaviour program and a pure Deal UI view; the app
compiles both with a pinned production toolchain and renders the checked result with native Compose.

The former offline voice assistant has been removed from the product APK. Its complete pre-split
workspace is preserved on `egavrin/assistant-backup-2026-09-03`.

## Product Flow

```text
user brief
   |
   v
DeepSeek compiler tools
   |
   +-- create_deal_program       nominal state, actions, helpers, capabilities
   +-- apply_deal_graph_patch    batched typed function-body fills
   |
   v
production-checked app.deal
   |
   v
extracted read-only AppInterfaceV1
   |
   v
DeepSeek Deal UI compiler tool
   |
   v
checked app.dealui -> portable IR -> native Compose
   |
   v
interactive runtime / local saved-app library
   |
   +-- dedicated generated-app screen and pinned app icon
   +-- adaptive interactive home-screen widget
```

DEAL owns state and behaviour. Deal UI owns presentation and typed event bindings. There is no
generated planner file, JSON AST or application template. Compiler tools accept compact semantic
units and reject invalid updates before they become runnable.

## Current Capabilities

- DeepSeek Flash and Pro can be selected independently for behaviour and UI; Flash is the default
  for a fresh installation.
- The cloud path generates DEAL first and Deal UI second from the exact verified interface.
- Canonical applications run in a sandboxed native Compose renderer.
- Fullscreen mode uses the same runtime and preserves state when returning to Studio.
- Canonical `app.deal` and `app.dealui` can be saved with provenance and recompiled on restore.
- Saved applications have live, noninteractive previews and reopen as interactive runtimes.
- A saved canonical app can be added to the Android home screen as an app icon or an interactive,
  resizable widget. Both use the same checked DEAL handlers and source-bound durable state.
- One natural-language refinement request is routed to narrow behaviour and UI edit agents; the
  previous app remains active unless the complete revision validates.
- Generic Deal UI pack v10 includes adaptive layout, semantic text/list/stat/status components,
  controls, icons, HTTPS images, progress, navigation surfaces, overlays, clock and canvas input.
- The local llama.cpp runtime remains available for future Gemma/Qwen evaluation; local model files
  are not bundled in the APK.

The debug APK is about 26 MB. It contains the native llama runtime and the pinned DEAL toolchain, but
does not contain speech, TTS, RuBERT or assistant model assets.

## Build And Install

Prerequisites:

- JDK 17;
- Android SDK and Build Tools 37;
- an ARM64 Android device for production-toolchain device tests.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.dealstudio.app.debug/com.offlineassistant.app.DealStudioActivity
```

The application id is `com.dealstudio.app`; debug builds use `com.dealstudio.app.debug`. The Kotlin
package namespace remains temporarily unchanged.

## DeepSeek Key

Debug builds may provision the disposable development key from `local.properties` or the
`DEEPSEEK_API_KEY` Gradle property. The first launch copies it into Android Keystore-backed encrypted
storage. A user can replace or remove it from the key action in the top bar. Release builds compile
with no embedded key and require BYOK.

```properties
DEEPSEEK_API_KEY=replace-with-development-key
```

Do not add a production credential to source control.

## Validation

Host checks:

```bash
./gradlew :app:testDebugUnitTest :deepseek-connector:testDebugUnitTest
./gradlew :app:ktlintCheck :app:detekt :app:lintDebug
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```

Focused connected checks:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r \
  -e class 'com.offlineassistant.app.generatedapp.CanonicalDealToolchainDeviceTest,com.offlineassistant.app.generatedapp.CanonicalDealUiTouchDeviceTest' \
  com.dealstudio.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The product acceptance matrix is tic-tac-toe, Arkanoid, todo, weather, medication, exam, health and
chess. A scenario passes only when generation, interaction, fullscreen state, save/restore,
refinement, responsive layout, accessibility and screenshot quality all pass. Compiling alone is
not sufficient.

## Architecture

Important implementation areas:

```text
app/src/main/java/com/offlineassistant/app/DealStudioActivity.kt
app/src/main/java/com/offlineassistant/app/generatedapp/CanonicalDealProgramGraphCompiler.kt
app/src/main/java/com/offlineassistant/app/generatedapp/CanonicalGeneratedAppCompiler.kt
app/src/main/java/com/offlineassistant/app/generatedapp/CanonicalDealUiGraphCompiler.kt
app/src/main/java/com/offlineassistant/app/generatedapp/CanonicalDealUiPack.kt
app/src/main/java/com/offlineassistant/app/generatedapp/CanonicalDealUiRuntime.kt
app/src/main/java/com/offlineassistant/app/generatedapp/GeneratedAppActivity.kt
app/src/main/java/com/offlineassistant/app/generatedapp/GeneratedAppWidgetProvider.kt
app/src/main/java/com/offlineassistant/app/generatedapp/CanonicalGeneratedAppWidgetProjection.kt
app/src/main/java/com/offlineassistant/app/generatedapp/CanonicalGeneratedAppRefiner.kt
app/src/main/java/com/offlineassistant/app/generatedapp/GeneratedAppLibrary.kt
deepseek-connector/src/main/kotlin/com/offlineassistant/deepseek/DeepSeekGenerationClient.kt
```

The embedded production compiler is built by:

```bash
scripts/build_deal_android_toolchain.sh
```

The normative engineering rules are in [AGENTS.md](AGENTS.md). The compiler protocol and remaining
acceptance work are tracked in
[2026-09-03-deepseek-deal-ui-streaming-compiler.md](docs/superpowers/plans/2026-09-03-deepseek-deal-ui-streaming-compiler.md).

## Safety Boundary

Generated source is data, not trusted Android code. The app never evaluates arbitrary Kotlin,
JavaScript or native code. DEAL and Deal UI pass strict parsers, type and capability checks, bounded
resource validation and runtime smoke checks. Invalid, partial or cancelled output is not executed.
Saved applications remain inside DEAL Studio; no arbitrary APK is emitted. A pinned generated-app
icon is a shortcut into `GeneratedAppActivity`, not an independently installed package. A home-screen
widget is a bounded Android `RemoteViews` projection. Its actions enter only through a private
receiver and are resolved to the same nominal DEAL updates used by the full app.
