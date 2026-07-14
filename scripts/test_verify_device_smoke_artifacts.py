#!/usr/bin/env python3
import json
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "verify_device_smoke_artifacts.py"


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


def make_artifacts(directory: Path) -> None:
    write_jsonl(
        directory / "rubert-host-eval.jsonl",
        [{
            "text": "Поставь таймер на 5 минут",
            "expected_intent": "set_timer",
            "actual_intent": "set_timer",
            "exact": True,
        }],
    )
    write_metrics(directory / "rubert-host-eval-metrics.json")
    write_jsonl(
        directory / "qwen-answer-eval.jsonl",
        [{"question": "q", "latency_ms": 1234, "answer_chars": 42, "streamed_chars": 42}],
    )
    write_jsonl(
        directory / "rubert-slot-eval.jsonl",
        [{"text": "Поставь таймер на 5 минут", "slot_key": "duration_seconds", "exact": True}],
    )
    write_jsonl(
        directory / "whisper-asr-eval.jsonl",
        [{"audio": "timer-five-minutes.wav", "transcript": "поставь таймер на 5 минут", "widget": "timer_card"}],
    )
    (directory / "offline-assistant-demo-flow.mp4").write_bytes(b"\x00\x00\x00\x18ftypmp42demo")
    (directory / "offline-assistant-device-smoke.png").write_bytes(b"\x89PNG\r\n\x1a\n")
    (directory / "logcat-tail.txt").write_text("Assistant smoke log\n")
    (directory / "offline-connectivity.txt").write_text(
        "oem_deny_chain=chain:enabled\n"
        "package_networking_com.offlineassistant.poc.debug=com.offlineassistant.poc.debug:deny\n"
        "active_network_count=1\n"
        "NetworkAgentInfo VPN snapshot\n",
    )


def make_live_voice_artifacts(
    directory: Path,
    ui_text: str = "Поставил таймер. Таймер",
    debug_text: str = "transcript: Поставь таймер на 5 минут intent: set_timer source: RUBERT_TINY2 fallback: false latency asr",
) -> None:
    write_jsonl(
        directory / "rubert-host-eval-live-voice.jsonl",
        [{"text": "Поставь таймер на 5 минут", "intent": "set_timer", "exact": True}],
    )
    write_metrics(directory / "rubert-host-eval-live-voice-metrics.json")
    (directory / "offline-assistant-live-voice.mp4").write_bytes(b"\x00\x00\x00\x18ftypmp42voice")
    (directory / "offline-assistant-live-voice.png").write_bytes(b"\x89PNG\r\n\x1a\n")
    (directory / "offline-assistant-live-voice-uiautomator.xml").write_text(
        f'<hierarchy><node text="{ui_text}" /></hierarchy>',
    )
    (directory / "offline-assistant-live-voice-debug-uiautomator.xml").write_text(
        f'<hierarchy><node text="{debug_text}" /></hierarchy>',
    )
    (directory / "offline-assistant-live-voice-logcat.txt").write_text("Whisper ASR latency\n")


def test_verifier_accepts_required_artifacts():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_artifacts(artifact_dir)

        subprocess.run(["python3", str(SCRIPT), str(artifact_dir)], check=True)


