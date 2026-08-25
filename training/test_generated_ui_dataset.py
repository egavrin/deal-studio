#!/usr/bin/env python3
import copy
import hashlib
import json
import sys
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from training.generated_ui.build_artifacts import artifacts
from training.generated_ui.catalog import COMPONENT_BY_NAME
from training.generated_ui.coverage import build_task_seeds, coverage_report
from training.generated_ui.dataset import assign_splits, build_record
from training.generated_ui.deepseek_client import DeepSeekGenerationError, _extract_output_text
from training.generated_ui.fixtures import all_blueprints, morning_agenda
from training.generated_ui.generate_dataset import percentile, rejection_taxonomy, task_seed_sha256
from training.generated_ui.pipeline import (
    BlueprintValidationError,
    adapt_teacher_blueprint,
    compile_a2ui_express,
    structural_fingerprint,
    validate_task_alignment,
    validate_blueprint,
)
from training.generated_ui.schema import build_blueprint_schema, build_teacher_response_schema


ROOT = Path(__file__).resolve().parent / "generated_ui"


class GeneratedUiDatasetTest(unittest.TestCase):
    def assert_diagnostic(self, blueprint, code):
        with self.assertRaises(BlueprintValidationError) as caught:
            validate_blueprint(blueprint)
        self.assertIn(code, {item.code for item in caught.exception.diagnostics})

    def test_generated_artifacts_are_current_and_schema_is_valid(self):
        Draft202012Validator.check_schema(build_blueprint_schema())
        Draft202012Validator.check_schema(build_teacher_response_schema())
        for path, expected in artifacts().items():
            self.assertTrue(path.is_file(), path)
            self.assertEqual(expected, path.read_text(encoding="utf-8"), path)

    def test_calibration_evidence_is_bound_to_current_contract(self):
        evidence = json.loads(
            (ROOT / "calibration" / "2026-08-25-deepseek-v4-flash-50-v4.json").read_text(
                encoding="utf-8"
            )
        )

        def sha256(path):
            return hashlib.sha256(path.read_bytes()).hexdigest()

        self.assertEqual(
            sha256(ROOT / "catalog-manifest.json"),
            evidence["contract"]["catalog_manifest_sha256"],
        )
        self.assertEqual(
            sha256(ROOT / "prompts" / "deepseek-ui-teacher-v1.md"),
            evidence["contract"]["teacher_prompt_sha256"],
        )
        self.assertEqual(
            evidence["candidate_count"],
            evidence["result"]["accepted"] + evidence["result"]["rejected"],
        )
        self.assertFalse(evidence["gate"]["passed"])

    def test_fixtures_validate_compile_and_cover_catalog(self):
        used = set()
        for blueprint in all_blueprints():
            validate_blueprint(blueprint)
            compiled = compile_a2ui_express(blueprint)
            self.assertTrue(compiled.startswith("<a2ui>\n"))
            self.assertTrue(compiled.endswith("\n</a2ui>"))
            self.assertEqual(1, compiled.count("\nroot = "))
            used.update(component["component"] for component in blueprint["components"])
        self.assertEqual(set(COMPONENT_BY_NAME), used)

    def test_compiler_emits_children_before_root(self):
        compiled = compile_a2ui_express(morning_agenda()).splitlines()
        root_index = next(index for index, line in enumerate(compiled) if line.startswith("root ="))
        child_index = next(index for index, line in enumerate(compiled) if line.startswith("calendar_button ="))
        self.assertLess(child_index, root_index)

    def test_semantic_validator_rejects_duplicate_ids(self):
        blueprint = morning_agenda()
        blueprint["components"][1]["id"] = blueprint["components"][0]["id"]
        self.assert_diagnostic(blueprint, "duplicate_component_id")

    def test_semantic_validator_rejects_missing_reference(self):
        blueprint = morning_agenda()
        root = next(item for item in blueprint["components"] if item["id"] == "agenda_root")
        root["children"][0] = "missing_header"
        self.assert_diagnostic(blueprint, "missing_child_reference")

    def test_semantic_validator_rejects_missing_binding(self):
        blueprint = morning_agenda()
        title = next(item for item in blueprint["components"] if item["id"] == "title")
        title["text"] = {"binding": "/missing"}
        self.assert_diagnostic(blueprint, "missing_binding")

    def test_bindings_support_array_indices_and_current_template_item(self):
        blueprint = morning_agenda()
        title = next(item for item in blueprint["components"] if item["id"] == "title")
        title["text"] = {"binding": "/events/0/title"}
        validate_blueprint(blueprint)

        event_row = next(item for item in blueprint["components"] if item["id"] == "event_row")
        title["text"] = "Tomorrow morning"
        blueprint["data_model"]["events"] = ["Design review", "Project sync"]
        event_row["label"] = {"binding": "."}
        event_row["value"] = {"binding": "."}
        validate_blueprint(blueprint)

        blueprint["data_model"]["events"] = [{"nested": "object"}]
        self.assert_diagnostic(blueprint, "binding_type_mismatch")

    def test_nested_and_empty_collection_templates_have_item_context(self):
        blueprint = morning_agenda()
        event_row = next(item for item in blueprint["components"] if item["id"] == "event_row")
        event_fact = copy.deepcopy(event_row)
        event_fact["id"] = "event_fact"
        event_row.clear()
        event_row.update(
            {
                "id": "event_row",
                "component": "Column",
                "children": ["event_fact", "stop_list"],
                "gap": "xs",
            }
        )
        blueprint["components"].extend(
            [
                event_fact,
                {
                    "id": "stop_list",
                    "component": "List",
                    "items": {"binding": "stops"},
                    "template": "stop_text",
                    "direction": "vertical",
                },
                {
                    "id": "stop_text",
                    "component": "Text",
                    "text": {"binding": "time"},
                    "variant": "body",
                    "tone": "muted",
                    "align": "start",
                },
            ]
        )
        blueprint["data_model"]["events"][0]["stops"] = [{"time": "09:15"}]
        validate_blueprint(blueprint)

        blueprint["data_model"]["events"] = []
        validate_blueprint(blueprint)

    def test_semantic_validator_rejects_undeclared_action(self):
        blueprint = morning_agenda()
        button = next(item for item in blueprint["components"] if item["id"] == "calendar_button")
        button["action"]["event"] = "undeclared_action"
        self.assert_diagnostic(blueprint, "undeclared_action")

    def test_collection_actions_support_item_relative_bindings(self):
        blueprint = all_blueprints()[1]
        source_list = next(
            item for item in blueprint["components"] if item["component"] == "SourceList"
        )
        source_list["open_action"]["args"][0] = {"binding": "url"}
        validate_blueprint(blueprint)

        source_list["open_action"]["args"][0] = {"binding": "missing"}
        self.assert_diagnostic(blueprint, "missing_binding")

    def test_design_token_enums_support_validated_bindings(self):
        blueprint = morning_agenda()
        badge = next(item for item in blueprint["components"] if item["component"] == "Badge")
        blueprint["data_model"]["status_tone"] = "positive"
        badge["tone"] = {"binding": "/status_tone"}
        validate_blueprint(blueprint)

        blueprint["data_model"]["status_tone"] = "neon"
        self.assert_diagnostic(blueprint, "binding_type_mismatch")

    def test_semantic_validator_rejects_inaccessible_icon_button(self):
        blueprint = morning_agenda()
        button = next(item for item in blueprint["components"] if item["id"] == "calendar_button")
        button["icon_only"] = True
        self.assert_diagnostic(blueprint, "missing_accessibility_label")

    def test_semantic_validator_rejects_cycle_and_unsafe_url(self):
        cyclic = morning_agenda()
        root = next(item for item in cyclic["components"] if item["id"] == "agenda_root")
        root["children"].append("agenda_root")
        self.assert_diagnostic(cyclic, "component_cycle")

        unsafe = all_blueprints()[4]
        image = next(item for item in unsafe["components"] if item["component"] == "Image")
        image["url"] = "http://untrusted.example/image.jpg"
        self.assert_diagnostic(unsafe, "unsafe_dataset_url")

        bound_unsafe = all_blueprints()[1]
        bound_unsafe["data_model"]["selected_source_url"] = "https://evil.example/source"
        self.assert_diagnostic(bound_unsafe, "unsafe_dataset_url")

    def test_task_alignment_rejects_teacher_rewriting_seed(self):
        blueprint = morning_agenda()
        task = {
            key: blueprint[key]
            for key in ("request", "locale", "domain", "task_kind", "viewport", "theme", "state", "density")
        }
        task.update(
            {
                "required_components": ["Timeline", "Button"],
                "focal_component": "Timeline",
                "target_nodes_min": 1,
                "target_nodes_max": 64,
            }
        )
        validate_task_alignment(blueprint, task)
        task["request"] = "A different request"
        with self.assertRaises(BlueprintValidationError) as caught:
            validate_task_alignment(blueprint, task)
        self.assertIn("task_mismatch", {item.code for item in caught.exception.diagnostics})

    def test_teacher_transport_decodes_dynamic_maps(self):
        canonical = morning_agenda()

        teacher = copy.deepcopy(canonical)
        teacher["data_model"] = {
            "entries": [
                {
                    "key": key,
                    "value_json": json.dumps(value, ensure_ascii=False, separators=(",", ":")),
                }
                for key, value in teacher["data_model"].items()
            ]
        }
        button = next(item for item in teacher["components"] if item["id"] == "calendar_button")
        button["action"]["context"] = {
            "entries": [
                {"key": key, "value": value}
                for key, value in button["action"]["context"].items()
            ]
        }
        teacher["required_id_Button"] = "calendar_button"
        teacher["required_id_Timeline"] = "timeline"
        decoded = adapt_teacher_blueprint(teacher)
        self.assertEqual(canonical, decoded)

        teacher["required_id_Button"] = "timeline"
        with self.assertRaises(BlueprintValidationError) as caught:
            adapt_teacher_blueprint(teacher)
        self.assertIn(
            "required_component_mapping",
            {item.code for item in caught.exception.diagnostics},
        )

    def test_task_specific_teacher_schema_requires_component_index(self):
        schema = build_teacher_response_schema(["Text", "Button", "Text"])
        self.assertIn("required_id_Button", schema["required"])
        self.assertIn("required_id_Text", schema["required"])
        self.assertEqual(
            {"$ref": "#/$defs/componentId"},
            schema["properties"]["required_id_Button"],
        )

    def test_deepseek_output_extraction_ignores_reasoning(self):
        response = {
            "output": [
                {
                    "type": "reasoning",
                    "content": [
                        {"type": "reasoning_text", "text": "internal analysis"}
                    ],
                },
                {
                    "type": "message",
                    "content": [
                        {"type": "output_text", "text": '{"schema_version":1}'}
                    ],
                },
            ]
        }
        self.assertEqual('{"schema_version":1}', _extract_output_text(response))

    def test_deepseek_output_extraction_rejects_reasoning_only_response(self):
        response = {
            "output": [
                {
                    "type": "reasoning",
                    "content": [
                        {"type": "reasoning_text", "text": "no final answer"}
                    ],
                }
            ]
        }
        with self.assertRaisesRegex(DeepSeekGenerationError, "no output text"):
            _extract_output_text(response)

    def test_coverage_schedule_focally_covers_every_component(self):
        tasks = build_task_seeds(310)
        report = coverage_report(tasks)
        self.assertEqual(set(COMPONENT_BY_NAME), set(report["focal_components"]))
        self.assertEqual({10}, set(report["focal_components"].values()))
        self.assertEqual({"en-US": 186, "ru-RU": 93, "mixed": 31}, report["locales"])
        self.assertEqual((8, 12), (tasks[0].target_nodes_min, tasks[0].target_nodes_max))
        self.assertTrue(
            all(task.target_nodes_max >= len(task.required_components) for task in tasks)
        )
        self.assertTrue(
            all(task.target_nodes_max >= min(64, len(task.required_components) + 4) for task in tasks)
        )
        self.assertTrue(any(task.target_nodes_max >= 49 for task in tasks))
        self.assertLessEqual(max(task.target_nodes_max for task in tasks), 64)

    def test_structural_templates_never_cross_splits(self):
        first = morning_agenda()
        second = copy.deepcopy(first)
        second["request"] = "Show the key events on my next morning."
        second["data_model"]["title"] = "Next morning"
        records = [
            build_record(first, {"generator": "test"}),
            build_record(second, {"generator": "test"}),
        ]
        splits, report = assign_splits(records)
        populated = [name for name, rows in splits.items() if rows]
        self.assertEqual(1, len(populated))
        self.assertEqual([], report["template_leakage"]["train_test"])
        self.assertEqual([], report["template_leakage"]["train_validation"])
        self.assertEqual([], report["template_leakage"]["validation_test"])

    def test_generation_report_helpers_are_deterministic(self):
        self.assertEqual(
            task_seed_sha256({"request": "hello", "required_components": ["Text"]}),
            task_seed_sha256({"required_components": ["Text"], "request": "hello"}),
        )
        self.assertEqual(20, percentile([30, 10, 20], 0.5))
        self.assertEqual(30, percentile([30, 10, 20], 0.95))
        self.assertIsNone(percentile([], 0.5))
        rows = [
            {
                "attempt_errors": [
                    "missing_binding at $.components.title: /title; "
                    "depth_budget at $.components: 9",
                    "missing_binding at $.components.title: /title",
                ]
            }
        ]
        self.assertEqual(
            {"depth_budget": 1, "missing_binding": 1},
            rejection_taxonomy(rows, 0),
        )
        self.assertEqual({"missing_binding": 1}, rejection_taxonomy(rows, 1))

    def test_structural_hash_ignores_teacher_chosen_ids_and_action_names(self):
        first = morning_agenda()
        second = copy.deepcopy(first)
        ids = {item["id"]: f"node_{index}" for index, item in enumerate(second["components"])}
        for component in second["components"]:
            component["id"] = ids[component["id"]]
            for key in ("child", "template", "empty_state"):
                if key in component:
                    component[key] = ids[component[key]]
            if "children" in component:
                component["children"] = [ids[item] for item in component["children"]]
            for tab in component.get("tabs", []):
                tab["child"] = ids[tab["child"]]
            for value in component.values():
                if isinstance(value, dict) and value.get("event") == "open_calendar":
                    value["event"] = "show_day"
                    value["context"]["target"] = value["context"].pop("date")
        second["root_id"] = ids[second["root_id"]]
        second["actions"][0]["name"] = "show_day"
        second["actions"][0]["parameters"][0]["name"] = "target"
        validate_blueprint(second)
        self.assertEqual(structural_fingerprint(first), structural_fingerprint(second))


if __name__ == "__main__":
    unittest.main()
