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
GALLERY_ROOT = Path("app/src/debug/assets/ui-catalog")


def validate_gallery(known_pairs: set[tuple[str, str]]) -> int:
    manifest_path = GALLERY_ROOT / "manifest.json"
    if not manifest_path.exists():
        return 0
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != "deal-studio-ui-catalog-fixtures-v1":
        raise ValueError("unsupported UI catalog fixture manifest")
    cases = manifest.get("cases")
    if not isinstance(cases, list) or not cases:
        raise ValueError("UI catalog fixture manifest needs at least one case")
    ids: set[str] = set()
    for case in cases:
        case_id = case.get("id")
        pair = (case.get("origin"), case.get("component"))
        if not case_id or case_id in ids:
            raise ValueError("UI catalog fixture IDs must be non-empty and unique")
        ids.add(case_id)
        if pair not in known_pairs:
            raise ValueError(f"UI catalog fixture {case_id} is outside the frozen denominator")
        pack_version = case.get("packVersion", "")
        if not pack_version.startswith("deal-studio-dealui-pack-v"):
            raise ValueError(f"UI catalog fixture {case_id} has an invalid packVersion")
        pack_number = pack_version.rsplit("v", 1)[1]
        if not Path(f"tooling/deal-ui-pack/deal-studio-v{pack_number}.dealui-pack").is_file():
            raise ValueError(f"UI catalog fixture {case_id} references an unavailable pack")
        for field in ("deal", "dealUi"):
            relative = Path(case.get(field, ""))
            if relative.is_absolute() or ".." in relative.parts or relative.parts[:1] != ("ui-catalog",):
                raise ValueError(f"UI catalog fixture {case_id}.{field} is not an allowlisted asset path")
            target = Path("app/src/debug/assets") / relative
            if not target.is_file():
                raise ValueError(f"UI catalog fixture asset is missing: {target}")
        template = (Path("app/src/debug/assets") / case["dealUi"]).read_text(encoding="utf-8")
        if template.count("ui.__STYLE__") != 1:
            raise ValueError(f"UI catalog fixture {case_id} must have exactly one style slot")
    return len(cases)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--all",
        action="store_true",
        help="Validate the complete frozen denominator (currently the only supported mode).",
    )
    parser.add_argument(
        "--ledger",
        type=Path,
        default=Path("tooling/deal-ui-pack/vercel-ui-coverage-v2.json"),
    )
    parser.add_argument(
        "--report",
        type=Path,
        help="Write a deterministic JSON contract report to this directory.",
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
    gallery_cases = validate_gallery(set(pairs)) if args.all else 0
    status_counts = dict(sorted(Counter(row["status"] for row in rows).items()))
    report = {
        "schemaVersion": "deal-studio-ui-catalog-contract-report-v1",
        "result": "PASS",
        "ledger": str(args.ledger),
        "source": source,
        "requiredRows": expected_rows,
        "origins": dict(sorted(origins.items())),
        "statuses": status_counts,
        "galleryCases": gallery_cases,
    }
    if args.report:
        args.report.mkdir(parents=True, exist_ok=True)
        (args.report / "contract-report.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
    print(
        "UI catalog ledger passed: "
        + ", ".join(f"{origin}={origins[origin]}" for origin in sorted(origins))
        + f", statuses={status_counts}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"UI catalog ledger failed: {error}", file=sys.stderr)
        raise SystemExit(1)
