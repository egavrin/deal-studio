#!/usr/bin/env python3
"""Fails when removed assistant subsystems leak back into DEAL Studio."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
EXPECTED_MODULES = {
    ":app",
    ":deepseek-connector",
}
FORBIDDEN_PATHS = (
    "benchmark",
    "core",
    "appfunctions-experiment",
    "cloud-widget-connector",
    "third_party/whisper.cpp",
    "training/widget_planner",
    "core/src/main/kotlin/com/offlineassistant/core/widgets",
    "app/src/main/java/com/offlineassistant/app/shell",
    "app/src/main/java/com/offlineassistant/app/widgets/planning",
)
GENERATED_APP_EXPERIMENT_ROOT = ROOT / "app/src/main/java/com/offlineassistant/app/generatedapp"
FORBIDDEN_PRODUCTION_TERMS = re.compile(
    r"\b(qwen|llama|whisper|gemma|compose_widget|generatedWidget|appFunctions|"
    r"brainDump|personalMemory|routine)\b",
    re.IGNORECASE,
)
FORBIDDEN_BUILD_TERMS = re.compile(
    r"androidx\.appfunctions|androidx\.room|com\.google\.devtools\.ksp|"
    r"\b(qwen|llama|whisper|gemma)\b",
    re.IGNORECASE,
)


def contains_source(path: Path) -> bool:
    if path.is_file():
        return True
    return any(
        candidate.is_file() and "build" not in candidate.relative_to(path).parts
        for candidate in path.rglob("*")
    )


def main() -> int:
    failures: list[str] = []
    for relative in FORBIDDEN_PATHS:
        if contains_source(ROOT / relative):
            failures.append(f"forbidden path exists: {relative}")

    production_roots = (
        ROOT / "app/src/main/java",
    )
    for source_root in production_roots:
        for path in source_root.rglob("*"):
            if path.suffix not in {".kt", ".kts", ".xml"}:
                continue
            if path.is_relative_to(GENERATED_APP_EXPERIMENT_ROOT):
                continue
            match = FORBIDDEN_PRODUCTION_TERMS.search(path.read_text(encoding="utf-8"))
            if match:
                failures.append(
                    f"forbidden production term {match.group(0)!r}: {path.relative_to(ROOT)}"
                )
    for path in (ROOT / "app/proguard-rules.pro",):
        match = FORBIDDEN_PRODUCTION_TERMS.search(path.read_text(encoding="utf-8"))
        if match:
            failures.append(
                f"forbidden production term {match.group(0)!r}: {path.relative_to(ROOT)}"
            )

    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    actual_modules = set(re.findall(r'include\("([^"]+)"\)', settings))
    if actual_modules != EXPECTED_MODULES:
        failures.append(
            f"Gradle module allowlist mismatch: expected={sorted(EXPECTED_MODULES)} "
            f"actual={sorted(actual_modules)}"
        )

    build_files = (
        ROOT / "settings.gradle.kts",
        ROOT / "build.gradle.kts",
        ROOT / "app/build.gradle.kts",
        ROOT / "deepseek-connector/build.gradle.kts",
        ROOT / "gradle/libs.versions.toml",
        ROOT / "app/proguard-rules.pro",
    )
    for path in build_files:
        if path == ROOT / "app/build.gradle.kts":
            continue
        match = FORBIDDEN_BUILD_TERMS.search(path.read_text(encoding="utf-8"))
        if match:
            failures.append(
                f"forbidden build term {match.group(0)!r}: {path.relative_to(ROOT)}"
            )

    model_assets = ROOT / "app/src/main/assets/models"
    # The production model payload is deliberately absent from a normal source
    # checkout (and from PR CI). When a release assembly materializes it, retain
    # the strict allowlist check; do not turn an absent optional payload into an
    # unrelated architecture-gate failure.
    if model_assets.exists():
        actual_model_directories = {path.name for path in model_assets.iterdir() if path.is_dir()}
        if actual_model_directories != {"rubert", "tone_ru"}:
            failures.append(
                f"packaged model assets mismatch: expected=['rubert', 'tone_ru'] "
                f"actual={sorted(actual_model_directories)}"
            )

    if failures:
        print("Core scope check failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    print("Core scope check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
