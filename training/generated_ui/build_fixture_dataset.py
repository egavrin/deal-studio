#!/usr/bin/env python3
"""Build the small checked-in generated UI compiler fixture corpus."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from training.generated_ui.dataset import build_record, write_dataset, write_jsonl  # noqa: E402
from training.generated_ui.fixtures import all_blueprints  # noqa: E402


ROOT = Path(__file__).resolve().parent


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "data" / "fixture")
    args = parser.parse_args()
    records = [
        build_record(
            blueprint,
            {
                "generator": "hand-authored-fixture",
                "prompt_version": "fixture-v1",
                "request_sha256": None,
                "response_sha256": None,
            },
        )
        for blueprint in all_blueprints()
    ]
    write_jsonl(args.output / "accepted.jsonl", records)
    report = write_dataset(args.output / "splits", records)
    print(json.dumps(report, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
