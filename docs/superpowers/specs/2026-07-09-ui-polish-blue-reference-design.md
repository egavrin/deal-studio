# UI Polish Blue Reference Design

Date: 2026-07-09
Status: implemented and verified on Pixel 10

## Goal

Polish the Android Offline Assistant PoC main chat and result widgets so they visually follow the provided single-phone blue reference mocks while preserving the existing assistant architecture and payload contracts.

## Approved Direction

Use the Blue Reference direction:

- white app background;
- compact cutout-safe `Assistant` top bar without decorative no-op actions;
- pale blue user bubbles aligned right;
- assistant avatar/sparkle aligned left with compact white assistant bubbles;
- white result cards with thin borders, light shadow, 14-16dp radius, and stronger typography;
- blue primary accents for microphone, action buttons, numeric results, and status icons.

The supplied mock screenshots are stored under `docs/design/references/`. A local browser review board for the
approved direction A is available at `docs/design/blue-reference-board/index.html`; it is a comparison aid for
visual review, not a replacement for the real Jetpack Compose implementation.

## Scope

In scope:

- `MainChatScreen` visual layout and input bar styling;
- visual styling for weather, timer, alarm, calculator, reminder, note, open app, permission, error, clarification, help, and generic answer cards;
- widget preview samples should inherit the same card styling;
- Compose/UI tests may be adjusted to preserve semantic tags/content descriptions.

Out of scope:

- ASR, RuBERT, Qwen routing behavior;
- widget payload schema;
- Android skill execution behavior;
- broad navigation architecture changes.

## UI Requirements

Main chat:

- Keep the first screen as a usable chat, not a landing page.
- Keep bottom navigation labels: `Чат`, `История`, `Навыки`, `Настройки`.
- Hide raw one-line debug footer from the main chat surface; diagnostics remain available through the debug/history screen.
- Keep transcript preview visible when voice input produces text.
- Keep loading/recording feedback visible and compact above the input bar.
- Use a single rounded composer plus one circular trailing action: microphone when empty, send when text is present, and stop while recording.
- Present the transcript as localized compact status (`Распознано:` / `Слушаю...`), never as a raw debug footer.

Widgets:

- Weather card should emphasize location, temperature, condition, metric row, hourly forecast row, and source chip.
- Timer card should show title, label, large countdown, visual circular progress, and pause/cancel actions.
- Alarm, reminder, note, calculator, and open-app cards should use reference-style icon tiles, strong primary values, and compact actions.
- Generic local LLM answer card should be clean text, with source indicator reserved for debug/internal presentation.
- Format persisted dates and payload states for people; do not show raw ISO timestamps, `scheduled`, `local_llm`, or `source: mock` identifiers.

Settings:

- Keep ASR selection as the primary control with name, size, language, and speed summary.
- Show Whisper/RuBERT/Qwen readiness as compact status rows, without app-private paths.
- Describe the RuBERT confidence threshold as an action-execution control; do not imply that Qwen parses or repairs commands.
- Keep destructive demo-data actions full-width and independently tappable.

## Implementation Snapshot

Implemented on 2026-07-10:

- shared Compose light theme and Blue Reference color/typography tokens;
- cutout-safe app title plus Material icon bottom navigation and assistant avatar;
- responsive message widths, localized streaming/processing states, and automatic list scrolling;
- unified mic/send/stop composer that remains usable with the Pixel's enlarged display/font settings;
- compact Settings sections for ASR choice, local model status, RuBERT action threshold, and demo data;
- mobile-safe widget actions, wrapping clarification/error suggestions, localized source/state labels, and readable timestamps.
- streamed Qwen answers render once in the assistant bubble; the deduplicated `GenericAnswerCard` collapses to a compact local-model source badge instead of leaving an empty card.

## Verification

- Unit/Compose tests must continue to pass for semantic tags and actions.
- Device install/launch must show the chat screen without the Android 16 KB compatibility dialog.
- A fresh screenshot should be captured after UI polish for visual review.

Pixel evidence:

- `build/device-screenshots/offline-assistant-ui-polish-chat-blank-2026-07-10.png`;
- `build/device-screenshots/offline-assistant-ui-polish-settings-2026-07-10.png`;
- `build/device-screenshots/offline-assistant-ui-polish-demo-final-2026-07-10.png`.
