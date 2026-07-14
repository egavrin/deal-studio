#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PACKAGE="${PACKAGE:-com.offlineassistant.poc.debug}"
OUT_DIR="${OUT_DIR:-build/device-smoke}"
QWEN_GGUF="${QWEN_GGUF:-models/external/qwen2.5-0.5b-instruct-gguf/qwen2.5-0.5b-instruct-q4_k_m.gguf}"
RUBERT_DIR="${RUBERT_DIR:-models/generated/rubert}"
TIMER_WAV="${TIMER_WAV:-docs/testing/audio/timer-command.wav}"
RUN_FULL_CONNECTED_SUITE="${RUN_FULL_CONNECTED_SUITE:-1}"

SCREENSHOT_DEVICE="/sdcard/offline-assistant-device-smoke.png"
SCREENSHOT_HOST="$OUT_DIR/offline-assistant-device-smoke.png"
DEMO_VIDEO_DEVICE="/sdcard/offline-assistant-demo-flow.mp4"
DEMO_VIDEO_HOST="$OUT_DIR/offline-assistant-demo-flow.mp4"
RUBERT_HOST_EVAL_HOST="$OUT_DIR/rubert-host-eval.jsonl"
RUBERT_HOST_METRICS_HOST="$OUT_DIR/rubert-host-eval-metrics.json"
OFFLINE_CONNECTIVITY_HOST="$OUT_DIR/offline-connectivity.txt"
OFFLINE_CONNECTIVITY_RAW_HOST="$OUT_DIR/offline-connectivity.raw.txt"

CONNECTED_TEST_CLASSES=(
  "com.offlineassistant.app.eval.RubertCommandEvaluationTest"
  "com.offlineassistant.app.QwenUiSmokeTest"
  "com.offlineassistant.app.llm.LlamaNativeSmokeTest"
  "com.offlineassistant.app.platform.AndroidPlatformAdaptersTest"
  "com.offlineassistant.app.MainChatScreenTest"
)

log() {
  printf '\n==> %s\n' "$*"
}

require_file() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    printf 'Required file is missing: %s\n' "$path" >&2
    exit 1
  fi
}

require_non_empty_file() {
  local path="$1"
  require_file "$path"
  if [[ ! -s "$path" ]]; then
    printf 'Required file is empty: %s\n' "$path" >&2
    exit 1
  fi
}

require_device() {
  if ! command -v adb >/dev/null 2>&1; then
    printf 'adb is not available on PATH\n' >&2
    exit 1
  fi
  local count
  count="$(adb devices | awk 'NR > 1 && $2 == "device" { count += 1 } END { print count + 0 }')"
  if [[ "$count" -lt 1 ]]; then
    printf 'No adb device is connected. Connect the phone and rerun this script.\n' >&2
    adb devices >&2 || true
    exit 1
  fi
}

require_no_blocking_phone_ui() {
  local window_dump
  local activity_dump
  window_dump="$(adb shell dumpsys window 2>/dev/null | tr -d '\r' || true)"
  activity_dump="$(adb shell dumpsys activity activities 2>/dev/null | tr -d '\r' || true)"

  if grep -q 'isKeyguardShowing=true' <<<"$window_dump"; then
    printf 'Device UI is locked (isKeyguardShowing=true). Unlock the phone before running acceptance.\n' >&2
    exit 1
  fi
  if grep -q 'mDreamingLockscreen=true' <<<"$window_dump"; then
    printf 'Device UI is on the lockscreen (mDreamingLockscreen=true). Unlock the phone before running acceptance.\n' >&2
    exit 1
  fi
  if grep -q 'InCallActivity' <<<"$activity_dump"; then
    printf 'Device UI is blocked by InCallActivity. Finish or dismiss the phone call UI before running acceptance.\n' >&2
    exit 1
  fi
  if grep -q 'mCurrentFocus=Window{.*NotificationShade' <<<"$window_dump"; then
    printf 'Device UI is blocked by NotificationShade. Dismiss it before running acceptance.\n' >&2
    exit 1
  fi
}

prepare_device_for_ui_acceptance() {
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
  sleep 1
  require_no_blocking_phone_ui
}

stage_models() {
  require_file "$QWEN_GGUF"
  require_file "$TIMER_WAV"
  for name in rubert-tiny2-intent-slots.onnx vocab.txt intent_labels.txt slot_labels.txt; do
    require_file "$RUBERT_DIR/$name"
  done

  log "Staging Qwen GGUF and RuBERT bundle under /data/local/tmp"
  adb push "$QWEN_GGUF" /data/local/tmp/offline-assistant-qwen.gguf >/dev/null
  adb shell rm -rf /data/local/tmp/offline-assistant-rubert
  adb shell mkdir -p /data/local/tmp/offline-assistant-rubert
  for name in rubert-tiny2-intent-slots.onnx vocab.txt intent_labels.txt slot_labels.txt; do
    adb push "$RUBERT_DIR/$name" "/data/local/tmp/offline-assistant-rubert/$name" >/dev/null
  done
  adb push "$TIMER_WAV" /data/local/tmp/offline-assistant-timer-command.wav >/dev/null
}

