#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
LOCK="$ROOT/tooling/deal-android-bridge/toolchain.lock"
UPDATE_LOCK=false
if [[ "${1:-}" == "--update-lock" ]]; then
  UPDATE_LOCK=true
elif [[ $# -ne 0 ]]; then
  printf 'Usage: DEAL_REPO=... DEAL_UI_REPO=... %s [--update-lock]\n' "$0" >&2
  exit 2
fi

: "${DEAL_REPO:?Set DEAL_REPO to the pinned DEAL checkout}"
: "${DEAL_UI_REPO:?Set DEAL_UI_REPO to the pinned Deal UI checkout}"
: "${ANDROID_SDK_ROOT:=/opt/homebrew/share/android-commandlinetools}"

# shellcheck source=/dev/null
source "$LOCK"

PACK="$ROOT/tooling/deal-ui-pack/deal-studio-v12.dealui-pack"
[[ -f "$PACK" ]] || { printf 'Pinned component pack is unavailable: %s\n' "$PACK" >&2; exit 1; }
ACTUAL_PACK_VERSION=$(sed -n 's/^pack version "\([^"]*\)";.*/\1/p' "$PACK")
ACTUAL_PACK_DIGEST=$(shasum -a 256 "$PACK" | awk '{print $1}')
[[ "$ACTUAL_PACK_VERSION" == "$COMPONENT_PACK_VERSION" ]] || {
  printf 'Component-pack version mismatch: expected %s, found %s\n' \
    "$COMPONENT_PACK_VERSION" "$ACTUAL_PACK_VERSION" >&2
  exit 1
}
[[ "$ACTUAL_PACK_DIGEST" == "$COMPONENT_PACK_SHA256" ]] || {
  printf 'Component-pack digest mismatch: expected %s, found %s\n' \
    "$COMPONENT_PACK_SHA256" "$ACTUAL_PACK_DIGEST" >&2
  exit 1
}

require_clean_revision() {
  local repository=$1
  local expected=$2
  local label=$3
  [[ -d "$repository/.git" ]] || { printf '%s is not a Git checkout: %s\n' "$label" "$repository" >&2; exit 1; }
  [[ -z "$(git -C "$repository" status --porcelain)" ]] || {
    printf '%s checkout is dirty: %s\n' "$label" "$repository" >&2
    exit 1
  }
  local actual
  actual=$(git -C "$repository" rev-parse HEAD)
  [[ "$actual" == "$expected" ]] || {
    printf '%s revision mismatch: expected %s, found %s\n' "$label" "$expected" "$actual" >&2
    exit 1
  }
}

require_clean_revision "$DEAL_REPO" "$DEAL_REVISION" "DEAL"
require_clean_revision "$DEAL_UI_REPO" "$DEAL_UI_REVISION" "Deal UI"

ACTUAL_JAVAC=$(javac -version 2>&1 | awk '{print $2}')
[[ "$ACTUAL_JAVAC" == "$JAVAC_VERSION" ]] || {
  printf 'javac mismatch: expected %s, found %s\n' "$JAVAC_VERSION" "$ACTUAL_JAVAC" >&2
  exit 1
}
D8="$ANDROID_SDK_ROOT/build-tools/$ANDROID_BUILD_TOOLS_VERSION/d8"
[[ -x "$D8" ]] || { printf 'Pinned d8 is unavailable: %s\n' "$D8" >&2; exit 1; }
ACTUAL_D8=$($D8 --version)
[[ "$ACTUAL_D8" == "$D8_VERSION" ]] || {
  printf 'd8 mismatch: expected %s, found %s\n' "$D8_VERSION" "$ACTUAL_D8" >&2
  exit 1
}

OUT="$ROOT/build/deal-android-toolchain"
CLASSES="$OUT/classes"
JAR="$OUT/deal-android-toolchain.jar"
ASSET="$ROOT/app/src/debug/assets/deal-android-toolchain.dex"
rm -rf "$OUT"
mkdir -p "$CLASSES" "$(dirname "$ASSET")"

(cd "$DEAL_REPO" && javac --release "$JAVAC_RELEASE" -d "$CLASSES" @build/prod-sources.txt)
javac --release "$JAVAC_RELEASE" \
  -cp "$CLASSES" \
  -d "$CLASSES" \
  "$DEAL_REPO/deal/compiler/CompilerProtocol.java" \
  "$DEAL_REPO/deal/compiler/CompilerProtocolJson.java" \
  "$DEAL_REPO/deal/compiler/DealCompilerWorkspace.java"
javac --release "$JAVAC_RELEASE" \
  -cp "$CLASSES" \
  -d "$CLASSES" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/UiModel.java" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/UiDiagnostic.java" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/UiParser.java" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/DealUiDealSource.java" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/UiBorrowedValueChecker.java" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/UiChecker.java" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/UiCompilerWorkspace.java" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/CanonicalCompiler.java" \
  "$DEAL_UI_REPO/src/main/java/deal/ui/UiIrDumper.java" \
  "$ROOT/tooling/deal-android-bridge/CanonicalDealUiJson.java" \
  "$ROOT/tooling/deal-android-bridge/CanonicalDealRuntime.java" \
  "$ROOT/tooling/deal-android-bridge/CanonicalDealToolchainBridge.java"

# Fixed ZIP timestamps and a clean output directory make identical inputs reproducible.
jar --create --date=2020-01-01T00:00:00Z --file "$JAR" -C "$CLASSES" .
"$D8" --min-api "$MIN_ANDROID_API" --output "$OUT" "$JAR"
cp "$OUT/classes.dex" "$ASSET"

DIGEST=$(shasum -a 256 "$ASSET" | awk '{print $1}')
if $UPDATE_LOCK; then
  temporary="$LOCK.tmp"
  awk -v digest="$DIGEST" '
    /^DEX_SHA256=/ { print "DEX_SHA256=" digest; next }
    { print }
  ' "$LOCK" > "$temporary"
  mv "$temporary" "$LOCK"
elif [[ "$DIGEST" != "$DEX_SHA256" ]]; then
  printf 'DEX digest mismatch: expected %s, built %s\n' "$DEX_SHA256" "$DIGEST" >&2
  printf 'Run with --update-lock only when intentionally updating pinned compiler inputs.\n' >&2
  exit 1
fi

printf 'Deal: %s\nDeal UI: %s\nPack: %s (%s)\nSHA-256: %s\n' \
  "$DEAL_REVISION" "$DEAL_UI_REVISION" "$COMPONENT_PACK_VERSION" "$COMPONENT_PACK_SHA256" "$DIGEST"
