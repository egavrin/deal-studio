#!/usr/bin/env python3
"""Evaluate Definition-of-Done gates against current acceptance evidence."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from acceptance_status import build_status


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_GATES = ROOT / "docs" / "acceptance" / "final-dod-gates.json"
REPO_AUDIT_FILES = [
    ROOT / "AGENTS.md",
    ROOT / "docs" / "acceptance" / "full-spec-audit.md",
    ROOT / "docs" / "superpowers" / "specs" / "2026-07-09-android-offline-assistant-poc-design.md",
]


def load_gates(path: Path) -> list[dict]:
    data = json.loads(path.read_text())
    items = data.get("items")
    if not isinstance(items, list) or not items:
        raise SystemExit(f"No DoD items found in {path}")
    return items


def repo_audit_present() -> bool:
    return all(path.is_file() and path.stat().st_size > 0 for path in REPO_AUDIT_FILES)


def evidence_availability(artifact_dir: Path) -> dict[str, bool]:
    status = build_status(artifact_dir)
    return {
        "host_preflight": status["host_preflight"]["status"] == "present",
        "device_smoke": status["device_smoke"]["status"] == "present",
        "live_voice": status["live_voice"]["status"] == "present",
        "repo_audit": repo_audit_present(),
    }


def evaluate(artifact_dir: Path, gates_path: Path) -> dict:
    gates = load_gates(gates_path)
    available = evidence_availability(artifact_dir)
    evaluated = []
    missing_groups: set[str] = set()
    for gate in gates:
        groups = gate.get("evidence_groups", [])
        missing = [group for group in groups if not available.get(group, False)]
        status = "proven" if not missing else "missing_evidence"
        missing_groups.update(missing)
        evaluated.append(
            {
                "id": gate["id"],
                "requirement": gate["requirement"],
                "evidence_groups": groups,
                "status": status,
                "missing_evidence_groups": missing,
            },
        )
    complete = all(item["status"] == "proven" for item in evaluated)
    return {
        "complete": complete,
        "artifact_dir": str(artifact_dir),
        "gates": str(gates_path),
        "requirement_count": len(evaluated),
        "available_evidence_groups": available,
        "missing_evidence_groups": sorted(missing_groups),
        "items": evaluated,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-dir", default="build/device-smoke")
    parser.add_argument("--gates", default=str(DEFAULT_GATES))
    args = parser.parse_args()

    payload = evaluate(Path(args.artifact_dir), Path(args.gates))
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if payload["complete"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