grant_runtime_permissions() {
  adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
  adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
}

run_connected_acceptance() {
  local classes
  classes="$(IFS=,; echo "${CONNECTED_TEST_CLASSES[*]}")"

  run_eval_artifact_acceptance \
    "com.offlineassistant.app.eval.RubertSlotEvaluationTest" \
    "RubertSlotEval" \
    "$OUT_DIR/rubert-slot-eval.jsonl"
  run_eval_artifact_acceptance \
    "com.offlineassistant.app.eval.QwenAnswerEvaluationTest" \
    "QwenAnswerEval" \
    "$OUT_DIR/qwen-answer-eval.jsonl"
  run_eval_artifact_acceptance \
    "com.offlineassistant.app.asr.WhisperNativeSmokeTest" \
    "WhisperAsrEval" \
    "$OUT_DIR/whisper-asr-eval.jsonl"

  log "Running remaining connected acceptance gates"
  ./gradlew :app:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=$classes"
}

run_eval_artifact_acceptance() {
  local test_class="$1"
  local log_tag="$2"
  local output="$3"

  log "Running and collecting $test_class"
  adb logcat -c
  ./gradlew :app:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=$test_class"
  extract_tagged_jsonl "$log_tag" "$output"
  require_non_empty_file "$output"
}

restore_network_state() {
  local wifi_was_on="${1:-1}"
  local data_was_on="${2:-1}"
  if [[ "$wifi_was_on" == "1" ]]; then
    adb shell svc wifi enable >/dev/null 2>&1 || true
  else
    adb shell svc wifi disable >/dev/null 2>&1 || true
  fi
  if [[ "$data_was_on" == "1" ]]; then
    adb shell svc data enable >/dev/null 2>&1 || true
  else
    adb shell svc data disable >/dev/null 2>&1 || true
  fi
}

get_chain3_state() {
  adb shell cmd connectivity get-chain3-enabled 2>/dev/null | tr -d '\r' || printf 'chain:unknown'
}

get_package_networking_state() {
  local package_name="$1"
  adb shell cmd connectivity get-package-networking-enabled "$package_name" 2>/dev/null | tr -d '\r' || printf '%s:unknown' "$package_name"
}

deny_package_networking() {
  adb shell cmd connectivity set-chain3-enabled true >/dev/null
  adb shell cmd connectivity set-package-networking-enabled false "$PACKAGE" >/dev/null
}

restore_package_networking() {
  local chain_was_enabled="${1:-chain:disabled}"
  adb shell cmd connectivity set-package-networking-enabled true "$PACKAGE" >/dev/null 2>&1 || true
  if [[ "$chain_was_enabled" == "chain:enabled" ]]; then
    adb shell cmd connectivity set-chain3-enabled true >/dev/null 2>&1 || true
  else
    adb shell cmd connectivity set-chain3-enabled false >/dev/null 2>&1 || true
  fi
}

run_offline_model_acceptance() {
  mkdir -p "$OUT_DIR"
  local wifi_was_on
  local data_was_on
  local chain_was_enabled
  wifi_was_on="$(adb shell settings get global wifi_on 2>/dev/null | tr -d '\r' || printf '1')"
  data_was_on="$(adb shell settings get global mobile_data 2>/dev/null | tr -d '\r' || printf '1')"
  chain_was_enabled="$(get_chain3_state)"

  log "Running offline local-model acceptance gate"
  local offline_status
  offline_status=0
  set +e
  deny_package_networking
  adb shell svc wifi disable >/dev/null 2>&1 || true
  adb shell svc data disable >/dev/null 2>&1 || true
  sleep 2
  adb shell dumpsys connectivity > "$OFFLINE_CONNECTIVITY_RAW_HOST" || true
  local active_network_count
  local oem_deny_chain
  local package_networking
  active_network_count="$(grep -c 'NetworkAgentInfo{' "$OFFLINE_CONNECTIVITY_RAW_HOST" || true)"
  oem_deny_chain="$(get_chain3_state)"
  package_networking="$(get_package_networking_state "$PACKAGE")"
  {
    printf 'oem_deny_chain=%s\n' "$oem_deny_chain"
    printf 'package_networking_%s=%s\n' "$PACKAGE" "$package_networking"
    printf 'wifi_on=%s\n' "$(adb shell settings get global wifi_on 2>/dev/null | tr -d '\r' || printf 'unknown')"
    printf 'mobile_data=%s\n' "$(adb shell settings get global mobile_data 2>/dev/null | tr -d '\r' || printf 'unknown')"
    printf 'active_network_count=%s\n' "$active_network_count"
    cat "$OFFLINE_CONNECTIVITY_RAW_HOST"
  } > "$OFFLINE_CONNECTIVITY_HOST"
  rm -f "$OFFLINE_CONNECTIVITY_RAW_HOST"
  if [[ "$oem_deny_chain" != "chain:enabled" || "$package_networking" != "$PACKAGE:deny" ]]; then
    printf 'Offline local-model gate expected oem_deny_chain=chain:enabled and package networking %s:deny. See %s\n' "$PACKAGE" "$OFFLINE_CONNECTIVITY_HOST" >&2
    offline_status=1
  else
    ./gradlew :app:connectedDebugAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=com.offlineassistant.app.asr.WhisperNativeSmokeTest,com.offlineassistant.app.eval.RubertCommandEvaluationTest,com.offlineassistant.app.QwenUiSmokeTest
    offline_status="$?"
  fi
  set -e
  restore_package_networking "$chain_was_enabled"
  restore_network_state "$wifi_was_on" "$data_was_on"
  require_non_empty_file "$OFFLINE_CONNECTIVITY_HOST"
  return "$offline_status"
}

