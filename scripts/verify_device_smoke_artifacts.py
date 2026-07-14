#!/usr/bin/env python3
import json
import sys
from pathlib import Path


REQUIRED_FILES = [
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


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def require_non_empty(path: Path) -> None:
    if not path.is_file():
        fail(f"Missing artifact: {path.name}")
    if path.stat().st_size <= 0:
        fail(f"Empty artifact: {path.name}")


def read_jsonl(path: Path) -> list[dict]:
    require_non_empty(path)
    rows = []
    for line_number, line in enumerate(path.read_text().splitlines(), start=1):
        if not line.strip():
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError as exc:
            fail(f"Invalid JSON in {path.name}:{line_number}: {exc}")
        if not isinstance(value, dict):
            fail(f"Expected JSON object in {path.name}:{line_number}")
        rows.append(value)
    if not rows:
        fail(f"No JSONL rows in {path.name}")
    return rows


def require_any_key(row: dict, keys: set[str], file_name: str) -> None:
    if not keys.intersection(row):
        fail(f"{file_name} row is missing one of {sorted(keys)}")


def verify_qwen(path: Path) -> None:
    rows = read_jsonl(path)
    for row in rows:
        require_any_key(row, {"question"}, path.name)
        require_any_key(row, {"latency_ms"}, path.name)
        require_any_key(row, {"answer_chars", "answer_preview"}, path.name)


def verify_rubert(path: Path) -> None:
    rows = read_jsonl(path)
    for row in rows:
        require_any_key(row, {"text"}, path.name)
        require_any_key(row, {"slot_key", "intent"}, path.name)
        require_any_key(row, {"exact"}, path.name)


def verify_rubert_host(path: Path) -> None:
    rows = read_jsonl(path)
    for row in rows:
        require_any_key(row, {"text"}, path.name)
        if "intent" not in row:
            require_any_key(row, {"actual_intent", "predicted_intent"}, path.name)
            require_any_key(row, {"expected_intent"}, path.name)
        require_any_key(row, {"exact"}, path.name)


def verify_rubert_metrics(path: Path) -> None:
    require_non_empty(path)
    try:
        metrics = json.loads(path.read_text())
    except json.JSONDecodeError as exc:
        fail(f"Invalid JSON in {path.name}: {exc}")
    required = {
        "intent_accuracy",
        "intent_macro_f1",
        "slot_f1",
        "confusion_matrix",
        "validation_pass_rate",
    }
    missing = sorted(required.difference(metrics))
    if missing:
        fail(f"{path.name} is missing required metrics: {missing}")


def verify_whisper(path: Path) -> None:
    rows = read_jsonl(path)
    for row in rows:
        require_any_key(row, {"audio", "case"}, path.name)
        require_any_key(row, {"transcript"}, path.name)
        require_any_key(row, {"widget", "widget_type"}, path.name)


def verify_media(path: Path, expected_prefix: bytes) -> None:
    require_non_empty(path)
    prefix = path.read_bytes()[: len(expected_prefix)]
    if prefix != expected_prefix:
        fail(f"Unexpected signature for {path.name}")


def verify_offline_connectivity(path: Path) -> None:
    require_non_empty(path)
    text = path.read_text()
    if "oem_deny_chain=chain:enabled" not in text:
        fail(f"{path.name} does not contain required offline marker: oem_deny_chain=chain:enabled")
    if "package_networking_com.offlineassistant.poc.debug=com.offlineassistant.poc.debug:deny" not in text:
        fail(
            f"{path.name} does not contain required offline marker: "
            "package_networking_com.offlineassistant.poc.debug=com.offlineassistant.poc.debug:deny",
        )


def verify(artifact_dir: Path) -> None:
    if not artifact_dir.is_dir():
        fail(f"Artifact directory does not exist: {artifact_dir}")

    for file_name in REQUIRED_FILES:
        require_non_empty(artifact_dir / file_name)

    verify_rubert_host(artifact_dir / "rubert-host-eval.jsonl")
    verify_rubert_metrics(artifact_dir / "rubert-host-eval-metrics.json")
    verify_qwen(artifact_dir / "qwen-answer-eval.jsonl")
    verify_rubert(artifact_dir / "rubert-slot-eval.jsonl")
    verify_whisper(artifact_dir / "whisper-asr-eval.jsonl")
    verify_media(artifact_dir / "offline-assistant-demo-flow.mp4", b"\x00\x00\x00")
    verify_media(artifact_dir / "offline-assistant-device-smoke.png", b"\x89PNG")
    verify_offline_connectivity(artifact_dir / "offline-connectivity.txt")


def verify_live_voice(artifact_dir: Path) -> None:
    if not artifact_dir.is_dir():
        fail(f"Artifact directory does not exist: {artifact_dir}")

    for file_name in LIVE_VOICE_FILES:
        require_non_empty(artifact_dir / file_name)

    verify_rubert_host(artifact_dir / "rubert-host-eval-live-voice.jsonl")
    verify_rubert_metrics(artifact_dir / "rubert-host-eval-live-voice-metrics.json")
    verify_media(artifact_dir / "offline-assistant-live-voice.mp4", b"\x00\x00\x00")
    verify_media(artifact_dir / "offline-assistant-live-voice.png", b"\x89PNG")

    ui_dump = artifact_dir / "offline-assistant-live-voice-uiautomator.xml"
    ui_text = ui_dump.read_text()
    if "Таймер" not in ui_text:
        fail(f"{ui_dump.name} does not contain expected text: Таймер")
    timer_result_texts = ["Поставил таймер", "Таймер создан в системном приложении."]
    if not any(expected in ui_text for expected in timer_result_texts):
        fail(
            f"{ui_dump.name} contains neither supported timer result: "
            + " | ".join(timer_result_texts)
        )

    debug_dump = artifact_dir / "offline-assistant-live-voice-debug-uiautomator.xml"
    debug_text = debug_dump.read_text()
    for expected in ["transcript:", "intent: set_timer", "source: RUBERT_TINY2", "fallback: false", "latency asr"]:
        if expected not in debug_text:
            fail(f"{debug_dump.name} does not contain expected text: {expected}")


def main(argv: list[str]) -> int:
    live_voice = "--live-voice" in argv[1:]
    paths = [arg for arg in argv[1:] if arg != "--live-voice"]
    artifact_dir = Path(paths[0]) if paths else Path("build/device-smoke")
    if live_voice:
        verify_live_voice(artifact_dir)
        print(f"Verified live voice artifacts in {artifact_dir}")
    else:
        verify(artifact_dir)
        print(f"Verified device smoke artifacts in {artifact_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
