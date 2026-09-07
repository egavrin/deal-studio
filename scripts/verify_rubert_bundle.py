#!/usr/bin/env python3
"""Verify the production RuBERT runtime bundle committed under APK assets."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BUNDLE_DIR = ROOT / "app/src/main/assets/models/rubert"
MANIFEST_PATH = BUNDLE_DIR / "runtime-bundle.json"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    files = manifest.get("files")
    if not isinstance(files, dict) or not files:
        raise SystemExit("RuBERT manifest has no runtime files")

    expected_names = {
        "rubert-tiny2-intent-slots.onnx",
        "vocab.txt",
        "intent_labels.txt",
        "slot_labels.txt",
    }
    if set(files) != expected_names:
        raise SystemExit(f"Unexpected RuBERT runtime files: {sorted(files)}")

    for name, expected in files.items():
        path = BUNDLE_DIR / name
        if not path.is_file():
            raise SystemExit(f"Missing RuBERT runtime file: {name}")
        actual_size = path.stat().st_size
        if actual_size != expected["bytes"]:
            raise SystemExit(f"{name}: expected {expected['bytes']} bytes, got {actual_size}")
        actual_hash = sha256(path)
        if actual_hash != expected["sha256"]:
            raise SystemExit(f"{name}: SHA-256 mismatch")

    print(
        f"RuBERT bundle {manifest['bundle_version']} verified: "
        f"{len(files)} files, {sum(item['bytes'] for item in files.values())} bytes"
    )


if __name__ == "__main__":
    main()
