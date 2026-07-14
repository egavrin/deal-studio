#!/usr/bin/env python3
"""Static checks for the browser design reference board."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
BOARD = ROOT / "docs" / "design" / "blue-reference-board" / "index.html"


class DesignBoardTest(unittest.TestCase):
    def test_blue_reference_board_contains_approved_assets_and_scenarios(self) -> None:
        html = BOARD.read_text(encoding="utf-8")

        required_tokens = [
            "Вариант A",
            "Blue Reference",
            "../references/chat-widgets-triptych.png",
            "../references/chat-weather-timer-reference.png",
            "../references/chat-alarm-calculator-reminder-reference.png",
            "Погода",
            "Таймер",
            "Заметка",
            "Сложный вопрос",
            "локальная модель",
            "Android-first",
            "Qwen отвечает текстом",
        ]

        for token in required_tokens:
            self.assertIn(token, html)

    def test_blue_reference_board_is_app_surface_not_landing_page(self) -> None:
        html = BOARD.read_text(encoding="utf-8")

        self.assertIn('class="phone-shell"', html)
        self.assertIn('class="chat-stream"', html)
        self.assertIn('class="input-bar"', html)
        self.assertIn("Введите сообщение", html)
        self.assertNotIn("hero", html.lower())
        self.assertNotIn("debug:", html)


if __name__ == "__main__":
    unittest.main()
