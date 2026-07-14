#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PACKAGE="${PACKAGE:-com.offlineassistant.poc.debug}"
OUT_DIR="${OUT_DIR:-build/device-smoke}"
DURATION_SECONDS="${DURATION_SECONDS:-120}"
UI_POLL_SECONDS="${UI_POLL_SECONDS:-2}"
HISTORY_WAIT_SECONDS="${HISTORY_WAIT_SECONDS:-8}"
PROMPT_TEXT="${PROMPT_TEXT:-Поставь таймер на 5 минут}"
RUBERT_DIR="${RUBERT_DIR:-models/generated/rubert}"

VIDEO_DEVICE="/sdcard/offline-assistant-live-voice.mp4"
SCREENSHOT_DEVICE="/sdcard/offline-assistant-live-voice.png"
UI_DUMP_DEVICE="/sdcard/offline-assistant-live-voice-uiautomator.xml"
DEBUG_UI_DUMP_DEVICE="/sdcard/offline-assistant-live-voice-debug-uiautomator.xml"
NAV_UI_DUMP_DEVICE="/sdcard/offline-assistant-live-voice-nav-uiautomator.xml"
VIDEO_HOST="$OUT_DIR/offline-assistant-live-voice.mp4"
SCREENSHOT_HOST="$OUT_DIR/offline-assistant-live-voice.png"
UI_DUMP_HOST="$OUT_DIR/offline-assistant-live-voice-uiautomator.xml"
DEBUG_UI_DUMP_HOST="$OUT_DIR/offline-assistant-live-voice-debug-uiautomator.xml"
NAV_UI_DUMP_HOST="$OUT_DIR/offline-assistant-live-voice-nav-uiautomator.xml"
LOGCAT_HOST="$OUT_DIR/offline-assistant-live-voice-logcat.txt"
RUBERT_HOST_EVAL_HOST="$OUT_DIR/rubert-host-eval-live-voice.jsonl"
RUBERT_HOST_METRICS_HOST="$OUT_DIR/rubert-host-eval-live-voice-metrics.json"

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

assert_ui_contains() {
  local expected="$1"
  if ! grep -Fq "$expected" "$UI_DUMP_HOST"; then
    printf 'Live voice acceptance failed: UI dump does not contain expected text: %s\n' "$expected" >&2
    printf 'Artifacts were saved under %s\n' "$OUT_DIR" >&2
    exit 1
  fi
}

assert_ui_contains_any() {
  local first="$1"
  local second="$2"
  if ! grep -Fq "$first" "$UI_DUMP_HOST" && ! grep -Fq "$second" "$UI_DUMP_HOST"; then
    printf 'Live voice acceptance failed: UI dump contains neither expected text: %s | %s\n' \
      "$first" "$second" >&2
    printf 'Artifacts were saved under %s\n' "$OUT_DIR" >&2
    exit 1
  fi
}

assert_debug_contains() {
  local expected="$1"
  if ! grep -Fq "$expected" "$DEBUG_UI_DUMP_HOST"; then
    printf 'Live voice acceptance failed: debug UI dump does not contain expected text: %s\n' "$expected" >&2
    printf 'Artifacts were saved under %s\n' "$OUT_DIR" >&2
    exit 1
  fi
}

tap_ui_text() {
  local label="$1"
  adb shell uiautomator dump "$NAV_UI_DUMP_DEVICE" >/dev/null
  adb pull "$NAV_UI_DUMP_DEVICE" "$NAV_UI_DUMP_HOST" >/dev/null
  require_non_empty_file "$NAV_UI_DUMP_HOST"

  local point
  point="$(python3 - "$NAV_UI_DUMP_HOST" "$label" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, label = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    if node.attrib.get("text") == label or node.attrib.get("content-desc") == label:
        bounds = node.attrib.get("bounds", "")
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if match:
            left, top, right, bottom = map(int, match.groups())
            print(f"{(left + right) // 2} {(top + bottom) // 2} bounds={bounds}")
            raise SystemExit(0)
print(f"Text not found: {label}", file=sys.stderr)
raise SystemExit(1)
PY
)"

  local x y
  read -r x y _ <<<"$point"
  adb shell input tap "$x" "$y"
}

