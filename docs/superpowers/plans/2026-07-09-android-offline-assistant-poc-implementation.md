# Android Offline Assistant PoC Implementation Plan

> Status note (2026-07-10): this is the original execution plan, not the live completion ledger. Tasks 1-7 and the host-checkable parts of Tasks 8-10 are implemented. Current status and remaining connected acceptance work are authoritative in `docs/superpowers/specs/2026-07-09-android-offline-assistant-poc-design.md`, `docs/acceptance/full-spec-audit.md`, and `scripts/final_dod_status.py`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build, install, and test a Kotlin + Jetpack Compose Android offline assistant PoC from the design spec.

**Architecture:** Create a new Android Gradle project with a pure Kotlin `:core` module and an Android `:app` module. `:core` owns assistant contracts, NLU stubs/model interfaces, slot normalization, skills, response/widget payloads, and tests. `:app` owns Compose chat UI, widget renderers, debug/settings/preview screens, Android permission/action adapters, and device tests.

**Model routing rule:** Whisper is ASR only. RuBERT/rule NLU owns intent + slots for Android actions. Qwen is a local plain-text answer provider for complex/low-confidence questions only; it must not be prompted for structured command JSON and its output must never execute Android actions.

**ASR/NLU correction rule:** Do not add one-off phrase fallbacks for a single bad Whisper transcript. The generic path is raw transcript -> RuBERT/rule NLU -> slot normalization -> skill execution; recognition errors should be handled by better ASR settings/models, representative evaluation data, or general language normalization rules that apply across many utterances.

**Tech Stack:** Kotlin 2.3.x, Android Gradle Plugin 8.13.x, Jetpack Compose Material3, kotlinx.serialization, coroutines, JUnit, AndroidX test, physical Pixel via adb.

---

## Design References

Use these mock images as the visual reference for the main chat and widget cards:

- `docs/design/references/chat-widgets-triptych.png` - weather, timer/note, and local LLM answer flows in one overview.
- `docs/design/references/chat-alarm-calculator-reminder-reference.png` - alarm, calculator, and reminder card treatment.
- `docs/design/references/chat-weather-timer-reference.png` - weather and timer card treatment.

Design intent from the references:

- white phone canvas with restrained chrome;
- pale blue/purple user bubbles aligned right;
- compact assistant text bubbles aligned left;
- substantial result cards with 8-16 dp corner radius, thin border/shadow, and strong content hierarchy;
- persistent bottom input with separate circular microphone action;
- weather/timer/alarm/calculator/reminder cards should feel custom and assistant-native, not like raw generic Material cards;
- local LLM/generic answers should show a clean answer block and only expose model/source labels as subtle debug/internal metadata.

Task 5 UI implementation and later polish passes must compare the rendered app against these references before calling the visual work done.

---

## Task 1: Project Foundation

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `local.properties`
- Copy: `gradlew`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
- Create: `core/build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`

- [ ] Step 1: Copy Gradle wrapper from the local llama Android example.
- [ ] Step 2: Create Gradle settings with `:core` and `:app`.
- [ ] Step 3: Configure Android SDK path from `/opt/homebrew/share/android-commandlinetools`.
- [ ] Step 4: Add compile SDK 36, min SDK 26, Kotlin JVM target 17, Compose plugin, serialization plugin, and test dependencies.
- [ ] Step 5: Run `./gradlew projects` and confirm both modules are visible.

## Task 2: Core Contracts, Tests First

**Files:**
- Create: `core/src/test/kotlin/com/offlineassistant/core/AssistantContractsTest.kt`
- Create: `core/src/main/kotlin/com/offlineassistant/core/contracts/AssistantContracts.kt`
- Create: `core/src/main/kotlin/com/offlineassistant/core/contracts/WidgetTypes.kt`

- [ ] Step 1: Write tests proving `AssistantResponse` serializes to lower-case product JSON values and carries `WidgetPayload`.
- [ ] Step 2: Run `./gradlew :core:testDebugUnitTest` or `./gradlew :core:test` and confirm the test fails because contracts are missing.
- [ ] Step 3: Implement contracts and widget constants.
- [ ] Step 4: Re-run the core tests and confirm they pass.

## Task 3: Slot Normalization and NLU Stub, Tests First

**Files:**
- Create: `core/src/test/kotlin/com/offlineassistant/core/nlu/RuleBasedNluTest.kt`
- Create: `core/src/main/kotlin/com/offlineassistant/core/nlu/NluModels.kt`
- Create: `core/src/main/kotlin/com/offlineassistant/core/nlu/RuleBasedNlu.kt`
- Create: `core/src/main/kotlin/com/offlineassistant/core/nlu/SlotNormalizer.kt`

