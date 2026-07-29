#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PACKAGE="com.offlineassistant.poc.debug"
RUBERT_DIR="models/generated/rubert"
SILERO_DIR="models/external/silero-v5_5-ru-xenia/android-bundle"

adb wait-for-device
test "$(adb devices | awk 'NR > 1 && $2 == "device" {count++} END {print count + 0}')" -eq 1

for file in rubert-tiny2-intent-slots.onnx vocab.txt intent_labels.txt slot_labels.txt; do
  test -s "$RUBERT_DIR/$file"
done
for file in predictors.onnx acoustic.onnx window.f32 frontend.json accentor.onnx \
  accentor-ngrams.json accentor-exceptions.json homosolver.onnx homosolver-vocab.txt \
  homographs.json; do
  test -s "$SILERO_DIR/$file"
done

python3 scripts/check_core_scope.py
./gradlew assembleDebug

adb shell rm -rf /data/local/tmp/offline-assistant-rubert
adb shell mkdir -p /data/local/tmp/offline-assistant-rubert
for file in rubert-tiny2-intent-slots.onnx vocab.txt intent_labels.txt slot_labels.txt; do
  adb push "$RUBERT_DIR/$file" "/data/local/tmp/offline-assistant-rubert/$file"
done
adb shell mkdir -p /data/local/tmp/offline-assistant-silero
adb push "$SILERO_DIR/." /data/local/tmp/offline-assistant-silero/

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || \
  echo "RECORD_AUDIO must be granted through the device UI on this OEM build."
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true
adb shell am force-stop "$PACKAGE"
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 3

adb shell uiautomator dump /sdcard/offline-assistant-core.xml >/dev/null
adb shell cat /sdcard/offline-assistant-core.xml | grep -q "Ассистент"
echo "Core app installed and launched on $(adb shell getprop ro.product.model | tr -d '\r')."
