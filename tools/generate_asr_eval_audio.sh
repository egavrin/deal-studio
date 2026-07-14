#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOC_AUDIO_DIR="$ROOT_DIR/docs/testing/audio/asr-eval"
ANDROID_ASSET_DIR="$ROOT_DIR/app/src/androidTest/assets/asr_eval"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$DOC_AUDIO_DIR" "$ANDROID_ASSET_DIR"

generate_case() {
  local name="$1"
  local text="$2"
  local aiff="$TMP_DIR/$name.aiff"
  local wav="$DOC_AUDIO_DIR/$name.wav"

  say -v Milena -r 170 -o "$aiff" "$text"
  afconvert -f WAVE -d LEI16@16000 "$aiff" "$wav"
  cp "$wav" "$ANDROID_ASSET_DIR/$name.wav"
}

generate_case "weather-moscow" "Какая погода в Москве сегодня?"
generate_case "timer-five-minutes" "Поставь таймер на пять минут."
generate_case "calculator-eighteen-times-three" "Посчитай 18 умножить на 3."
generate_case "note-buy-milk" "Создай заметку купить молоко."

cp "$ROOT_DIR/docs/testing/audio/asr-eval-manifest.jsonl" "$ANDROID_ASSET_DIR/manifest.jsonl"

echo "Generated ASR eval audio in $DOC_AUDIO_DIR and copied assets to $ANDROID_ASSET_DIR"
