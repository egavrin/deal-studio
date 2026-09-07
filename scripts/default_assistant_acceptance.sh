#!/usr/bin/env bash

set -euo pipefail

PACKAGE="${PACKAGE:-com.offlineassistant.poc.debug}"
ITERATIONS="${ITERATIONS:-10}"
OUTPUT_DIR="${OUTPUT_DIR:-build/default-assistant-acceptance}"
ADB=(adb)

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB+=(--serial "$ANDROID_SERIAL")
fi

mkdir -p "$OUTPUT_DIR"
REPORT="$OUTPUT_DIR/report.txt"
LOGCAT="$OUTPUT_DIR/logcat.txt"
: >"$REPORT"

fail() {
  printf 'FAIL: %s\n' "$1" | tee -a "$REPORT" >&2
  exit 1
}

wait_for_shown_state() {
  local expected="$1"
  local attempts=100
  local current
  while ((attempts > 0)); do
    current="$("${ADB[@]}" shell dumpsys voiceinteraction | grep -m1 -o 'mShown=[a-z]*' | cut -d= -f2)"
    if [[ "$current" == "$expected" ]]; then
      return 0
    fi
    sleep 0.1
    attempts=$((attempts - 1))
  done
  return 1
}

holder="$("${ADB[@]}" shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"
[[ "$holder" == "$PACKAGE" ]] || fail "assistant role holder is '$holder', expected '$PACKAGE'"

"${ADB[@]}" logcat -c
printf 'package=%s\niterations=%s\n' "$PACKAGE" "$ITERATIONS" | tee -a "$REPORT"

if "${ADB[@]}" shell dumpsys voiceinteraction | grep -q 'mShown=true'; then
  "${ADB[@]}" shell input keyevent 4
  wait_for_shown_state false || fail "existing assistant session did not dismiss"
fi

for ((iteration = 1; iteration <= ITERATIONS; iteration++)); do
  if ((iteration % 2 == 0)); then
    "${ADB[@]}" shell input keyevent 3
    host="launcher"
  else
    "${ADB[@]}" shell am start -n com.android.settings/.Settings >/dev/null
    host="settings"
  fi
  sleep 0.25
  "${ADB[@]}" shell input keyevent 219
  wait_for_shown_state true || fail "session $iteration did not become visible over $host"
  sleep 0.5
  "${ADB[@]}" shell input keyevent 4
  wait_for_shown_state false || fail "session $iteration did not dismiss over $host"
  printf 'session=%02d host=%s result=pass\n' "$iteration" "$host" | tee -a "$REPORT"
done

"${ADB[@]}" logcat -d -v threadtime >"$LOGCAT"
if rg -n "FATAL EXCEPTION|ANR in $PACKAGE|Process $PACKAGE .* has died" "$LOGCAT" >"$OUTPUT_DIR/failures.txt"; then
  fail "crash or ANR signature found; see $OUTPUT_DIR/failures.txt"
fi

printf 'result=pass\nlogcat=%s\n' "$LOGCAT" | tee -a "$REPORT"