swipe_debug_history() {
  local size width height x start_y end_y
  size="$(adb shell wm size | awk '/Physical size:/ {print $3; exit}')"
  width="${size%x*}"
  height="${size#*x}"
  x="$((width / 2))"
  start_y="$((height * 7 / 10))"
  end_y="$((height * 2 / 10))"
  adb shell input swipe "$x" "$start_y" "$x" "$end_y" 500
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

grant_runtime_permissions() {
  adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
  adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
}

reset_app_data() {
  log "Clearing app data for an isolated live voice run"
  adb shell pm clear "$PACKAGE" >/dev/null
}

stage_rubert_bundle() {
  for name in rubert-tiny2-intent-slots.onnx vocab.txt intent_labels.txt slot_labels.txt; do
    require_file "$RUBERT_DIR/$name"
  done

  log "Staging RuBERT bundle under /data/local/tmp/offline-assistant-rubert"
  adb shell rm -rf /data/local/tmp/offline-assistant-rubert
  adb shell mkdir -p /data/local/tmp/offline-assistant-rubert
  for name in rubert-tiny2-intent-slots.onnx vocab.txt intent_labels.txt slot_labels.txt; do
    adb push "$RUBERT_DIR/$name" "/data/local/tmp/offline-assistant-rubert/$name" >/dev/null
  done
}

run_host_checks() {
  mkdir -p "$OUT_DIR"
  python3 training/test_training_layout.py
  python3 training/rubert/evaluate_export.py --model-dir "$RUBERT_DIR" --output "$RUBERT_HOST_EVAL_HOST"
  require_non_empty_file "$RUBERT_HOST_EVAL_HOST"
  require_non_empty_file "$RUBERT_HOST_METRICS_HOST"
}

launch_app() {
  log "Installing and launching debug APK"
  ./gradlew :app:installDebug
  reset_app_data
  grant_runtime_permissions
  adb shell monkey -p "$PACKAGE" 1 >/dev/null
  sleep 2
}

record_live_voice_run() {
  mkdir -p "$OUT_DIR"
  adb logcat -c || true
  adb shell rm -f "$VIDEO_DEVICE" "$SCREENSHOT_DEVICE" "$UI_DUMP_DEVICE" "$DEBUG_UI_DUMP_DEVICE" "$NAV_UI_DUMP_DEVICE"

  cat <<INSTRUCTIONS

Live voice acceptance:
1. On the phone, press the Mic button.
2. Say: "$PROMPT_TEXT"
3. Stop recording if the UI does not stop automatically.
4. Wait until the assistant renders the expected TimerCard/action widget and debug latency is available in History.

Recording starts now, allows up to ${DURATION_SECONDS}s, and stops automatically after the TimerCard appears.

INSTRUCTIONS

  adb shell screenrecord --time-limit "$DURATION_SECONDS" "$VIDEO_DEVICE" >/dev/null 2>&1 &
  local recorder_pid="$!"
  local result_found=0
  if wait_for_timer_result; then
    result_found=1
  fi
  adb shell pkill -INT screenrecord >/dev/null 2>&1 || true
  kill -INT "$recorder_pid" >/dev/null 2>&1 || true
  wait "$recorder_pid" >/dev/null 2>&1 || true

  adb shell screencap -p "$SCREENSHOT_DEVICE"
  if [[ ! -s "$UI_DUMP_HOST" ]]; then
    adb shell uiautomator dump "$UI_DUMP_DEVICE" >/dev/null
    adb pull "$UI_DUMP_DEVICE" "$UI_DUMP_HOST" >/dev/null
  fi
  adb pull "$VIDEO_DEVICE" "$VIDEO_HOST" >/dev/null
  adb pull "$SCREENSHOT_DEVICE" "$SCREENSHOT_HOST" >/dev/null

  require_non_empty_file "$VIDEO_HOST"
  require_non_empty_file "$SCREENSHOT_HOST"
  require_non_empty_file "$UI_DUMP_HOST"
  if [[ "$result_found" != "1" ]]; then
    printf 'Live voice acceptance timed out after %ss without the expected timer result.\n' "$DURATION_SECONDS" >&2
    printf 'Video, screenshot, and UI dump were saved under %s\n' "$OUT_DIR" >&2
    exit 1
  fi
  assert_ui_contains_any "Поставил таймер" "Таймер создан в системном приложении."
  assert_ui_contains "Таймер"

  cat <<DEBUG_INSTRUCTIONS

Switching to the История tab on the phone now.
Waiting ${HISTORY_WAIT_SECONDS}s before collecting debug diagnostics.

DEBUG_INSTRUCTIONS

  tap_ui_text "История"
  sleep "$HISTORY_WAIT_SECONDS"
  swipe_debug_history
  sleep 1
  adb shell uiautomator dump "$DEBUG_UI_DUMP_DEVICE" >/dev/null
  adb pull "$DEBUG_UI_DUMP_DEVICE" "$DEBUG_UI_DUMP_HOST" >/dev/null
  adb logcat -d -t 3000 > "$LOGCAT_HOST" || true

  require_non_empty_file "$DEBUG_UI_DUMP_HOST"
  require_non_empty_file "$LOGCAT_HOST"
  assert_debug_contains "transcript:"
  assert_debug_contains "intent: set_timer"
  assert_debug_contains "source: RUBERT_TINY2"
  assert_debug_contains "fallback: false"
  assert_debug_contains "latency asr"
  python3 scripts/verify_device_smoke_artifacts.py --live-voice "$OUT_DIR"
}

wait_for_timer_result() {
  local started now
  started="$(date +%s)"
  rm -f "$UI_DUMP_HOST"
  while true; do
    adb shell uiautomator dump "$UI_DUMP_DEVICE" >/dev/null 2>&1 || true
    adb pull "$UI_DUMP_DEVICE" "$UI_DUMP_HOST" >/dev/null 2>&1 || true
    if [[ -s "$UI_DUMP_HOST" ]] && grep -Fq "Таймер" "$UI_DUMP_HOST"; then
      if grep -Fq "Поставил таймер" "$UI_DUMP_HOST" || \
         grep -Fq "Таймер создан в системном приложении." "$UI_DUMP_HOST"; then
        return 0
      fi
    fi
    now="$(date +%s)"
    if (( now - started >= DURATION_SECONDS )); then
      return 1
    fi
    sleep "$UI_POLL_SECONDS"
  done
}

run_host_checks
require_device
stage_rubert_bundle
launch_app
record_live_voice_run

cat <<SUMMARY

Live voice acceptance artifacts:
- video: $VIDEO_HOST
- screenshot: $SCREENSHOT_HOST
- UI dump: $UI_DUMP_HOST
- debug UI dump: $DEBUG_UI_DUMP_HOST
- logcat: $LOGCAT_HOST

Manual acceptance criteria:
- The video shows tapping Mic.
- The spoken command is "$PROMPT_TEXT".
- Whisper transcript appears in the chat or transcript preview.
- RuBERT selects the action intent.
- The result renders TimerCard, not a generic LLM answer.
- History/debug shows ASR latency for the live human recording.

SUMMARY
