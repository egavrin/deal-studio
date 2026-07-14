# Design References

These images are product mock references for the Android Offline Assistant PoC chat and result widgets.

## Files

- `chat-widgets-triptych.png` - three-screen overview showing weather, timer/note, and local LLM answer flows.
- `chat-alarm-calculator-reminder-reference.png` - single-phone reference for alarm, calculator, and reminder cards.
- `chat-weather-timer-reference.png` - single-phone reference for weather and timer cards.
- `../blue-reference-board/index.html` - browser review board for approved direction A, with the mocks and an interactive chat/widget surface.

## Implementation Notes

- Treat these as visual direction, not exact pixel locks.
- Keep the first screen as the usable chat, not a landing page.
- Preserve the quiet assistant style: white background, pale user bubbles, compact assistant bubbles, rounded result cards, strong typography inside cards, and a persistent input bar.
- Weather, timer, alarm, calculator, reminder, and note cards should be visually closer to these references than to generic Material sample cards.
- Debug/internal screens may stay more utilitarian, but the main chat and widget preview should use these mocks as the visual benchmark.
