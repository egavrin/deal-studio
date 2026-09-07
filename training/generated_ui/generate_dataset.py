#!/usr/bin/env python3
"""Generate, validate and split a DeepSeek Flash UI dataset pilot."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from training.generated_ui.coverage import build_task_seeds, coverage_report  # noqa: E402
from training.generated_ui.catalog import catalog_teacher_reference  # noqa: E402
from training.generated_ui.dataset import build_record, write_dataset, write_jsonl  # noqa: E402
from training.generated_ui.deepseek_client import (  # noqa: E402
    DeepSeekGenerationError,
    DeepSeekUiTeacher,
)
from training.generated_ui.pipeline import (  # noqa: E402
    BLUEPRINT_SCHEMA,
    BlueprintValidationError,
    adapt_teacher_blueprint,
    validate_task_alignment,
)
from training.generated_ui.schema import build_teacher_response_schema  # noqa: E402


ROOT = Path(__file__).resolve().parent


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def task_seed_sha256(value: Dict[str, Any]) -> str:
    return hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()


def percentile(values: List[float], fraction: float) -> Optional[float]:
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, math.ceil(fraction * len(ordered)))
    return ordered[rank - 1]


def rejection_taxonomy(rows: List[Dict[str, Any]], attempt: int) -> Dict[str, int]:
    counts: Dict[str, int] = {}
    for row in rows:
        errors = row.get("attempt_errors", [])
        if len(errors) <= attempt:
            continue
        for diagnostic in errors[attempt].split("; "):
            match = re.match(r"([a-z_]+) at \$", diagnostic)
            code = match.group(1) if match else diagnostic.split(":", 1)[0]
            counts[code] = counts.get(code, 0) + 1
    return dict(sorted(counts.items(), key=lambda item: (-item[1], item[0])))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=1000)
    parser.add_argument("--seed", type=int, default=20260825)
    parser.add_argument("--model", default="deepseek-v4-flash")
    parser.add_argument(
        "--reasoning-effort",
        default="none",
        choices=("none", "minimal", "low"),
        help="DeepSeek Responses reasoning budget; none is the dataset default",
    )
    parser.add_argument(
        "--retry-reasoning-effort",
        default="none",
        choices=("none", "minimal", "low"),
        help="Reasoning budget used only for the targeted validation retry",
    )
    parser.add_argument(
        "--final-retry-reasoning-effort",
        default="none",
        choices=("none", "minimal", "low"),
        help="Reasoning budget for an optional third and final correction",
    )
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--cache", type=Path, default=ROOT / ".cache" / "deepseek")
    parser.add_argument("--max-attempts", type=int, default=3, choices=(1, 2, 3))
    parser.add_argument("--concurrency", type=int, default=8, choices=range(1, 65), metavar="1-64")
    parser.add_argument(
        "--tasks-only",
        action="store_true",
        help="Write the deterministic coverage schedule without calling DeepSeek",
    )
    args = parser.parse_args()

    tasks = build_task_seeds(args.count, args.seed)
    task_hashes = {
        task.seed_id: task_seed_sha256(task.to_dict())
        for task in tasks
    }
    args.output.mkdir(parents=True, exist_ok=True)
    write_jsonl(args.output / "task-seeds.jsonl", (task.to_dict() for task in tasks))
    schedule_report = coverage_report(tasks)
    (args.output / "coverage-schedule.json").write_text(
        json.dumps(schedule_report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    if args.tasks_only:
        print(json.dumps(schedule_report["focal_components"], sort_keys=True))
        return 0

    instructions = (ROOT / "prompts" / "deepseek-ui-teacher-v1.md").read_text(
        encoding="utf-8"
    )
    instructions += "\n\nPinned catalog signatures:\n" + catalog_teacher_reference()
    try:
        teacher = DeepSeekUiTeacher(
            model=args.model,
            cache_dir=args.cache,
            reasoning_effort=args.reasoning_effort,
        )
    except DeepSeekGenerationError as error:
        print(str(error), file=sys.stderr)
        return 2

    checkpoint_path = args.output / "accepted.checkpoint.jsonl"
    accepted_by_seed: Dict[str, Dict[str, Any]] = {}
    if checkpoint_path.is_file():
        for line in checkpoint_path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            record = json.loads(line)
            seed_id = record.get("provenance", {}).get("task_seed_id")
            recorded_hash = record.get("provenance", {}).get("task_seed_sha256")
            if (
                isinstance(seed_id, str)
                and recorded_hash == task_hashes.get(seed_id)
            ):
                accepted_by_seed[seed_id] = record
    rejected: List[Dict[str, Any]] = []
    usage_totals: Dict[str, float] = {}
    for record in accepted_by_seed.values():
        prior_usage = record.get("provenance", {}).get(
            "aggregate_usage",
            record.get("provenance", {}).get("usage", {}),
        )
        for key, value in prior_usage.items():
            if isinstance(value, (int, float)):
                usage_totals[key] = usage_totals.get(key, 0) + value
    first_pass = sum(
        record.get("provenance", {}).get("generation_attempt") == 1
        for record in accepted_by_seed.values()
    )

    def process(task) -> Tuple[Optional[Dict[str, Any]], Optional[Dict[str, Any]], Dict[str, Any], int]:
        feedback = None
        last_error = None
        attempt_errors = []
        aggregate_usage: Dict[str, float] = {}
        retry_candidate = None
        for attempt in range(1, args.max_attempts + 1):
            try:
                generated = teacher.generate(
                    task=task.to_dict(),
                    instructions=instructions,
                    schema=build_teacher_response_schema(task.required_components),
                    retry_feedback=feedback,
                    retry_candidate=retry_candidate,
                    reasoning_effort=(
                        args.reasoning_effort
                        if attempt == 1
                        else (
                            args.retry_reasoning_effort
                            if attempt == 2
                            else args.final_retry_reasoning_effort
                        )
                    ),
                )
                for key, value in generated.usage.items():
                    if isinstance(value, (int, float)):
                        aggregate_usage[key] = aggregate_usage.get(key, 0) + value
                retry_candidate = generated.blueprint
                blueprint = adapt_teacher_blueprint(generated.blueprint)
                validate_task_alignment(blueprint, task.to_dict())
                provenance = dict(generated.provenance)
                provenance["task_seed_id"] = task.seed_id
                provenance["task_seed_sha256"] = task_hashes[task.seed_id]
                provenance["generation_attempt"] = attempt
                provenance["usage"] = generated.usage
                provenance["aggregate_usage"] = aggregate_usage
                provenance["prior_validation_errors"] = attempt_errors
                record = build_record(blueprint, provenance)
                return record, None, aggregate_usage, attempt
            except (DeepSeekGenerationError, BlueprintValidationError, ValueError) as error:
                if isinstance(error, DeepSeekGenerationError):
                    for key, value in error.usage.items():
                        if isinstance(value, (int, float)):
                            aggregate_usage[key] = aggregate_usage.get(key, 0) + value
                    if error.candidate is not None:
                        retry_candidate = error.candidate
                last_error = str(error)
                attempt_errors.append(last_error[:6000])
                feedback = "\n".join(attempt_errors[-2:])[-3000:]
        return (
            None,
            {
                "seed_id": task.seed_id,
                "task": task.to_dict(),
                "error": (last_error or "unknown generation failure")[:6000],
                "attempt_errors": attempt_errors,
            },
            aggregate_usage,
            args.max_attempts,
        )

    pending = [task for task in tasks if task.seed_id not in accepted_by_seed]
    completed = len(tasks) - len(pending)
    if pending:
        with ThreadPoolExecutor(max_workers=min(args.concurrency, len(pending))) as executor:
            futures = {executor.submit(process, task): task for task in pending}
            for future in as_completed(futures):
                record, rejection, usage, attempt = future.result()
                if record is not None:
                    seed_id = record["provenance"]["task_seed_id"]
                    accepted_by_seed[seed_id] = record
                    with checkpoint_path.open("a", encoding="utf-8") as checkpoint:
                        checkpoint.write(json.dumps(record, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n")
                    if attempt == 1:
                        first_pass += 1
                    for key, value in usage.items():
                        if isinstance(value, (int, float)):
                            usage_totals[key] = usage_totals.get(key, 0) + value
                elif rejection is not None:
                    rejected.append(rejection)
                    for key, value in usage.items():
                        if isinstance(value, (int, float)):
                            usage_totals[key] = usage_totals.get(key, 0) + value
                completed += 1
                print(
                    f"[{completed}/{len(tasks)}] accepted={len(accepted_by_seed)} rejected={len(rejected)}",
                    file=sys.stderr,
                )

    accepted = [accepted_by_seed[task.seed_id] for task in tasks if task.seed_id in accepted_by_seed]
    latency_values = [
        float(value)
        for record in accepted
        for value in [record.get("provenance", {}).get("host_elapsed_ms")]
        if isinstance(value, (int, float))
    ]

    write_jsonl(args.output / "accepted.jsonl", accepted)
    write_jsonl(args.output / "rejected.jsonl", rejected)
    split_report = write_dataset(args.output / "splits", accepted)
    report = {
        "requested": len(tasks),
        "accepted": len(accepted),
        "rejected": len(rejected),
        "first_pass_acceptance_rate": first_pass / len(tasks),
        "final_acceptance_rate": len(accepted) / len(tasks),
        "resumed_records": len(tasks) - len(pending),
        "concurrency": args.concurrency,
        "reasoning_effort": args.reasoning_effort,
        "retry_reasoning_effort": args.retry_reasoning_effort,
        "final_retry_reasoning_effort": args.final_retry_reasoning_effort,
        "usage": usage_totals,
        "latency_ms": {
            "count": len(latency_values),
            "min": min(latency_values) if latency_values else None,
            "p50": percentile(latency_values, 0.50),
            "p95": percentile(latency_values, 0.95),
            "max": max(latency_values) if latency_values else None,
        },
        "accepted_by_attempt": {
            "first": sum(
                record.get("provenance", {}).get("generation_attempt") == 1
                for record in accepted
            ),
            "retry": sum(
                record.get("provenance", {}).get("generation_attempt") == 2
                for record in accepted
            ),
            "final_retry": sum(
                record.get("provenance", {}).get("generation_attempt") == 3
                for record in accepted
            ),
        },
        "rejection_taxonomy": {
            "first_attempt": rejection_taxonomy(rejected, 0),
            "retry_attempt": (
                rejection_taxonomy(rejected, 1)
                if args.max_attempts >= 2
                else {}
            ),
            "final_attempt": rejection_taxonomy(rejected, args.max_attempts - 1),
        },
        "coverage": schedule_report,
        "splits": split_report,
    }
    (args.output / "generation-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps({key: report[key] for key in ("requested", "accepted", "rejected", "first_pass_acceptance_rate", "final_acceptance_rate")}, sort_keys=True))
    return 0 if not rejected else 1


if __name__ == "__main__":
    raise SystemExit(main())
