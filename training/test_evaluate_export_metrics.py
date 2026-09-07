#!/usr/bin/env python3
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "training" / "rubert" / "evaluate_export.py"
SPEC = importlib.util.spec_from_file_location("evaluate_export", MODULE_PATH)
evaluate_export = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(evaluate_export)


class EvaluateExportMetricsTest(unittest.TestCase):
    def test_load_eval_cases_normalizes_runtime_slots(self):
        rows = [
            {
                "text": "Поставь таймер на две минуты",
                "intent": "set_timer",
                "slots": [{"name": "duration", "value": "две минуты", "start": 18, "end": 28}],
            },
            {
                "text": "Создай заметку проверить отчет",
                "intent": "create_note",
                "slots": [{"name": "text", "value": "проверить отчет", "start": 15, "end": 29}],
            },
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "eval.jsonl"
            path.write_text("\n".join(json.dumps(row, ensure_ascii=False) for row in rows), encoding="utf-8")

            cases = evaluate_export.load_eval_cases(path)

        self.assertEqual("120", cases[0]["slots"]["duration_seconds"])
        self.assertEqual("проверить отчет", cases[1]["slots"]["text"])
        self.assertTrue(cases[0]["gate"])
        self.assertTrue(cases[1]["gate"])

    def test_summary_reports_all_required_metrics(self):
        results = [
            result("set_timer", "set_timer", exact=True, gate=True, tp=3),
            result("set_alarm", "set_timer", exact=False, gate=True, tp=1, fp=1, fn=2),
            result("create_note", "create_note", exact=True, gate=False, tp=2),
        ]

        metrics = evaluate_export.summarize_results(results)

        self.assertAlmostEqual(2 / 3, metrics["intent_accuracy"])
        self.assertIn("intent_macro_f1", metrics)
        self.assertAlmostEqual(12 / 15, metrics["slot_f1"])
        self.assertAlmostEqual(2 / 3, metrics["validation_pass_rate"])
        self.assertEqual(1, metrics["confusion_matrix"]["set_alarm"]["set_timer"])
        self.assertEqual(2, metrics["regression_gate_count"])
        self.assertEqual(1, metrics["regression_gate_pass_count"])

    def test_reminder_text_uses_reminder_slot_label(self):
        self.assertEqual("reminder_text", evaluate_export.dataset_slot_label("create_reminder", "text"))
        self.assertEqual("text", evaluate_export.dataset_slot_label("create_note", "text"))

    def test_division_expression_is_preserved_in_expected_slots(self):
        slots = evaluate_export.normalize_expected_slots(
            "calculate",
            [{"name": "expression", "value": "81 разделить на 9"}],
        )

        self.assertEqual("81 / 9", slots["expression"])

    def test_metric_gates_report_only_threshold_regressions(self):
        metrics = {
            "intent_accuracy": 0.96,
            "intent_macro_f1": 0.94,
            "slot_f1": 0.89,
            "validation_pass_rate": 0.91,
        }

        failures = evaluate_export.metric_gate_failures(
            metrics,
            min_intent_accuracy=0.95,
            min_macro_f1=0.93,
            min_slot_f1=0.90,
            min_validation_pass_rate=0.90,
        )

        self.assertEqual(["slot_f1=0.890 below 0.900"], failures)


def result(expected, actual, exact, gate, tp, fp=0, fn=0):
    return {
        "expected_intent": expected,
        "actual_intent": actual,
        "exact": exact,
        "gate": gate,
        "_slot_tp": tp,
        "_slot_fp": fp,
        "_slot_fn": fn,
    }


if __name__ == "__main__":
    unittest.main()
