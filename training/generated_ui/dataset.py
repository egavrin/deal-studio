"""Canonical record construction and leakage-safe dataset splitting."""

from __future__ import annotations

import hashlib
import json
from collections import Counter, defaultdict
from copy import deepcopy
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Sequence, Tuple

from .catalog import ASSISTANT_CATALOG_ID, BASIC_CATALOG_ID
from .pipeline import (
    BLUEPRINT_SCHEMA,
    blueprint_sha256,
    canonical_blueprint,
    compile_a2ui_express,
    compile_a2ui_wire,
    structural_fingerprint,
    validate_blueprint,
)


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


SCHEMA_SHA256 = hashlib.sha256(canonical_json(BLUEPRINT_SCHEMA).encode("utf-8")).hexdigest()


def build_record(
    blueprint: Mapping[str, Any],
    provenance: Mapping[str, Any],
) -> Dict[str, Any]:
    validate_blueprint(blueprint)
    canonical = canonical_blueprint(blueprint)
    exact_hash = blueprint_sha256(canonical)
    template_hash = structural_fingerprint(canonical)
    return {
        "schema_version": 1,
        "id": f"sha256:{exact_hash}",
        "split": None,
        "task": {
            "request": canonical["request"],
            "locale": canonical["locale"],
            "domain": canonical["domain"],
            "task_kind": canonical["task_kind"],
            "viewport": canonical["viewport"],
            "theme": canonical["theme"],
            "state": canonical["state"],
            "density": canonical["density"],
        },
        "catalog": {
            "protocol_version": "1.0-internal",
            "catalog_ids": [BASIC_CATALOG_ID, ASSISTANT_CATALOG_ID],
            "resolved_schema_sha256": SCHEMA_SHA256,
        },
        "blueprint": canonical,
        "target": {
            "ast": {
                "components": deepcopy(canonical["components"]),
                "root_id": canonical["root_id"],
                "actions": deepcopy(canonical["actions"]),
            },
            "a2ui_express": compile_a2ui_express(canonical),
            "a2ui_wire": compile_a2ui_wire(canonical),
            "data_model": deepcopy(canonical["data_model"]),
        },
        "validation": {
            "schema": "pass",
            "catalog": "pass",
            "references": "pass",
            "bindings": "pass",
            "actions": "pass",
            "accessibility": "pass",
        },
        "hashes": {
            "blueprint_sha256": exact_hash,
            "template_sha256": template_hash,
        },
        "provenance": dict(provenance),
    }


def _group_split(template_hash: str) -> str:
    bucket = int(template_hash[:8], 16) % 100
    if bucket < 80:
        return "train"
    if bucket < 90:
        return "validation"
    return "test"


def assign_splits(
    records: Sequence[Mapping[str, Any]],
    *,
    validation_domains: Sequence[str] = (),
    test_domains: Sequence[str] = (),
) -> Tuple[Dict[str, List[Dict[str, Any]]], Dict[str, Any]]:
    validation_domain_set = set(validation_domains)
    test_domain_set = set(test_domains)
    if validation_domain_set & test_domain_set:
        raise ValueError("validation and test held-out domains overlap")

    exact_seen = set()
    output: Dict[str, List[Dict[str, Any]]] = {
        "train": [],
        "validation": [],
        "test": [],
    }
    duplicates = []
    grouped: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    for source in records:
        record = deepcopy(dict(source))
        exact_hash = record["hashes"]["blueprint_sha256"]
        template_hash = record["hashes"]["template_sha256"]
        if exact_hash in exact_seen:
            duplicates.append(record["id"])
            continue
        exact_seen.add(exact_hash)
        grouped[template_hash].append(record)

    for template_hash, group in sorted(grouped.items()):
        domains = {record["task"]["domain"] for record in group}
        if domains & test_domain_set:
            split = "test"
        elif domains & validation_domain_set:
            split = "validation"
        else:
            split = _group_split(template_hash)
        for record in group:
            record["split"] = split
            output[split].append(record)

    split_templates = {
        split: {record["hashes"]["template_sha256"] for record in rows}
        for split, rows in output.items()
    }
    leakage = {
        "train_validation": sorted(split_templates["train"] & split_templates["validation"]),
        "train_test": sorted(split_templates["train"] & split_templates["test"]),
        "validation_test": sorted(split_templates["validation"] & split_templates["test"]),
    }
    report = {
        "input_records": len(records),
        "accepted_unique_records": sum(len(rows) for rows in output.values()),
        "exact_duplicates_removed": len(duplicates),
        "split_counts": {split: len(rows) for split, rows in output.items()},
        "unique_templates": {split: len(items) for split, items in split_templates.items()},
        "template_leakage": leakage,
        "domains": {
            split: dict(sorted(Counter(row["task"]["domain"] for row in rows).items()))
            for split, rows in output.items()
        },
        "quality": dataset_quality_report([row for rows in output.values() for row in rows]),
    }
    return output, report