- [ ] Step 1: Write tests for timer, alarm, note, reminder, calculator, weather, open app, help, unknown, and clarification cases.
- [ ] Step 2: Run tests and confirm they fail because NLU classes are missing.
- [ ] Step 3: Implement deterministic offline rule-based NLU as the Milestone 2 stub path.
- [ ] Step 4: Implement slot normalization for durations, simple date/time, note/reminder text, calculator expression, and app name.
- [ ] Step 5: Re-run tests and confirm they pass.

## Task 4: Skills and Assistant Engine, Tests First

**Files:**
- Create: `core/src/test/kotlin/com/offlineassistant/core/engine/AssistantEngineTest.kt`
- Create: `core/src/main/kotlin/com/offlineassistant/core/engine/AssistantEngine.kt`
- Create: `core/src/main/kotlin/com/offlineassistant/core/skills/Skill.kt`
- Create: `core/src/main/kotlin/com/offlineassistant/core/skills/Skills.kt`
- Create: `core/src/main/kotlin/com/offlineassistant/core/platform/PlatformAdapters.kt`

- [ ] Step 1: Write tests that text commands return structured `AssistantResponse` with correct widget types for timer, weather, calculator, alarm, reminder, note, open app, help, clarification, error, and generic fallback.
- [ ] Step 2: Run tests and confirm they fail because engine/skills are missing.
- [ ] Step 3: Implement `AssistantEngine`, skill registry, and deterministic skill results.
- [ ] Step 4: Re-run tests and confirm they pass.

## Task 5: Android Compose UI and Widget Registry

**Files:**
- Create: `app/src/main/java/com/offlineassistant/app/MainActivity.kt`
- Create: `app/src/main/java/com/offlineassistant/app/ui/ChatViewModel.kt`
- Create: `app/src/main/java/com/offlineassistant/app/ui/MainChatScreen.kt`
- Create: `app/src/main/java/com/offlineassistant/app/ui/DebugScreen.kt`
- Create: `app/src/main/java/com/offlineassistant/app/ui/SettingsScreen.kt`
- Create: `app/src/main/java/com/offlineassistant/app/ui/WidgetPreviewScreen.kt`
- Create: `app/src/main/java/com/offlineassistant/app/widgets/WidgetRegistry.kt`
- Create: `app/src/main/java/com/offlineassistant/app/widgets/WidgetCards.kt`

- [ ] Step 1: Add Compose UI tests for sending text and seeing a widget container.
- [ ] Step 2: Run Android/unit UI tests and confirm they fail because UI is missing.
- [ ] Step 3: Implement `MainChatScreen`, chat bubbles, input, send button, voice placeholder, loading state, transcript preview, debug footer, and tab navigation.
- [ ] Step 4: Implement widget registry and all required widget cards.
- [ ] Step 5: Implement `WidgetPreviewScreen` with deterministic sample payloads for all cards.
- [ ] Step 6: Re-run UI tests and confirm they pass.

## Task 6: Android Platform Adapters

