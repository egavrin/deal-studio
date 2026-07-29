#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent


class TrainingLayoutTest(unittest.TestCase):
    def test_spec_training_deliverables_exist(self):
        expected = [
            ROOT / "rubert" / "synthetic_intents.jsonl",
            ROOT / "rubert" / "generate_core_dataset.py",
            ROOT / "data" / "eval_set.jsonl",
            ROOT / "scripts" / "generate_synthetic_dataset.py",
            ROOT / "scripts" / "train_rubert_tiny2.py",
            ROOT / "scripts" / "export_onnx.py",
            ROOT / "scripts" / "evaluate_nlu.py",
            ROOT / "rubert" / "ci_export_smoke.py",
            ROOT / "README.md",
        ]

        missing = [str(path.relative_to(ROOT)) for path in expected if not path.is_file()]

        self.assertEqual([], missing)

    def test_eval_set_has_slot_records(self):
        eval_path = ROOT / "data" / "eval_set.jsonl"
        records = [
            json.loads(line)
            for line in eval_path.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]

        self.assertGreaterEqual(len(records), 8)
        self.assertTrue(any(record["intent"] == "set_timer" for record in records))
        self.assertTrue(any(record["intent"] == "get_weather" for record in records))
        self.assertTrue(any(record["slots"] for record in records))

    def test_training_set_matches_core_allowlist(self):
        records = [
            json.loads(line)
            for line in (ROOT / "rubert" / "synthetic_intents.jsonl").read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]
        expected = {
            "get_current_time",
            "get_weather",
            "set_timer",
            "set_alarm",
            "create_reminder",
            "create_note",
            "calculate",
            "open_app",
            "help",
            "web_search",
            "web_research",
            "unknown",
        }
        counts = {intent: 0 for intent in expected}
        for record in records:
            self.assertIn(record["intent"], expected)
            counts[record["intent"]] += 1
            for slot in record.get("slots", []):
                self.assertEqual(
                    slot["value"],
                    record["text"][slot["start"]:slot["end"]],
                )
        self.assertGreaterEqual(min(counts.values()), 80)

    def test_readme_documents_required_metrics(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8").lower()

        for phrase in [
            "intent accuracy",
            "macro f1",
            "slot f1",
            "per-intent confusion",
            "validation pass rate",
        ]:
            self.assertIn(phrase, readme)


if __name__ == "__main__":
    unittest.main()
