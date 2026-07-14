#!/usr/bin/env python3
import os
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "live_voice_acceptance.sh"


def test_script_exists_and_is_executable():
    assert SCRIPT.is_file(), "live voice acceptance script should exist"
    assert os.access(SCRIPT, os.X_OK), "live voice acceptance script should be executable"


def test_script_syntax_is_valid():
    subprocess.run(["bash", "-n", str(SCRIPT)], check=True)


def test_script_records_live_voice_acceptance_artifacts():
    text = SCRIPT.read_text()
    required_tokens = [
        "training/test_training_layout.py",
        "training/rubert/evaluate_export.py",
        "rubert-host-eval-live-voice.jsonl",
        "rubert-host-eval-live-voice-metrics.json",
        "models/generated/rubert",
        "/data/local/tmp/offline-assistant-rubert",
        "rubert-tiny2-intent-slots.onnx",
        "vocab.txt",
        "intent_labels.txt",
        "slot_labels.txt",
        "app:installDebug",
        "reset_app_data",
        "pm clear",
        "android.permission.RECORD_AUDIO",
        "android.permission.POST_NOTIFICATIONS",
        "screenrecord",
        "wait_for_timer_result",
        "UI_POLL_SECONDS",
        "pkill -INT screenrecord",
        "stops automatically after the TimerCard appears",
        "timed out after",
        "offline-assistant-live-voice.mp4",
        "offline-assistant-live-voice.png",
        "offline-assistant-live-voice-uiautomator.xml",
        "offline-assistant-live-voice-debug-uiautomator.xml",
        "offline-assistant-live-voice-logcat.txt",
        "Поставь таймер на 5 минут",
        "Mic",
        "TimerCard",
        "assert_ui_contains",
        "assert_ui_contains_any",
        "tap_ui_text",
        "swipe_debug_history",
        "adb shell wm size",
        "adb shell input swipe",
        "bounds=",
        "adb shell input tap",
        "Поставил таймер",
        "Таймер создан в системном приложении.",
        "Таймер",
        "Switching to the История tab",
        "latency asr",
        "intent: set_timer",
        "source: RUBERT_TINY2",
        "fallback: false",
        "Live voice acceptance failed",
        "verify_device_smoke_artifacts.py",
        "--live-voice",
        "uiautomator dump",
        "adb logcat",
    ]
    missing = [token for token in required_tokens if token not in text]

    assert not missing, "live voice acceptance script is missing: " + ", ".join(missing)


if __name__ == "__main__":
    test_script_exists_and_is_executable()
    test_script_syntax_is_valid()
    test_script_records_live_voice_acceptance_artifacts()