def test_verifier_rejects_empty_qwen_eval():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_artifacts(artifact_dir)
        (artifact_dir / "qwen-answer-eval.jsonl").write_text("")

        result = subprocess.run(
            ["python3", str(SCRIPT), str(artifact_dir)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        assert result.returncode != 0
        assert "qwen-answer-eval.jsonl" in result.stderr


def test_verifier_rejects_missing_rubert_host_eval():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_artifacts(artifact_dir)
        (artifact_dir / "rubert-host-eval.jsonl").unlink()

        result = subprocess.run(
            ["python3", str(SCRIPT), str(artifact_dir)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        assert result.returncode != 0
        assert "rubert-host-eval.jsonl" in result.stderr


def test_verifier_rejects_incomplete_rubert_metrics():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_artifacts(artifact_dir)
        (artifact_dir / "rubert-host-eval-metrics.json").write_text('{"intent_accuracy": 1.0}')

        result = subprocess.run(
            ["python3", str(SCRIPT), str(artifact_dir)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        assert result.returncode != 0
        assert "required metrics" in result.stderr


def test_verifier_rejects_missing_offline_connectivity_artifact():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_artifacts(artifact_dir)
        (artifact_dir / "offline-connectivity.txt").unlink()

        result = subprocess.run(
            ["python3", str(SCRIPT), str(artifact_dir)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        assert result.returncode != 0
        assert "offline-connectivity.txt" in result.stderr


def test_verifier_rejects_offline_connectivity_without_package_network_deny():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_artifacts(artifact_dir)
        (artifact_dir / "offline-connectivity.txt").write_text(
            "oem_deny_chain=chain:enabled\n"
            "package_networking_com.offlineassistant.poc.debug=com.offlineassistant.poc.debug:allow\n"
        )

        result = subprocess.run(
            ["python3", str(SCRIPT), str(artifact_dir)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        assert result.returncode != 0
        assert "com.offlineassistant.poc.debug:deny" in result.stderr


def test_verifier_rejects_offline_connectivity_without_oem_deny_chain():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_artifacts(artifact_dir)
        (artifact_dir / "offline-connectivity.txt").write_text(
            "oem_deny_chain=chain:disabled\n"
            "package_networking_com.offlineassistant.poc.debug=com.offlineassistant.poc.debug:deny\n"
        )

        result = subprocess.run(
            ["python3", str(SCRIPT), str(artifact_dir)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        assert result.returncode != 0
        assert "oem_deny_chain=chain:enabled" in result.stderr


def test_verifier_accepts_live_voice_artifacts():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_live_voice_artifacts(artifact_dir)

        subprocess.run(["python3", str(SCRIPT), "--live-voice", str(artifact_dir)], check=True)


def test_verifier_accepts_passive_system_timer_result():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_live_voice_artifacts(
            artifact_dir,
            ui_text="Таймер создан в системном приложении. Таймер",
        )

        subprocess.run(["python3", str(SCRIPT), "--live-voice", str(artifact_dir)], check=True)


def test_verifier_rejects_live_voice_without_timer_result():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_live_voice_artifacts(artifact_dir, ui_text="Сообщение")

        result = subprocess.run(
            ["python3", str(SCRIPT), "--live-voice", str(artifact_dir)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        assert result.returncode != 0
        assert "offline-assistant-live-voice-uiautomator.xml" in result.stderr


def test_verifier_rejects_live_voice_without_rubert_host_eval():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_live_voice_artifacts(artifact_dir)
        (artifact_dir / "rubert-host-eval-live-voice.jsonl").unlink()

        result = subprocess.run(
            ["python3", str(SCRIPT), "--live-voice", str(artifact_dir)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        assert result.returncode != 0
        assert "rubert-host-eval-live-voice.jsonl" in result.stderr


def test_verifier_rejects_live_voice_without_debug_latency():
    with tempfile.TemporaryDirectory() as tmp:
        artifact_dir = Path(tmp)
        make_live_voice_artifacts(artifact_dir, debug_text="transcript: Поставь таймер на 5 минут")

        result = subprocess.run(
            ["python3", str(SCRIPT), "--live-voice", str(artifact_dir)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        assert result.returncode != 0
        assert "offline-assistant-live-voice-debug-uiautomator.xml" in result.stderr


if __name__ == "__main__":
    test_verifier_accepts_required_artifacts()
    test_verifier_rejects_empty_qwen_eval()
    test_verifier_rejects_missing_rubert_host_eval()
    test_verifier_rejects_incomplete_rubert_metrics()
    test_verifier_rejects_missing_offline_connectivity_artifact()
    test_verifier_rejects_offline_connectivity_without_package_network_deny()
    test_verifier_rejects_offline_connectivity_without_oem_deny_chain()
    test_verifier_accepts_live_voice_artifacts()
    test_verifier_accepts_passive_system_timer_result()
    test_verifier_rejects_live_voice_without_timer_result()
    test_verifier_rejects_live_voice_without_rubert_host_eval()
    test_verifier_rejects_live_voice_without_debug_latency()