run_microphone_permission_acceptance() {
  log "Running microphone permission acceptance gate"
  adb shell pm revoke --user 0 "$PACKAGE" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
  adb shell pm clear-permission-flags "$PACKAGE" android.permission.RECORD_AUDIO user-set user-fixed >/dev/null 2>&1 || true
  ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.offlineassistant.app.MicrophonePermissionAcceptanceTest
  grant_runtime_permissions
}

run_notification_permission_acceptance() {
  log "Running notification permission acceptance gate"
  adb shell pm revoke --user 0 "$PACKAGE" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  adb shell pm clear-permission-flags "$PACKAGE" android.permission.POST_NOTIFICATIONS user-set user-fixed >/dev/null 2>&1 || true
  ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.offlineassistant.app.NotificationPermissionAcceptanceTest
  grant_runtime_permissions
}

run_full_connected_suite() {
  if [[ "$RUN_FULL_CONNECTED_SUITE" != "1" ]]; then
    log "Skipping full connected suite because RUN_FULL_CONNECTED_SUITE=$RUN_FULL_CONNECTED_SUITE"
    return
  fi
  log "Running full connectedDebugAndroidTest suite"
  ./gradlew :app:connectedDebugAndroidTest
}

run_host_checks() {
  mkdir -p "$OUT_DIR"
  log "Running host checks"
  python3 training/test_training_layout.py
  python3 training/rubert/evaluate_export.py --model-dir "$RUBERT_DIR" --output "$RUBERT_HOST_EVAL_HOST"
  require_non_empty_file "$RUBERT_HOST_EVAL_HOST"
  require_non_empty_file "$RUBERT_HOST_METRICS_HOST"
  ./gradlew test :app:compileDebugAndroidTestKotlin assembleDebug
}

run_recorded_demo_flow() {
  mkdir -p "$OUT_DIR"
  adb shell rm -f "$DEMO_VIDEO_DEVICE"
  log "Recording demo flow to $DEMO_VIDEO_HOST"
  adb shell screenrecord --time-limit 180 "$DEMO_VIDEO_DEVICE" >/dev/null 2>&1 &
  local recorder_pid="$!"
  set +e
  ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.offlineassistant.app.DemoVideoFlowTest
  local test_status="$?"
  set -e
  kill -INT "$recorder_pid" >/dev/null 2>&1 || true
  wait "$recorder_pid" >/dev/null 2>&1 || true
  adb pull "$DEMO_VIDEO_DEVICE" "$DEMO_VIDEO_HOST" >/dev/null || true
  if [[ "$test_status" -ne 0 ]]; then
    exit "$test_status"
  fi
  require_file "$DEMO_VIDEO_HOST"
}

extract_tagged_jsonl() {
  local tag="$1"
  local output="$2"
  adb logcat -d -v raw -s "$tag:I" '*:S' \
    | awk '/^\{.*\}$/ { print }' > "$output"
}

pull_debug_artifacts() {
  mkdir -p "$OUT_DIR"
  require_non_empty_file "$OUT_DIR/qwen-answer-eval.jsonl"
  require_non_empty_file "$OUT_DIR/rubert-slot-eval.jsonl"
  require_non_empty_file "$OUT_DIR/whisper-asr-eval.jsonl"
  adb logcat -d -t 3000 > "$OUT_DIR/logcat-tail.txt" || true
}

launch_for_manual_testing() {
  log "Installing and launching debug APK for manual testing"
  ./gradlew :app:installDebug
  grant_runtime_permissions
  adb shell monkey -p "$PACKAGE" 1 >/dev/null
  sleep 2
  adb shell screencap -p "$SCREENSHOT_DEVICE"
  adb pull "$SCREENSHOT_DEVICE" "$SCREENSHOT_HOST" >/dev/null
}

run_host_checks

require_device
prepare_device_for_ui_acceptance
stage_models
launch_for_manual_testing
run_offline_model_acceptance
prepare_device_for_ui_acceptance
run_connected_acceptance
pull_debug_artifacts
run_microphone_permission_acceptance
run_notification_permission_acceptance
run_recorded_demo_flow
run_full_connected_suite
launch_for_manual_testing
python3 scripts/verify_device_smoke_artifacts.py "$OUT_DIR"

log "Device smoke finished. Artifacts are in $OUT_DIR"
