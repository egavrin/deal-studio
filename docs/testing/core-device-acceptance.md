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
- Saying “стоп” or a new request during speech stops playback through the AEC/T-one
  barge-in path and keeps the same conversation; manual interruption also works.
- “Поставь таймер на 5 минут” routes through local RuBERT and shows TimerCard.
- “Запиши заметку купить молоко” shows NoteCard and survives restart.
- “Сколько будет 18 умножить на 3” shows CalculatorCard with 54.
- “Построй маршрут до Красной площади” shows a confirmation card and opens maps
  only after Continue is tapped; tapping twice executes once.
- “Подготовь сообщение: буду через десять минут” opens a populated system composer
  after confirmation and does not send the message.
- Low-confidence action returns clarification without network use.
- Open-ended question routes to DeepSeek only when enabled and streams one message.
- “Что нового в Android 17?” routes RuBERT → Exa Search → DeepSeek, streams
  Markdown, inline citations, visual source cards, a source preview and related
  questions.
- “Исследуй рынок локальных голосовых ассистентов” starts Exa Agent, releases the
  composer, updates one `ResearchCard` from SSE and can be cancelled independently.
- Completed research opens a report, shares Markdown and accepts a follow-up tied
  to the original Exa run.
- A follow-up open-ended question uses earlier visible turns as context.
- “Покажи фотографии Кривого Рога” routes as RuBERT `unknown`, renders attributed
  Wikimedia images and
  opens the Commons source page when tapped.
- Second and later DeepSeek requests do not crash or duplicate the answer.
- Silero starts speaking a stable clause and highlights the spoken range.
- Stop cancels generation and TTS without blocking scrolling or input.
- Chat auto-scrolls during transcription and cloud streaming.
- Composer stays above the bottom navigation and IME.
- Title stays below display cutouts and status bars.
- Assistant TTS is not accepted as the next user turn. Short ASR decoder noise
  resumes listening without invoking RuBERT.

## System Assistant Checklist

Run after the normal chat path is stable:

API 37 emulator coverage already verifies role binding, system-key invocation,
overlay rendering/insets, shared text routing and repeated dismissal. The checklist
below intentionally remains a physical-device gate for OEM role UX, real audio,
screen context and barge-in.

1. In app Settings, grant microphone access and choose the app as the default
   digital assistant.
2. Verify Android reports “Выбран системным ассистентом” after returning to the
   app.
3. From the launcher and from another application, invoke the configured
   power/home/corner gesture.
4. Require the compact overlay to appear without opening the full activity and to
   begin local T-one listening.
5. Say “Поставь таймер на 5 минут”. Require an evolving local transcript, the
   `LOCAL • RUBERT` badge and one TimerCard.
6. Ask an open-ended question. Require one streamed Markdown message, the
   `DEEPSEEK` badge and local Silero playback.
7. With screen context disabled, ask about the foreground page and verify no
   “ЭКРАН” badge or page text reaches the answer.
8. Enable screen context, invoke over a non-sensitive page and ask “Кратко перескажи
   этот экран”. Require the visible “ЭКРАН” badge and a context-aware answer.
9. Repeat over password/payment and `FLAG_SECURE` screens. Require no sensitive
   text in the overlay, answer, history or logs.
10. Interrupt TTS with a new utterance and require the existing AEC/T-one barge-in
    path to continue the same conversation.
11. Dismiss during listening and during DeepSeek streaming. Require microphone,
    speech and foreground request cancellation with no stuck notification or UI.
12. Invoke and dismiss ten times, then open full chat. Require one shared history
    and no duplicate answer or model instance.
13. Switch the default role back to the previous assistant and verify its gesture
    works without residual Offline Assistant services.

Record cold and warm gesture-to-overlay, gesture-to-listening, first ASR partial,
RuBERT completion, first cloud token, first spoken audio and process PSS.

## Final Gate

Record one self-contained demo showing:

1. live microphone to T-one transcript;
2. local RuBERT timer or note action;
3. a grounded Exa Search + DeepSeek streaming answer;
4. a background Exa Agent research card;
5. local Silero playback;
6. debug route labels distinguishing local and cloud execution.
7. system gesture invocation and the compact assistant overlay.

## Automated Device Checks

The following focused checks use production runtime wiring and do not call Exa or
DeepSeek:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.offlineassistant.app.acceptance.LocalActionsRouteTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.offlineassistant.app.audio.VoiceProcessingDeviceTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.offlineassistant.app.acceptance.ConversationEchoLoopTest
```

`LocalActionsRouteTest` submits a navigation command through the installed ONNX
RuBERT model and requires the local route label plus `ActionConfirmationCard`.
`VoiceProcessingDeviceTest` starts a real `VOICE_COMMUNICATION` capture and requires
working acoustic echo cancellation. It reports NS/AGC availability but does not
replace the final human speak-over-TTS barge-in check.
`ConversationEchoLoopTest` drives a real local response through
Silero speaker playback and T-one microphone capture, then requires that no second
user turn is created.

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

The same device passed both automated production-wiring checks on 2026-07-29:

- the installed 21-label RuBERT export classified
  `Построй маршрут до Красной площади` locally and rendered a confirmation card;
- `VOICE_COMMUNICATION` initialized and platform acoustic echo cancellation was
  enabled.

Additional OPPO CPH2765 regressions passed on 2026-07-29:

- `Покажи фотографии Кривого Рога` classified as `unknown` at confidence `0.9868`,
  streamed one DeepSeek answer and loaded three attributed Wikimedia images;
- the focused speaker-to-microphone test completed local weather TTS, returned to
  listening and created no echoed user turn during the observation window;
- a real ColorOS IME check placed the composer bottom exactly at the keyboard top,
  with neither the previous full-height jump nor keyboard overlap.

The live tests are opt-in and do not run in CI:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.offlineassistant.app.acceptance.LiveExaRouteTest \
  -Pandroid.testInstrumentationRunnerArguments.liveExa=true
```

If the phone uses a per-app VPN allowlist, add the Offline Assistant package before
interpreting HTTP 403 as an invalid API key. The app reports both key and
network/VPN checks in its error guidance.
