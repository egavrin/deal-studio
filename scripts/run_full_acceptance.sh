#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT/build/device-smoke"
RUBERT_DIR="$ROOT/models/generated/rubert"
HOST_ONLY=0
WAIT_FOR_DEVICE=0
WAIT_TIMEOUT_SECONDS=900

usage() {
    cat <<'USAGE'
Usage: scripts/run_full_acceptance.sh [--host-only] [--wait-for-device] [--wait-timeout-seconds N]

Runs the full PoC acceptance path:
  1. Host preflight checks.
  2. Connected device smoke runner.
  3. Live human voice acceptance runner.
  4. Artifact verification.

Use --host-only when the Android phone is disconnected. This proves the local
repo, training layout, generated RuBERT bundle, scripts, unit tests, and build
are ready, but it does not complete the product acceptance goal.

Use --wait-for-device to run host preflight now and wait for adb to report a
connected Android device before starting the phone acceptance stages.
USAGE
}

log() {
    printf '\n==> %s\n' "$*"
}

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

while (($#)); do
    case "$1" in
        --host-only)
            HOST_ONLY=1
            shift
            ;;
        --wait-for-device)
            WAIT_FOR_DEVICE=1
            shift
            ;;
        --wait-timeout-seconds)
            WAIT_TIMEOUT_SECONDS="${2:-}"
            [[ "$WAIT_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || fail "--wait-timeout-seconds requires an integer value"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            fail "Unknown argument: $1"
            ;;
    esac
done

cd "$ROOT"
mkdir -p "$OUT_DIR"

run_host_preflight() {
    log "Running host preflight"
    python3 training/test_training_layout.py
    python3 training/rubert/evaluate_export.py \
        --model-dir "$RUBERT_DIR" \
        --output "$OUT_DIR/rubert-host-eval-full-acceptance.jsonl"
    test -s "$OUT_DIR/rubert-host-eval-full-acceptance.jsonl"
    test -s "$OUT_DIR/rubert-host-eval-full-acceptance-metrics.json"

    python3 scripts/test_device_smoke_script.py
    python3 training/test_evaluate_export_metrics.py
    python3 scripts/test_qwen_generation_policy.py
    python3 scripts/test_live_voice_acceptance_script.py
    python3 scripts/test_verify_device_smoke_artifacts.py
    python3 scripts/test_design_board.py
    python3 scripts/test_full_acceptance_script.py
    python3 scripts/test_acceptance_status.py
    python3 scripts/test_final_dod_status.py

    ./gradlew test :app:compileDebugAndroidTestKotlin assembleDebug :app:compileReleaseKotlin
}

require_device() {
    local devices
    devices="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
    if [[ -z "$devices" ]]; then
        fail "Full acceptance still requires a connected Android device. Re-run with --host-only for local preflight only."
    fi
}

wait_for_device() {
    if [[ "$WAIT_FOR_DEVICE" != "1" ]]; then
        return 0
    fi

    log "Waiting for connected Android device"
    local start now
    start="$(date +%s)"
    while true; do
        if adb devices | awk 'NR > 1 && $2 == "device" { found=1 } END { exit found ? 0 : 1 }'; then
            log "Connected Android device detected"
            return 0
        fi
        now="$(date +%s)"
        if (( now - start >= WAIT_TIMEOUT_SECONDS )); then
            fail "Timed out waiting for a connected Android device after ${WAIT_TIMEOUT_SECONDS}s"
        fi
        sleep 5
    done
}

run_phone_acceptance() {
    log "Running connected device smoke acceptance"
    scripts/device_smoke_test.sh
    test -s "$OUT_DIR/offline-assistant-demo-flow.mp4"
    python3 scripts/verify_device_smoke_artifacts.py "$OUT_DIR"

    log "Running live human voice acceptance"
    scripts/live_voice_acceptance.sh
    test -s "$OUT_DIR/offline-assistant-live-voice.mp4"
    python3 scripts/verify_device_smoke_artifacts.py --live-voice "$OUT_DIR"
    python3 scripts/acceptance_status.py "$OUT_DIR"
    python3 scripts/final_dod_status.py --artifact-dir "$OUT_DIR"
}

run_host_preflight

if [[ "$HOST_ONLY" == "1" ]]; then
    log "Host preflight passed; phone acceptance not run because --host-only was requested"
    exit 0
fi

wait_for_device
require_device
run_phone_acceptance

log "Full Android Offline Assistant PoC acceptance path completed"
