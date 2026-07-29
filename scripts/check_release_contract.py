#!/usr/bin/env python3
import re
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


def main():
    manifest = ET.parse(ROOT / "app" / "src" / "main" / "AndroidManifest.xml").getroot()
    application = manifest.find("application")
    require(application is not None, "application manifest node is missing")
    require(application.get(f"{ANDROID}allowBackup") == "false", "app backup must remain disabled")
    require(application.get(f"{ANDROID}fullBackupContent") == "false", "full backup must remain disabled")
    require(application.get(f"{ANDROID}dataExtractionRules"), "data extraction rules are required")

    gradle = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    release_block = extract_named_block(gradle, "release")
    require('signingConfigs.getByName("debug")' not in release_block, "release must never use debug signing")
    require("isMinifyEnabled = true" in release_block, "release minification must remain enabled")
    require("isShrinkResources = true" in release_block, "release resource shrinking must remain enabled")

    secret_patterns = [
        re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b"),
        re.compile(r"\bexa_[A-Za-z0-9_-]{20,}\b", re.IGNORECASE),
    ]
    source_roots = [
        ROOT / "app" / "src",
        ROOT / "core" / "src",
        ROOT / "deepseek-connector" / "src",
    ]
    leaked = []
    for source_root in source_roots:
        for path in source_root.rglob("*"):
            if path.suffix not in {".kt", ".kts", ".xml", ".json", ".properties"}:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            if any(pattern.search(text) for pattern in secret_patterns):
                leaked.append(str(path.relative_to(ROOT)))
    require(not leaked, f"credential-like values found in sources: {leaked}")

    print("release_contract_ok backup=disabled signing=production_or_unsigned minified=true secrets=clean")


def extract_named_block(text, name):
    marker = f"{name} {{"
    start = text.find(marker)
    require(start >= 0, f"{name} block is missing")
    brace = text.find("{", start)
    depth = 0
    for index in range(brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[brace + 1:index]
    raise SystemExit(f"{name} block is not balanced")


def require(condition, message):
    if not condition:
        raise SystemExit(message)


if __name__ == "__main__":
    main()