**Files:**
- Create: `app/src/main/java/com/offlineassistant/app/platform/AndroidPlatformAdapters.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] Step 1: Add tests or manual debug assertions for permission state and app lookup.
- [ ] Step 2: Implement Android app lookup/open adapter.
- [ ] Step 3: Implement permission request path for microphone and notifications.
- [ ] Step 4: Implement passive system timer/alarm delegation where supported.

## Task 7: Local Model Adapter Shells

**Files:**
- Create: `app/src/main/java/com/offlineassistant/app/models/ModelReadiness.kt`
- Create: `app/src/main/java/com/offlineassistant/app/asr/WhisperTranscriber.kt`
- Create: `app/src/main/java/com/offlineassistant/app/nlu/OnnxRubertNlu.kt`
- Create: `app/src/main/java/com/offlineassistant/app/llm/LlamaCppFallbackParser.kt` (legacy name; behavior must be answer-only)

- [ ] Step 1: Implement model readiness discovery for expected asset/file locations.
- [ ] Step 2: Implement safe unavailable states when model files or JNI libraries are absent.
- [ ] Step 3: Wire Settings/Debug to show model status.
- [ ] Step 4: Keep the rule-based core path active until model assets are installed.
- [ ] Step 5: Add tests proving Qwen prompts ask for plain text, never structured command JSON, and `FallbackKind.COMMAND` is ignored by action execution.

## Task 8: Build, Install, and Device Test

**Files:**
- Create: `scripts/device_smoke_test.sh`
- Create: `docs/testing/device-smoke-results.md`

Current runner: `scripts/device_smoke_test.sh` performs the host checks, writes RuBERT per-case and aggregate metrics artifacts, stages Qwen/RuBERT/audio assets under `/data/local/tmp`, denies networking for the debug package, and runs a targeted offline local-model gate for Whisper/RuBERT/Qwen. It then runs connected model, permission, platform and UI suites. Qwen/RuBERT/Whisper evaluation rows use tagged logcat and are collected immediately after their producer suite so later instrumentation teardown cannot erase them. The runner records demo video, screenshot, connectivity and logcat evidence and validates the final bundle. Local script tests guard the sequence and artifact requirements.

Live human voice acceptance: `scripts/live_voice_acceptance.sh` runs `training/rubert/evaluate_export.py` into `build/device-smoke/rubert-host-eval-live-voice.jsonl`, stages the final RuBERT bundle, reinstalls the debug APK, clears app data for an isolated run, records the manual Mic flow for `Поставь таймер на 5 минут`, pulls `offline-assistant-live-voice.mp4`, screenshot, chat UIAutomator dump, debug UIAutomator dump, and logcat into `build/device-smoke/`, fails unless the chat dump contains the timer response/widget text, automatically switches to `История` by parsing UIAutomator bounds and tapping the tab, fails unless the debug dump contains transcript, `set_timer`, `RUBERT_TINY2`, `fallback=false`, and `latency asr`, and validates those artifacts with `scripts/verify_device_smoke_artifacts.py --live-voice`. `scripts/test_live_voice_acceptance_script.py` guards the script locally.

Final acceptance entrypoint: `scripts/run_full_acceptance.sh` runs host preflight first, then `scripts/device_smoke_test.sh`, then `scripts/live_voice_acceptance.sh`, and verifies the standard plus live-voice artifact bundles. Use `scripts/run_full_acceptance.sh --host-only` for host readiness only. Host preflight writes 12 RuBERT rows plus aggregate metrics (current exact `11/12`, strict regression `9/9`) and runs the native Qwen generation-policy test.

Wait mode: `scripts/run_full_acceptance.sh --wait-for-device` runs host preflight immediately, then waits for adb to report a connected Android device before starting the phone acceptance stages. `--wait-timeout-seconds N` can override the default wait timeout.

Acceptance status: `scripts/acceptance_status.py build/device-smoke` returns JSON showing whether host preflight, device smoke, and live voice artifacts are present. It must report `"complete": true` after the final connected run before the overall goal can be considered complete.

Final DoD status: `scripts/final_dod_status.py --artifact-dir build/device-smoke` evaluates `docs/acceptance/final-dod-gates.json`, which maps the spec Definition of Done to explicit evidence groups. It is called by `scripts/run_full_acceptance.sh` after artifact verification and must also report `"complete": true`.

- [ ] Step 1: Run `./gradlew test`.
- [ ] Step 2: Run `./gradlew assembleDebug`.
- [ ] Step 3: Install APK on the connected Pixel with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] Step 4: Launch the app with `adb shell monkey -p com.offlineassistant.poc 1`.
- [ ] Step 5: Capture `adb logcat` errors and a screenshot.
- [ ] Step 6: Record device smoke results, tested commands, and remaining gaps against the full DoD.

## Task 9: Completion Audit

**Files:**
- Create: `docs/acceptance/full-spec-audit.md`

- [ ] Step 1: Re-read the design spec.
- [ ] Step 2: Build a requirement-by-requirement checklist.
- [ ] Step 3: Mark each requirement as proven, partially implemented, missing asset, blocked by missing model integration, or not yet tested.
- [ ] Step 4: Do not mark the overall goal complete unless every full DoD item has direct evidence.

## Task 10: UI Polish Against Product Mocks

**Files:**
- Modify: `app/src/main/java/com/offlineassistant/app/ui/MainChatScreen.kt`
- Modify: `app/src/main/java/com/offlineassistant/app/widgets/WidgetCards.kt`
- Modify: `app/src/main/java/com/offlineassistant/app/ui/WidgetPreviewScreen.kt`
- Reference: `docs/design/references/chat-widgets-triptych.png`
- Reference: `docs/design/references/chat-alarm-calculator-reminder-reference.png`
- Reference: `docs/design/references/chat-weather-timer-reference.png`

- [ ] Step 1: Capture current phone screenshots for chat, weather, timer, note, calculator, alarm, reminder, and local LLM answer flows.
- [ ] Step 2: Compare the rendered app against the mock references and list concrete visual gaps.
- [ ] Step 3: Polish `MainChatScreen`: white canvas, compact top bar, chat spacing, pale user bubbles, compact assistant bubbles, persistent bottom input, separate circular microphone action, and reliable auto-scroll.
- [ ] Step 4: Polish MVP widget cards so weather, timer, alarm, calculator, reminder, note, and generic answer cards look assistant-native and close to the supplied mocks instead of raw generic Material cards.
- [ ] Step 5: Update `WidgetPreviewScreen` so it can review the polished cards quickly without running ASR/NLU/LLM.
- [ ] Step 6: Verify on the physical phone with screenshots and update `docs/testing/device-smoke-results.md` with the visual QA evidence.