def dataset_quality_report(records: Sequence[Mapping[str, Any]]) -> Dict[str, Any]:
    components: Counter[str] = Counter()
    functions: Counter[str] = Counter()
    locales: Counter[str] = Counter()
    states: Counter[str] = Counter()
    node_bands: Counter[str] = Counter()
    action_records = 0
    binding_records = 0

    def walk(value: Any):
        yield value
        if isinstance(value, dict):
            for child in value.values():
                yield from walk(child)
        elif isinstance(value, list):
            for child in value:
                yield from walk(child)

    for record in records:
        blueprint = record["blueprint"]
        count = len(blueprint["components"])
        if count <= 8:
            node_bands["4-8"] += 1
        elif count <= 20:
            node_bands["9-20"] += 1
        elif count <= 48:
            node_bands["21-48"] += 1
        else:
            node_bands["49-64"] += 1
        components.update(item["component"] for item in blueprint["components"])
        locales[blueprint["locale"]] += 1
        states[blueprint["state"]] += 1
        values = list(walk(blueprint["components"]))
        functions.update(
            value["function"]
            for value in values
            if isinstance(value, dict) and "function" in value
        )
        if blueprint["actions"]:
            action_records += 1
        if any(isinstance(value, dict) and set(value) == {"binding"} for value in values):
            binding_records += 1
    record_count = len(records)
    return {
        "record_count": record_count,
        "unique_blueprints": len({row["hashes"]["blueprint_sha256"] for row in records}),
        "unique_templates": len({row["hashes"]["template_sha256"] for row in records}),
        "component_frequency": dict(sorted(components.items())),
        "function_frequency": dict(sorted(functions.items())),
        "locale_frequency": dict(sorted(locales.items())),
        "state_frequency": dict(sorted(states.items())),
        "node_bands": dict(sorted(node_bands.items())),
        "data_bound_share": binding_records / record_count if record_count else 0.0,
        "interactive_share": action_records / record_count if record_count else 0.0,
    }


def write_jsonl(path: Path, rows: Iterable[Mapping[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as output:
        for row in rows:
            output.write(canonical_json(row) + "\n")


def write_dataset(output_dir: Path, records: Sequence[Mapping[str, Any]]) -> Dict[str, Any]:
    splits, report = assign_splits(records)
    output_dir.mkdir(parents=True, exist_ok=True)
    for split, rows in splits.items():
        write_jsonl(output_dir / f"{split}.jsonl", rows)
        sft = [
            {
                "messages": [
                    {
                        "role": "system",
                        "content": "Generate only valid A2UI Express for the pinned Assistant Catalog v1.",
                    },
                    {"role": "user", "content": row["task"]["request"]},
                    {"role": "assistant", "content": row["target"]["a2ui_express"]},
                ],
                "record_id": row["id"],
            }
            for row in rows
        ]
        write_jsonl(output_dir / f"{split}.sft.jsonl", sft)
    (output_dir / "split-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return report
