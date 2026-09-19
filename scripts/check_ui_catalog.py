#!/usr/bin/env python3
"""Verify the immutable denominator for the UI-first component program.

This validates the checked coverage ledger only.  Extracting prop-level upstream
contracts is deliberately a separate build-time operation: production runtime
must not parse TypeScript or fetch json-render.
"""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
import sys


ALLOWED_STATUS = {"PENDING", "IMPLEMENTED", "VERIFIED", "UNSUPPORTED"}
REQUIRED_ORIGINS = {"shadcn": 36, "react-native": 26, "jev-playground": 2}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--ledger",
        type=Path,
        default=Path("tooling/deal-ui-pack/vercel-ui-coverage-v2.json"),
    )
    args = parser.parse_args()
    ledger = json.loads(args.ledger.read_text(encoding="utf-8"))
    rows = ledger.get("rows")
    if ledger.get("schemaVersion") != 1 or not isinstance(rows, list):
        raise ValueError("UI catalog ledger must use schemaVersion 1 and a rows array")
    if ledger.get("requiredRows") != len(rows):
        raise ValueError("requiredRows must equal the exact tracked row count")
    expected_rows = sum(REQUIRED_ORIGINS.values())
    if len(rows) != expected_rows:
        raise ValueError(f"expected {expected_rows} required rows, found {len(rows)}")
    pairs = [(row.get("origin"), row.get("name")) for row in rows]
    if any(not origin or not name for origin, name in pairs):
        raise ValueError("every row needs non-empty origin and name")
    if len(set(pairs)) != len(pairs):
        raise ValueError("duplicate origin/name rows are forbidden")
    origins = Counter(origin for origin, _ in pairs)
    if origins != REQUIRED_ORIGINS:
        raise ValueError(f"unexpected origin denominator: {dict(origins)}")
    statuses = {row.get("status") for row in rows}
    if not statuses <= ALLOWED_STATUS:
        raise ValueError(f"unknown coverage status: {sorted(statuses - ALLOWED_STATUS)}")
    source = ledger.get("source", {})
    if not source.get("repository") or not source.get("revision"):
        raise ValueError("source repository and revision are required")
    print(
        "UI catalog ledger passed: "
        + ", ".join(f"{origin}={origins[origin]}" for origin in sorted(origins))
        + f", statuses={dict(sorted(Counter(row['status'] for row in rows).items()))}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"UI catalog ledger failed: {error}", file=sys.stderr)
        raise SystemExit(1)
