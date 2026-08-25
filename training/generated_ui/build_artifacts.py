#!/usr/bin/env python3
"""Build or check catalog-derived schema, manifest and prompt signatures."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    import sys

    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from training.generated_ui.catalog import (  # noqa: E402
    ASSISTANT_CATALOG_ID,
    BASIC_CATALOG_ID,
    COMPONENTS,
    FUNCTIONS,
    UPSTREAM_BASIC_CATALOG_ID,
    UPSTREAM_BASIC_CATALOG_SHA256,
    UPSTREAM_COMMIT,
    catalog_prompt_signatures,
    component_catalog,
)
from training.generated_ui.schema import (  # noqa: E402
    build_blueprint_schema,
    build_teacher_response_schema,
)


ROOT = Path(__file__).resolve().parent


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def digest(value: Any) -> str:
    return hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()


def artifacts() -> dict[Path, str]:
    schema = build_blueprint_schema()
    teacher_schema = build_teacher_response_schema()
    catalog = component_catalog()
    signatures = catalog_prompt_signatures() + "\n"
    manifest = {
        "manifest_version": 1,
        "protocol_profile": "a2ui-v1.0-candidate-internal",
        "express_profile": "assistant-express-v1-keyword-topological",
        "upstream": {
            "repository": "a2ui-project/a2ui",
            "commit": UPSTREAM_COMMIT,
            "basic_catalog_id": UPSTREAM_BASIC_CATALOG_ID,
            "basic_catalog_sha256": UPSTREAM_BASIC_CATALOG_SHA256,
        },
        "catalog_ids": [BASIC_CATALOG_ID, ASSISTANT_CATALOG_ID],
        "component_count": len(COMPONENTS),
        "function_count": len(FUNCTIONS),
        "components": [component.name for component in COMPONENTS],
        "functions": list(FUNCTIONS),
        "artifacts": {
            "catalog_sha256": digest(catalog),
            "ui_blueprint_schema_sha256": digest(schema),
            "teacher_response_schema_sha256": digest(teacher_schema),
            "signatures_sha256": hashlib.sha256(signatures.encode("utf-8")).hexdigest(),
        },
    }
    pretty = lambda value: json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    return {
        ROOT / "catalogs" / "assistant-v1.catalog.json": pretty(catalog),
        ROOT / "schemas" / "ui-blueprint-v1.schema.json": pretty(schema),
        ROOT / "schemas" / "ui-blueprint-teacher-v1.schema.json": pretty(teacher_schema),
        ROOT / "catalogs" / "assistant-v1.signatures.txt": signatures,
        ROOT / "catalog-manifest.json": pretty(manifest),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Fail if generated artifacts drift")
    args = parser.parse_args()
    stale = []
    for path, content in artifacts().items():
        if args.check:
            if not path.is_file() or path.read_text(encoding="utf-8") != content:
                stale.append(str(path.relative_to(ROOT)))
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    if stale:
        print("Generated UI artifacts are stale: " + ", ".join(stale))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
