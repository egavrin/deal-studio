#!/usr/bin/env python3
"""Verify the immutable contract denominator for the UI-first component program.

The checked upstream contract is generated separately from a pinned json-render
checkout. This verifier never fetches or executes TypeScript: it proves that the
committed extraction, native adaptation ledger, gallery fixtures, and active
component pack name exactly the same source surface.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
import re
import sys


ALLOWED_STATUS = {"PENDING", "IMPLEMENTED", "VERIFIED", "UNSUPPORTED"}
ALLOWED_EVIDENCE_STATUS = {"PENDING", "PASS", "FAIL"}
REQUIRED_ORIGINS = {"shadcn": 36, "react-native": 26, "jev-playground": 2}
GALLERY_ROOT = Path("app/src/debug/assets/ui-catalog")
ACTIVE_PACK_MANIFEST = Path("tooling/deal-ui-pack/deal-studio-v16.agent.json")
REQUIRED_EVIDENCE_GATES = {
    "contractCompile",
    "interactive",
    "visual",
    "accessibility",
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def active_pack_components(pack_path: Path) -> set[str]:
    return set(
        re.findall(
            r"^export component ([A-Za-z][A-Za-z0-9_]*)\(",
            pack_path.read_text(encoding="utf-8"),
            re.MULTILINE,
        )
    )


def validate_gallery(known_pairs: set[tuple[str, str]]) -> dict[tuple[str, str], set[str]]:
    manifest_path = GALLERY_ROOT / "manifest.json"
    if not manifest_path.exists():
        return {}
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
        target_component = case.get("targetComponent", case.get("component"))
        if not isinstance(target_component, str) or not target_component:
            raise ValueError(f"UI catalog fixture {case_id} has an invalid targetComponent")
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
    return {
        pair: {
            case["id"]
            for case in cases
            if (case.get("origin"), case.get("component")) == pair
        }
        for pair in known_pairs
    }


def validate_contract(
    contract_path: Path,
    ledger: dict,
    ledger_pairs: set[tuple[str, str]],
) -> tuple[dict, dict[tuple[str, str], dict]]:
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    if contract.get("schemaVersion") != "deal-studio-json-render-contract-extraction-v1":
        raise ValueError("unsupported json-render contract extraction schema")
    if contract.get("requiredRows") != len(ledger_pairs):
        raise ValueError("upstream contract requiredRows does not match the frozen denominator")
    if contract.get("origins") != REQUIRED_ORIGINS:
        raise ValueError("upstream contract origin counts do not match the frozen denominator")
    source = contract.get("source")
    if not isinstance(source, dict) or source.get("repository") != ledger["source"]["repository"]:
        raise ValueError("upstream contract repository does not match the coverage ledger")
    if source.get("revision") != ledger["source"]["revision"]:
        raise ValueError("upstream contract revision does not match the coverage ledger")
    files = source.get("files")
    if not isinstance(files, dict) or set(files) != {
        "apps/web/lib/jev/grammar.ts",
        "packages/react-native/src/catalog.ts",
        "packages/shadcn/src/catalog.ts",
    }:
        raise ValueError("upstream contract must pin all catalog/Playground source file digests")
    if any(not isinstance(value, str) or len(value) != 64 for value in files.values()):
        raise ValueError("upstream contract source file digests must be SHA-256 strings")
    catalogs = contract.get("components")
    if not isinstance(catalogs, dict) or set(catalogs) != set(REQUIRED_ORIGINS):
        raise ValueError("upstream contract has an unexpected component-origin set")

    components: dict[tuple[str, str], dict] = {}
    for origin, expected_count in REQUIRED_ORIGINS.items():
        catalog = catalogs[origin]
        if not isinstance(catalog, dict) or len(catalog) != expected_count:
            raise ValueError(f"upstream contract {origin} count is not {expected_count}")
        for name, component in catalog.items():
            if not isinstance(name, str) or not isinstance(component, dict):
                raise ValueError(f"upstream contract {origin} has an invalid component entry")
            props = component.get("props")
            events = component.get("events")
            slots = component.get("slots")
            if not isinstance(props, dict) or not isinstance(events, list) or not isinstance(slots, list):
                raise ValueError(f"upstream contract {origin}.{name} needs props/events/slots")
            if len(events) != len(set(events)) or len(slots) != len(set(slots)):
                raise ValueError(f"upstream contract {origin}.{name} has duplicate events or slots")
            if any(not isinstance(prop, str) or not isinstance(shape, dict) for prop, shape in props.items()):
                raise ValueError(f"upstream contract {origin}.{name} has an invalid prop shape")
            if any(not isinstance(event, str) or not event for event in events):
                raise ValueError(f"upstream contract {origin}.{name} has an invalid event")
            if any(not isinstance(slot, str) or not slot for slot in slots):
                raise ValueError(f"upstream contract {origin}.{name} has an invalid slot")
            components[(origin, name)] = component
    if set(components) != ledger_pairs:
        missing = sorted(ledger_pairs - set(components))
        extra = sorted(set(components) - ledger_pairs)
        raise ValueError(f"upstream contract/ledger pairs differ; missing={missing}, extra={extra}")
    return contract, components


def require_disposition(
    value: object,
    context: str,
) -> None:
    if not isinstance(value, dict):
        raise ValueError(f"{context} needs an object disposition")
    state = value.get("state")
    if state not in ALLOWED_STATUS:
        raise ValueError(f"{context} has unknown state {state!r}")
    target = value.get("targetProperty", value.get("targetEvent"))
    if state in {"IMPLEMENTED", "VERIFIED"} and (not isinstance(target, str) or not target):
        raise ValueError(f"{context} is {state} but has no target mapping")
    if state == "UNSUPPORTED" and not isinstance(value.get("reason"), str):
        raise ValueError(f"{context} is UNSUPPORTED but has no explicit reason")


def validate_adaptations(
    adaptation_path: Path,
    contract_path: Path,
    contract: dict,
    source_components: dict[tuple[str, str], dict],
    ledger_rows: dict[tuple[str, str], dict],
    fixture_ids: dict[tuple[str, str], set[str]],
) -> dict:
    adaptations = json.loads(adaptation_path.read_text(encoding="utf-8"))
    if adaptations.get("schemaVersion") != "deal-studio-ui-adaptation-v1":
        raise ValueError("unsupported UI adaptation manifest schema")
    source_contract = adaptations.get("sourceContract")
    if not isinstance(source_contract, dict):
        raise ValueError("adaptation manifest needs sourceContract provenance")
    if source_contract.get("path") != str(contract_path):
        raise ValueError("adaptation manifest points at a different source contract path")
    if source_contract.get("sha256") != digest(contract_path):
        raise ValueError("adaptation manifest was not generated from the checked source contract")
    target_pack = adaptations.get("targetPack")
    if not isinstance(target_pack, dict):
        raise ValueError("adaptation manifest needs targetPack provenance")
    pack_path = Path(target_pack.get("sourcePath", ""))
    if pack_path.is_absolute() or not pack_path.is_file():
        raise ValueError("adaptation manifest target pack is unavailable")
    if target_pack.get("sha256") != digest(pack_path):
        raise ValueError("adaptation manifest target pack digest is stale")
    if not ACTIVE_PACK_MANIFEST.is_file():
        raise ValueError("active v16 pack manifest is missing")
    active_manifest = json.loads(ACTIVE_PACK_MANIFEST.read_text(encoding="utf-8"))
    if target_pack.get("version") != active_manifest.get("packVersion"):
        raise ValueError("adaptation manifest target pack is not the active pack")
    pack_components = active_pack_components(pack_path)

    rows = adaptations.get("rows")
    if adaptations.get("requiredRows") != len(source_components) or not isinstance(rows, list):
        raise ValueError("adaptation manifest needs exactly one row per upstream component")
    rows_by_pair: dict[tuple[str, str], dict] = {}
    for row in rows:
        pair = (row.get("origin"), row.get("component"))
        if pair in rows_by_pair or pair not in source_components:
            raise ValueError(f"adaptation row has an invalid or duplicate pair: {pair}")
        rows_by_pair[pair] = row
    if set(rows_by_pair) != set(source_components):
        raise ValueError("adaptation rows do not cover the exact upstream denominator")

    property_states: Counter[str] = Counter()
    event_states: Counter[str] = Counter()
    slot_states: Counter[str] = Counter()
    target_availability: Counter[str] = Counter()
    fixture_count = 0
    for pair, source in source_components.items():
        origin, name = pair
        row = rows_by_pair[pair]
        state = row.get("status")
        if state not in ALLOWED_STATUS:
            raise ValueError(f"adaptation row {origin}.{name} has unknown status {state!r}")
        if state != ledger_rows[pair]["status"]:
            raise ValueError(f"adaptation row {origin}.{name} status disagrees with the coverage ledger")
        target = row.get("target")
        if not isinstance(target, dict):
            raise ValueError(f"adaptation row {origin}.{name} needs a target")
        target_component = target.get("component")
        availability = target.get("availability")
        if not isinstance(target_component, str) or not target_component:
            raise ValueError(f"adaptation row {origin}.{name} has no target component")
        if availability not in {"active", "planned"}:
            raise ValueError(f"adaptation row {origin}.{name} has invalid target availability")
        if availability == "active" and target_component not in pack_components:
            raise ValueError(f"active target {origin}.{name}->{target_component} is not exported by the pack")
        target_availability[availability] += 1

        properties = row.get("properties")
        if not isinstance(properties, dict) or set(properties) != set(source["props"]):
            raise ValueError(f"adaptation row {origin}.{name} does not disposition every source prop")
        for prop, disposition in properties.items():
            require_disposition(disposition, f"{origin}.{name}.props.{prop}")
            property_states[disposition["state"]] += 1

        events = row.get("events")
        if not isinstance(events, dict) or set(events) != set(source["events"]):
            raise ValueError(f"adaptation row {origin}.{name} does not disposition every source event")
        for event, disposition in events.items():
            require_disposition(disposition, f"{origin}.{name}.events.{event}")
            event_states[disposition["state"]] += 1

        slots = row.get("slots")
        if not isinstance(slots, dict) or slots.get("source") != source["slots"]:
            raise ValueError(f"adaptation row {origin}.{name} has a stale source slot contract")
        slot_state = slots.get("state")
        if slot_state not in ALLOWED_STATUS:
            raise ValueError(f"adaptation row {origin}.{name} has invalid slot state")
        if slot_state in {"IMPLEMENTED", "VERIFIED"} and (
            not isinstance(slots.get("targetSlot"), str) or not slots["targetSlot"]
        ):
            raise ValueError(f"adaptation row {origin}.{name} has an implemented slot without a target")
        if slot_state == "UNSUPPORTED" and not isinstance(slots.get("reason"), str):
            raise ValueError(f"adaptation row {origin}.{name} has an unsupported slot without a reason")
        slot_states[slot_state] += 1

        row_fixture_ids = row.get("fixtureIds")
        if not isinstance(row_fixture_ids, list) or len(row_fixture_ids) != len(set(row_fixture_ids)):
            raise ValueError(f"adaptation row {origin}.{name} has invalid fixture IDs")
        unknown_fixture_ids = set(row_fixture_ids) - fixture_ids[pair]
        if unknown_fixture_ids:
            raise ValueError(f"adaptation row {origin}.{name} references unrelated fixtures {sorted(unknown_fixture_ids)}")
        fixture_count += len(row_fixture_ids)

        evidence = row.get("evidence")
        if not isinstance(evidence, dict) or set(evidence) != REQUIRED_EVIDENCE_GATES:
            raise ValueError(f"adaptation row {origin}.{name} needs the complete evidence gate set")
        if not set(evidence.values()) <= ALLOWED_EVIDENCE_STATUS:
            raise ValueError(f"adaptation row {origin}.{name} has an unknown evidence status")
        dispositions = [
            *(value["state"] for value in properties.values()),
            *(value["state"] for value in events.values()),
            slot_state,
        ]
        if state in {"IMPLEMENTED", "VERIFIED"} and "PENDING" in dispositions:
            raise ValueError(f"adaptation row {origin}.{name} is {state} with pending source obligations")
        if state == "VERIFIED" and (
            any(value != "PASS" for value in evidence.values())
            or any(value not in {"VERIFIED", "UNSUPPORTED"} for value in dispositions)
        ):
            raise ValueError(f"verified adaptation row {origin}.{name} lacks verified dispositions/evidence")

    return {
        "propertyStates": dict(sorted(property_states.items())),
        "eventStates": dict(sorted(event_states.items())),
        "slotStates": dict(sorted(slot_states.items())),
        "targetAvailability": dict(sorted(target_availability.items())),
        "fixtureLinks": fixture_count,
        "targetPack": target_pack,
    }


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
        "--contract",
        type=Path,
        default=Path("tooling/deal-ui-pack/vercel-json-render-contract-v1.json"),
        help="Checked prop/event extraction generated from the pinned upstream source.",
    )
    parser.add_argument(
        "--adaptations",
        type=Path,
        default=Path("tooling/deal-ui-pack/vercel-ui-adaptations-v1.json"),
        help="Per-source-property native adaptation and evidence ledger.",
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
    ledger_rows = {(row["origin"], row["name"]): row for row in rows}
    contract, source_components = validate_contract(args.contract, ledger, set(pairs))
    fixture_ids = validate_gallery(set(pairs)) if args.all else {}
    adaptation_summary = validate_adaptations(
        args.adaptations,
        args.contract,
        contract,
        source_components,
        ledger_rows,
        fixture_ids,
    )
    status_counts = dict(sorted(Counter(row["status"] for row in rows).items()))
    report = {
        "schemaVersion": "deal-studio-ui-catalog-contract-report-v2",
        "result": "PASS",
        "ledger": str(args.ledger),
        "contract": str(args.contract),
        "adaptations": str(args.adaptations),
        "source": source,
        "requiredRows": expected_rows,
        "origins": dict(sorted(origins.items())),
        "statuses": status_counts,
        "galleryCases": sum(len(ids) for ids in fixture_ids.values()),
        "adaptationSummary": adaptation_summary,
    }
    if args.report:
        args.report.mkdir(parents=True, exist_ok=True)
        (args.report / "contract-report.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
    print(
        "UI catalog contract passed: "
        + ", ".join(f"{origin}={origins[origin]}" for origin in sorted(origins))
        + f", rows={status_counts}, props={adaptation_summary['propertyStates']}, "
        + f"events={adaptation_summary['eventStates']}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"UI catalog contract failed: {error}", file=sys.stderr)
        raise SystemExit(1)
