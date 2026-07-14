# UI Polish Blue Reference Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the main chat and result widgets to match the approved Blue Reference mock direction.

**Architecture:** Keep all assistant data contracts and engine behavior unchanged. Add focused Compose presentation helpers inside existing UI/widget files and preserve test tags/content descriptions for automated checks.

**Tech Stack:** Kotlin, Jetpack Compose Material3, existing Android instrumentation tests.

---

## Files

- Modify `app/src/main/java/com/offlineassistant/app/ui/MainChatScreen.kt`: chat chrome, bubbles, input bar, compact loading/recording states.
- Modify `app/src/main/java/com/offlineassistant/app/widgets/WidgetCards.kt`: shared card style and concrete card layouts.
- Modify `app/src/androidTest/java/com/offlineassistant/app/MainChatScreenTest.kt`: add semantic assertions for polished chrome if needed.
- Modify `docs/acceptance/full-spec-audit.md`: record UI polish evidence after verification.
- Modify `docs/testing/device-smoke-results.md`: record screenshot/test evidence after verification.

## Task 1: Preserve UI Semantics Before Styling

- [x] Run `./gradlew :app:testDebugUnitTest --tests com.offlineassistant.app.llm.LlamaCppFallbackParserTest`.
- [x] Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.offlineassistant.app.MainChatScreenTest#chatShellMatchesReferenceNavigationChrome`.
- [x] If the connected test removes the app, reinstall it before manual verification.

## Task 2: Main Chat Blue Reference Styling

- [x] In `MainChatScreen.kt`, replace the full-screen padded column with a white background, an unframed top bar, a weighted message list, compact status area, input bar, and bottom nav.
- [x] Keep content descriptions: `Меню`, `Статус приватности`, `Записать голос`, `Отправить`.
- [x] Change user bubbles to pale blue with dark text and right alignment.
- [x] Add a small assistant avatar/sparkle next to assistant bubbles.
- [x] Hide the raw `debug: intent=...` footer from the main chat.
- [x] Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.offlineassistant.app.MainChatScreenTest#chatShellMatchesReferenceNavigationChrome,com.offlineassistant.app.MainChatScreenTest#sendingTimerCommandShowsAssistantTextAndTimerCard`.

## Task 3: Widget Card Blue Reference Styling

- [x] In `WidgetCards.kt`, update `WidgetCard` to white background, 16dp radius, thin border, and light shadow.
- [x] Restyle `WeatherCardRenderer` with large temperature, condition icon, metric row, forecast row, and source chip.
- [x] Restyle `TimerCardRenderer` with title row, circular progress approximation, large countdown, label, pause/cancel outline buttons.
- [x] Restyle `CalculatorCardRenderer`, `AlarmCardRenderer`, `ReminderCardRenderer`, and `NoteCardRenderer` with icon tiles, strong primary values, and compact action rows.
- [x] Keep existing test tags and content descriptions.
- [x] Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.offlineassistant.app.MainChatScreenTest#weatherCalculatorAndReminderCommandsRenderReferenceCards,com.offlineassistant.app.MainChatScreenTest#widgetActionButtonsShowVisibleFeedback`.

## Task 4: Device Launch And Evidence

- [x] Run `./gradlew :app:installDebug`.
- [x] Copy Qwen GGUF back into app files if connected tests removed the app.
- [x] Grant microphone and notification permissions.
- [x] Launch `com.offlineassistant.poc.debug/com.offlineassistant.app.MainActivity`.
- [x] Capture a screenshot to `build/device-screenshots/offline-assistant-blue-reference-ui-2026-07-09.png`.
- [x] Verify UIAutomator sees `Assistant`, welcome text, `Mic`, input, and `Send`, and does not see `Android App Compatibility`.

## Task 5: Docs

- [x] Update `docs/testing/device-smoke-results.md` with commands and screenshot path.
- [x] Update `docs/acceptance/full-spec-audit.md` to remove or narrow the UI polish gap if evidence is complete.

## Task 6: Browser Review Board

- [x] Add `docs/design/blue-reference-board/index.html` as a local browser board for approved direction A.
- [x] Include all supplied mock references and the primary demo scenarios: weather, timer, note/calculator, and complex local-model answer.
- [x] Add `scripts/test_design_board.py` static checks for required references, app-surface structure, and no raw main-chat debug text.
- [x] Verify with `python3 scripts/test_design_board.py`.
