#!/usr/bin/env python3
import os
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "run_full_acceptance.sh"


def test_script_exists_and_is_executable():
    assert SCRIPT.is_file(), "full acceptance script should exist"
    assert os.access(SCRIPT, os.X_OK), "full acceptance script should be executable"


def test_script_syntax_is_valid():
    subprocess.run(["bash", "-n", str(SCRIPT)], check=True)


def test_script_runs_host_preflight_and_phone_acceptance_in_order():
    text = SCRIPT.read_text()

    required_tokens = [
        "--host-only",
        "--wait-for-device",
        "--wait-timeout-seconds",
        "run_host_preflight",
        "wait_for_device",
        "training/test_training_layout.py",
        "training/rubert/evaluate_export.py",
        "rubert-host-eval-full-acceptance.jsonl",
        "rubert-host-eval-full-acceptance-metrics.json",
        "training/test_evaluate_export_metrics.py",
        "scripts/test_qwen_generation_policy.py",
        "scripts/test_device_smoke_script.py",
        "scripts/test_live_voice_acceptance_script.py",
        "scripts/test_verify_device_smoke_artifacts.py",
        "scripts/test_design_board.py",
        "scripts/test_acceptance_status.py",
        "scripts/test_final_dod_status.py",
        "./gradlew test :app:compileDebugAndroidTestKotlin assembleDebug :app:compileReleaseKotlin",
        "require_device",
        "scripts/device_smoke_test.sh",
        "scripts/live_voice_acceptance.sh",
        "scripts/verify_device_smoke_artifacts.py",
        "scripts/acceptance_status.py",
        "scripts/final_dod_status.py",
        "--live-voice",
        "offline-assistant-demo-flow.mp4",
        "offline-assistant-live-voice.mp4",
        "Full acceptance still requires a connected Android device",
        "Timed out waiting for a connected Android device",
    ]
    missing = [token for token in required_tokens if token not in text]

    assert not missing, "full acceptance script is missing: " + ", ".join(missing)

    execution_block = text[text.rindex("\nrun_host_preflight") :]
    host = execution_block.index("run_host_preflight")
    wait = execution_block.index("wait_for_device", host)
    require_device = execution_block.index("require_device", wait)

    phone_block = text[text.index("run_phone_acceptance()") :]
    device = phone_block.index("scripts/device_smoke_test.sh")
    live = phone_block.index("scripts/live_voice_acceptance.sh", device)
    verify_live = phone_block.index("--live-voice", live)

    assert host < wait < require_device
    assert device < live < verify_live


if __name__ == "__main__":
    test_script_exists_and_is_executable()
    test_script_syntax_is_valid()
    test_script_runs_host_preflight_and_phone_acceptance_in_order()
