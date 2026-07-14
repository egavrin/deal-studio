# Android 17 Migration Results

Date: 2026-07-14

## Build Baseline

- `compileSdk 37`, `targetSdk 37`, `minSdk 26`;
- Android Gradle Plugin 9.2.1 and Gradle 9.4.1;
- JDK 17, Build Tools 37.0.0, NDK 27.0.12077973 and CMake 3.22.1;
- Kotlin uses the AGP 9 built-in integration; the Compose and serialization compiler plugins remain on Kotlin 2.3.10;
- the Baseline Profile build plugin is 1.5.0-alpha07 because the stable 1.4 plugin does not support the AGP 9 DSL.

## Host Verification

- clean fast gate passed: ktlint, detekt, Android lint, app/core JVM tests, Kover verification/report and release Kotlin compilation;
- combined app/core line coverage is `2845/6063`, or `46.9240%`, against a 45% regression floor;
- clean ARM64 `assembleDebug` passed;
- the APK manifest reports platform/API 37 and `targetSdk 37`;
- Build Tools 37 `zipalign -c -P 16 -v 4` reports `Verification successful`;
- strict Gradle dependency verification passes with the committed SHA-256 metadata.

## Android 17 Runtime Smoke

Runtime: Android 17/API 37 ARM64 Google APIs emulator with 16 KB pages.

- runtime SDK: `37`;
- runtime page size: `16384` bytes;
- streamed APK installation succeeded;
- cold `MainActivity` launch completed in `2758 ms`;
- the application process remained alive and `MainActivity` was the top resumed activity after the post-launch wait;
- crash buffer and package-specific fatal/ANR scan were empty;
- targeted `MainChatScreenTest` plus `SettingsScreenTest` passed `10/10`.

The first targeted run exposed stale test assumptions: timer cancellation updates the existing card instead of appending a second bubble, and action nodes in a lazy chat list must be scrolled into view before interaction. The tests now assert the product contract and explicitly scroll to lazy-list actions. No product runtime behavior was changed for those failures.

## Android 17 Behavior Audit

- native code is packaged as APK `.so` libraries and loaded with `System.loadLibrary`; the app does not write and dynamically load executable code;
- microphone capture and assistant TTS are Activity-scoped foreground user flows, not background services;
- staged model residency and Android memory-pressure callbacks already release the persistent Qwen and TTS runtimes on constrained devices;
- 16 KB APK alignment and an actual 16 KB runtime are both covered above.

The physical Pixel was not connected during this migration run. The API 37/16 KB runtime result is complete, while a final install/launch regression on the reference Pixel remains a separate device check when it is available.
