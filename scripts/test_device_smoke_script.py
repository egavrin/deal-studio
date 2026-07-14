#!/usr/bin/env python3
import subprocess
import os
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "device_smoke_test.sh"


def test_script_syntax_is_valid():
    subprocess.run(["bash", "-n", str(SCRIPT)], check=True)


def test_script_is_executable():
    assert os.access(SCRIPT, os.X_OK), "device smoke script should be executable"


def test_script_runs_required_connected_acceptance_gates():
    text = SCRIPT.read_text()

    required_tokens = [
        "training/test_training_layout.py",
        "training/rubert/evaluate_export.py",
        "rubert-host-eval.jsonl",
        "rubert-host-eval-metrics.json",
        "models/external/qwen2.5-0.5b-instruct-gguf/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        "models/generated/rubert",
        "/data/local/tmp/offline-assistant-qwen.gguf",
        "/data/local/tmp/offline-assistant-rubert",
        "/data/local/tmp/offline-assistant-timer-command.wav",
        "com.offlineassistant.app.eval.RubertSlotEvaluationTest",
        "com.offlineassistant.app.eval.RubertCommandEvaluationTest",
        "com.offlineassistant.app.eval.QwenAnswerEvaluationTest",
        "com.offlineassistant.app.asr.WhisperNativeSmokeTest",
        "com.offlineassistant.app.QwenUiSmokeTest",
        "com.offlineassistant.app.llm.LlamaNativeSmokeTest",
        "com.offlineassistant.app.platform.AndroidPlatformAdaptersTest",
        "com.offlineassistant.app.MainChatScreenTest",
        "com.offlineassistant.app.MicrophonePermissionAcceptanceTest",
        "com.offlineassistant.app.NotificationPermissionAcceptanceTest",
        "com.offlineassistant.app.DemoVideoFlowTest",
        "run_offline_model_acceptance",
        "prepare_device_for_ui_acceptance",
        "require_no_blocking_phone_ui",
        "KEYCODE_WAKEUP",
        "wm dismiss-keyguard",
        "InCallActivity",
        "NotificationShade",
        "isKeyguardShowing=true",
        "svc wifi disable",
        "svc data disable",
        "svc wifi enable",
        "svc data enable",
        "offline-connectivity.txt",
        "active_network_count",
        "cmd connectivity set-chain3-enabled true",
        "cmd connectivity set-package-networking-enabled false",
        "cmd connectivity set-package-networking-enabled true",
        "oem_deny_chain",
        "package_networking_",
        "grep -c",
        "run_microphone_permission_acceptance",
        "run_notification_permission_acceptance",
        "android.permission.RECORD_AUDIO",
        "pm revoke --user 0",
        "clear-permission-flags",
        "RUN_FULL_CONNECTED_SUITE",
        "run_full_connected_suite",
        "verify_device_smoke_artifacts.py",
        "screenrecord",
        "offline-assistant-demo-flow.mp4",
        "require_non_empty_file",
        "run_host_checks",
        "qwen-answer-eval.jsonl",
        "rubert-slot-eval.jsonl",
        "whisper-asr-eval.jsonl",
        "QwenAnswerEval",
        "RubertSlotEval",
        "WhisperAsrEval",
        "extract_tagged_jsonl",
        "run_eval_artifact_acceptance",
        "adb logcat -c",
        "android.permission.RECORD_AUDIO",
        "android.permission.POST_NOTIFICATIONS",
        "screencap",
    ]
    missing = [token for token in required_tokens if token not in text]

    assert not missing, "device smoke script is missing: " + ", ".join(missing)


def test_script_collects_eval_artifacts_before_followup_instrumentation():
    text = SCRIPT.read_text()
    execution_block = text[text.index('log "Running host checks"') :]
    offline = execution_block.index("run_offline_model_acceptance")
    acceptance = execution_block.index("run_connected_acceptance", offline)
    artifacts = execution_block.index("pull_debug_artifacts", acceptance)
    microphone = execution_block.index("run_microphone_permission_acceptance", acceptance)
    notification = execution_block.index("run_notification_permission_acceptance", acceptance)
    demo = execution_block.index("run_recorded_demo_flow", acceptance)
    full_suite = execution_block.index("run_full_connected_suite", demo)
    final_launch = execution_block.index("launch_for_manual_testing", full_suite)

    assert offline < acceptance < artifacts < microphone < notification < demo < full_suite < final_launch


def test_each_eval_producer_is_collected_before_the_next_instrumentation_run():
    text = SCRIPT.read_text()
    connected_block = text[text.index("run_connected_acceptance()") : text.index("restore_network_state()")]

    rubert = connected_block.index('"com.offlineassistant.app.eval.RubertSlotEvaluationTest"')
    qwen = connected_block.index('"com.offlineassistant.app.eval.QwenAnswerEvaluationTest"', rubert)
    whisper = connected_block.index('"com.offlineassistant.app.asr.WhisperNativeSmokeTest"', qwen)
    remaining = connected_block.index('log "Running remaining connected acceptance gates"', whisper)
    collector = text[text.index("run_eval_artifact_acceptance()") : text.index("restore_network_state()")]

    assert rubert < qwen < whisper < remaining
    assert 'extract_tagged_jsonl "$log_tag" "$output"' in collector
    assert 'require_non_empty_file "$output"' in collector


def test_eval_artifacts_do_not_depend_on_target_apk_private_files():
    text = SCRIPT.read_text()
    artifact_block = text[text.index("extract_tagged_jsonl()") : text.index("launch_for_manual_testing()")]

    assert "adb logcat -d -v raw" in artifact_block
    assert "run-as" not in artifact_block


def test_device_ui_preflight_runs_before_launch_and_ui_gates():
    text = SCRIPT.read_text()
    execution_block = text[text.index("run_host_checks") :]
    first_preflight = execution_block.index("prepare_device_for_ui_acceptance")
    first_launch = execution_block.index("launch_for_manual_testing", first_preflight)
    offline = execution_block.index("run_offline_model_acceptance", first_launch)
    second_preflight = execution_block.index("prepare_device_for_ui_acceptance", offline)
    acceptance = execution_block.index("run_connected_acceptance", second_preflight)

    assert first_preflight < first_launch < offline < second_preflight < acceptance


def test_offline_gate_restores_network_without_return_trap():
    text = SCRIPT.read_text()
    offline_block = text[
        text.index("run_offline_model_acceptance()") : text.index("run_microphone_permission_acceptance()")
    ]

    required_tokens = [
        "local offline_status",
        "set +e",
        "set -e",
        'restore_network_state "$wifi_was_on" "$data_was_on"',
        'restore_package_networking "$chain_was_enabled"',
        'return "$offline_status"',
    ]
    missing = [token for token in required_tokens if token not in offline_block]

    assert not missing, "offline gate restore flow is missing: " + ", ".join(missing)
    assert "trap 'restore_network_state" not in offline_block


if __name__ == "__main__":
    test_script_syntax_is_valid()
    test_script_is_executable()
    test_script_runs_required_connected_acceptance_gates()
    test_script_collects_eval_artifacts_before_followup_instrumentation()
    test_each_eval_producer_is_collected_before_the_next_instrumentation_run()
    test_eval_artifacts_do_not_depend_on_target_apk_private_files()
    test_device_ui_preflight_runs_before_launch_and_ui_gates()
    test_offline_gate_restores_network_without_return_trap()
