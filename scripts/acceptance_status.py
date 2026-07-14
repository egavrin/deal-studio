#!/usr/bin/env python3
"""Summarize current acceptance evidence for the Android Offline Assistant PoC."""

from __future__ import annotations

import json
import sys
from pathlib import Path


HOST_FILES = [
    "rubert-host-eval-full-acceptance.jsonl",
    "rubert-host-eval-full-acceptance-metrics.json",
]
DEVICE_SMOKE_FILES = [
    "rubert-host-eval.jsonl",
    "rubert-host-eval-metrics.json",
    "qwen-answer-eval.jsonl",
    "rubert-slot-eval.jsonl",
    "whisper-asr-eval.jsonl",
    "offline-assistant-demo-flow.mp4",
    "offline-assistant-device-smoke.png",
    "offline-connectivity.txt",
    "logcat-tail.txt",
]
LIVE_VOICE_FILES = [
    "rubert-host-eval-live-voice.jsonl",
    "rubert-host-eval-live-voice-metrics.json",
    "offline-assistant-live-voice.mp4",
    "offline-assistant-live-voice.png",
    "offline-assistant-live-voice-uiautomator.xml",
    "offline-assistant-live-voice-debug-uiautomator.xml",
    "offline-assistant-live-voice-logcat.txt",
]


def missing_files(directory: Path, names: list[str]) -> list[str]:
    missing = []
    for name in names:
        path = directory / name
        if not path.is_file() or path.stat().st_size <= 0:
            missing.append(name)
    return missing


def group_status(directory: Path, names: list[str]) -> dict:
    missing = missing_files(directory, names)
    return {
        "status": "present" if not missing else "missing",
        "required": names,
        "missing": missing,
    }


def build_status(directory: Path) -> dict:
    host = group_status(directory, HOST_FILES)
    device = group_status(directory, DEVICE_SMOKE_FILES)
    live = group_status(directory, LIVE_VOICE_FILES)
    missing = (
        [f"host_preflight:{name}" for name in host["missing"]]
        + [f"device_smoke:{name}" for name in device["missing"]]
        + [f"live_voice:{name}" for name in live["missing"]]
    )
    complete = not missing
    next_step = (
        "Acceptance evidence is complete; verify docs and goal audit before marking done."
        if complete
        else "Run scripts/run_full_acceptance.sh --wait-for-device; use --host-only only for local readiness."
    )
    return {
        "artifact_dir": str(directory),
        "complete": complete,
        "host_preflight": host,
        "device_smoke": device,
        "live_voice": live,
        "missing": missing,
        "next_step": next_step,
    }


def main(argv: list[str]) -> int:
    directory = Path(argv[1]) if len(argv) > 1 else Path("build/device-smoke")
    payload = build_status(directory)
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if payload["complete"] else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
