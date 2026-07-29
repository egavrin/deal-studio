# Core Device Acceptance

Run on an ARM64 phone after host checks pass.

## Setup

1. Stage `models/generated/rubert/` to
   `/data/local/tmp/offline-assistant-rubert/`.
2. Stage `models/external/silero-v5_5-ru-xenia/android-bundle/` to
   `/data/local/tmp/offline-assistant-silero/`.
3. Run `scripts/device_smoke_test.sh`.
4. Configure DeepSeek BYOK through Settings when cloud testing is required.

## Functional Checklist

- T-one shows evolving partial Russian transcript while recording.
- Final transcript is inserted as one user message.
- “Поставь таймер на 5 минут” routes through local RuBERT and shows TimerCard.
- “Запиши заметку купить молоко” shows NoteCard and survives restart.
- “Сколько будет 18 умножить на 3” shows CalculatorCard with 54.
- Low-confidence action returns clarification without network use.
- Open-ended question routes to DeepSeek only when enabled and streams one message.
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
3. DeepSeek streaming answer;
4. local Silero playback;
5. debug route labels distinguishing local and cloud execution.
