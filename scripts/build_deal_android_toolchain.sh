#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
DEAL_REPO=${DEAL_REPO:-/Users/egavrin/Documents/codex/2026-09-02/new-chat/work/deal-reference}
DEAL_UI_REPO=${DEAL_UI_REPO:-/Users/egavrin/Documents/codex/2026-09-02/new-chat/work/deal-ui-reference}
ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}
BUILD_TOOLS_VERSION=${BUILD_TOOLS_VERSION:-37.0.0}
OUT="$ROOT/build/deal-android-toolchain"
CLASSES="$OUT/classes"
JAR="$OUT/deal-android-toolchain.jar"
ASSET="$ROOT/app/src/debug/assets/deal-android-toolchain.dex"

mkdir -p "$CLASSES" "$(dirname "$ASSET")"
(cd "$DEAL_REPO" && javac --release 25 -d "$CLASSES" @build/prod-sources.txt)
javac --release 25 \
  -cp "$CLASSES" \
  -d "$CLASSES" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/UiModel.java" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/UiDiagnostic.java" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/UiParser.java" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/UiChecker.java" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/UiIrDumper.java" \
  "$ROOT/tooling/deal-android-bridge/CanonicalDealUiJson.java" \
  "$ROOT/tooling/deal-android-bridge/CanonicalDealRuntime.java" \
  "$ROOT/tooling/deal-android-bridge/CanonicalDealToolchainBridge.java"
jar --create --file "$JAR" -C "$CLASSES" .
"$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/d8" \
  --min-api 26 \
  --output "$OUT" \
  "$JAR"
cp "$OUT/classes.dex" "$ASSET"

DEAL_REV=$(git -C "$DEAL_REPO" rev-parse HEAD)
DEAL_UI_REV=$(git -C "$DEAL_UI_REPO" rev-parse HEAD)
DIGEST=$(shasum -a 256 "$ASSET" | awk '{print $1}')
printf 'Deal: %s\nDeal UI: %s\nSHA-256: %s\n' "$DEAL_REV" "$DEAL_UI_REV" "$DIGEST"
