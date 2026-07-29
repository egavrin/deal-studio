# Core Device Acceptance

Run on an ARM64 phone after host checks pass.

## Setup

1. Stage `models/generated/rubert/` to
   `/data/local/tmp/offline-assistant-rubert/`.
2. Stage `models/external/silero-v5_5-ru-xenia/android-bundle/` to
   `/data/local/tmp/offline-assistant-silero/`.
3. Run `scripts/device_smoke_test.sh`.
4. Configure and enable DeepSeek and Exa BYOK through Settings for network testing.

## Functional Checklist

- T-one shows evolving partial Russian transcript while recording.
- Composer microphone leaves the final transcript editable and does not send it.
- Waveform action starts conversation mode and auto-submits each finalized turn.
- After local speech finishes, conversation mode returns to listening.
- Interrupting speech stops playback and starts the next listening turn.
- “Поставь таймер на 5 минут” routes through local RuBERT and shows TimerCard.
- “Запиши заметку купить молоко” shows NoteCard and survives restart.
- “Сколько будет 18 умножить на 3” shows CalculatorCard with 54.
- Low-confidence action returns clarification without network use.
- Open-ended question routes to DeepSeek only when enabled and streams one message.
- “Что нового в Android 17?” routes RuBERT → Exa Search → DeepSeek, streams
  Markdown and shows tappable numbered sources.
- “Исследуй рынок локальных голосовых ассистентов” starts Exa Agent, releases the
  composer, updates one `ResearchCard` and can be cancelled independently.
- A follow-up open-ended question uses earlier visible turns as context.
- “Покажи фотографии Красной площади” renders attributed Wikimedia images and
  opens the Commons source page when tapped.
- Second and later DeepSeek requests do not crash or duplicate the answer.
- Silero starts speaking a stable clause and highlights the spoken range.
- Stop cancels generation and TTS without blocking scrolling or input.
- Chat auto-scrolls during transcription and cloud streaming.
- Composer stays above the bottom navigation and IME.
- Title stays below display cutouts and status bars.

## Final Gate

Record one self-contained demo showing:

1. live microphone to T-one transcript;
2. local RuBERT timer or note action;
3. a grounded Exa Search + DeepSeek streaming answer;
4. a background Exa Agent research card;
5. local Silero playback;
6. debug route labels distinguishing local and cloud execution.

## Latest Live Cloud Evidence

Validated on 2026-07-29 on OPPO CPH2765 with the release-equivalent debug
pipeline and encrypted BYOK values:

- `Что нового в Android 17?`
  - RuBERT selected `web_search` with confidence `0.9673`;
  - Exa `auto` returned six official Android/Google sources;
  - Exa retrieval: `2694 ms`;
  - first visible DeepSeek token: `5417 ms` from request start;
  - DeepSeek completion: `6555 ms`;
  - end-to-end: `10188 ms`;
  - Markdown, numbered sources and the route label
    `RuBERT → Exa Search → DeepSeek` rendered correctly.
- `Исследуй рынок локальных голосовых ассистентов`
  - RuBERT selected `web_research` with confidence `0.9896`;
  - Exa Agent completed in `19006 ms`;
  - end-to-end: `19883 ms`;
  - the completed `ResearchCard` contained a summary, findings and five sources;
  - a previously interrupted run restored as `interrupted` instead of resuming
    stale work.

The live tests are opt-in and do not run in CI:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.offlineassistant.app.acceptance.LiveExaRouteTest \
  -Pandroid.testInstrumentationRunnerArguments.liveExa=true
```

If the phone uses a per-app VPN allowlist, add the Offline Assistant package before
interpreting HTTP 403 as an invalid API key. The app reports both key and
network/VPN checks in its error guidance.
