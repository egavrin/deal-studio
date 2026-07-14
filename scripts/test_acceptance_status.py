#!/usr/bin/env python3
import json
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "acceptance_status.py"


def write_jsonl(path: Path, rows: list[dict]) -> None:
    path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows))


def write_metrics(path: Path) -> None:
    path.write_text(json.dumps({
        "intent_accuracy": 1.0,
        "intent_macro_f1": 1.0,
        "slot_f1": 1.0,
        "confusion_matrix": {"set_timer": {"set_timer": 1}},
        "validation_pass_rate": 1.0,
    }))


def make_host_only_artifacts(directory: Path) -> None:
    write_jsonl(
        directory / "rubert-host-eval-full-acceptance.jsonl",
        [{"text": "Поставь таймер на 5 минут", "intent": "set_timer", "exact": True}],
    )
    write_metrics(directory / "rubert-host-eval-full-acceptance-metrics.json")


def make_complete_artifacts(directory: Path) -> None:
    make_host_only_artifacts(directory)
    write_jsonl(directory / "rubert-host-eval.jsonl", [{"text": "x", "intent": "set_timer", "exact": True}])
    write_metrics(directory / "rubert-host-eval-metrics.json")
    write_jsonl(directory / "qwen-answer-eval.jsonl", [{"question": "q", "latency_ms": 10, "answer_chars": 20}])
    write_jsonl(directory / "rubert-slot-eval.jsonl", [{"text": "x", "slot_key": "duration_seconds", "exact": True}])
    write_jsonl(directory / "whisper-asr-eval.jsonl", [{"audio": "timer.wav", "transcript": "таймер", "widget": "timer_card"}])
    write_jsonl(directory / "rubert-host-eval-live-voice.jsonl", [{"text": "x", "intent": "set_timer", "exact": True}])
    write_metrics(directory / "rubert-host-eval-live-voice-metrics.json")
    (directory / "offline-connectivity.txt").write_text(
        "oem_deny_chain=chain:enabled\n"
        "package_networking_com.offlineassistant.poc.debug=com.offlineassistant.poc.debug:deny\n",
    )
    (directory / "offline-assistant-demo-flow.mp4").write_bytes(b"\x00\x00\x00\x18ftypmp42")
    (directory / "offline-assistant-device-smoke.png").write_bytes(b"\x89PNG\r\n\x1a\n")
    (directory / "logcat-tail.txt").write_text("log\n")
    (directory / "offline-assistant-live-voice.mp4").write_bytes(b"\x00\x00\x00\x18ftypmp42")
    (directory / "offline-assistant-live-voice.png").write_bytes(b"\x89PNG\r\n\x1a\n")
    (directory / "offline-assistant-live-voice-uiautomator.xml").write_text("Поставил таймер Таймер")
    (directory / "offline-assistant-live-voice-debug-uiautomator.xml").write_text(
        "transcript: x intent: set_timer source: RUBERT_TINY2 fallback: false latency asr",
    )
    (directory / "offline-assistant-live-voice-logcat.txt").write_text("log\n")


def run_status(directory: Path) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["python3", str(SCRIPT), str(directory)],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def test_status_reports_incomplete_when_only_host_preflight_exists():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_host_only_artifacts(artifact_dir)

        result = run_status(artifact_dir)

        assert result.returncode == 1
        payload = json.loads(result.stdout)
        assert payload["complete"] is False
        assert payload["host_preflight"]["status"] == "present"
        assert payload["device_smoke"]["status"] == "missing"
        assert payload["live_voice"]["status"] == "missing"
        assert "scripts/run_full_acceptance.sh --wait-for-device" in payload["next_step"]


def test_status_reports_complete_when_all_acceptance_artifacts_exist():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_complete_artifacts(artifact_dir)

        result = run_status(artifact_dir)

        assert result.returncode == 0
        payload = json.loads(result.stdout)
        assert payload["complete"] is True
        assert payload["device_smoke"]["status"] == "present"
        assert payload["live_voice"]["status"] == "present"
        assert payload["missing"] == []


if __name__ == "__main__":
    test_status_reports_incomplete_when_only_host_preflight_exists()
    test_status_reports_complete_when_all_acceptance_artifacts_exist()
