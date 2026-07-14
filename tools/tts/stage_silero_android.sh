#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BUNDLE="${SILERO_BUNDLE:-$ROOT/models/external/silero-v5_5-ru-xenia/android-bundle}"
TARGET="/data/local/tmp/offline-assistant-silero"

if ! command -v adb >/dev/null 2>&1; then
  printf 'adb is not available on PATH\n' >&2
  exit 1
fi
if [[ "$(adb devices | awk 'NR > 1 && $2 == "device" { count += 1 } END { print count + 0 }')" -lt 1 ]]; then
  printf 'No adb device is connected.\n' >&2
  exit 1
fi

required=(
  predictors.onnx acoustic.onnx window.f32 frontend.json
  accentor.onnx accentor-ngrams.json accentor-exceptions.json
  homosolver.onnx homosolver-vocab.txt homographs.json manifest.json
)
for name in "${required[@]}"; do
  if [[ ! -s "$BUNDLE/$name" ]]; then
    printf 'Missing Silero bundle file: %s\n' "$BUNDLE/$name" >&2
    exit 1
  fi
done

adb shell rm -rf "$TARGET"
adb shell mkdir -p "$TARGET"
for name in "${required[@]}"; do
  adb push "$BUNDLE/$name" "$TARGET/$name" >/dev/null
done
printf 'Silero Xenia bundle staged at %s\n' "$TARGET"
