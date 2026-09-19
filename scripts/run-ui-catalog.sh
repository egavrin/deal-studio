#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"
CASE_ID=""
STYLE="clean"
OUTPUT=""
SKIP_INSTALL=false

while (($#)); do
  case "$1" in
    --serial) SERIAL="${2:?missing --serial value}"; shift 2 ;;
    --case) CASE_ID="${2:?missing --case value}"; shift 2 ;;
    --style) STYLE="${2:?missing --style value}"; shift 2 ;;
    --output) OUTPUT="${2:?missing --output value}"; shift 2 ;;
    --skip-install) SKIP_INSTALL=true; shift ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

[[ -n "$SERIAL" ]] || { echo "Provide --serial or ANDROID_SERIAL" >&2; exit 2; }
[[ -n "$CASE_ID" ]] || { echo "Provide --case" >&2; exit 2; }
case "$STYLE" in
  clean|soft|expressive|editorial|technical|playful) ;;
  *) echo "Unsupported style: $STYLE" >&2; exit 2 ;;
esac
command -v adb >/dev/null || { echo "adb is not available on PATH" >&2; exit 2; }
adb -s "$SERIAL" get-state >/dev/null

cd "$REPO_ROOT"
if [[ "$SKIP_INSTALL" != true ]]; then
  ./gradlew :app:installDebug
fi
adb -s "$SERIAL" shell am force-stop com.dealstudio.app.debug
adb -s "$SERIAL" shell am start -W \
  -n com.dealstudio.app.debug/com.offlineassistant.app.generatedapp.UiCatalogActivity \
  --es ui_catalog_case_id "$CASE_ID" \
  --es ui_catalog_style "$STYLE" >/dev/null

READY="ui-catalog-ready:$CASE_ID"
FAILED="ui-catalog-failed:$CASE_ID"
for _ in $(seq 1 40); do
  TREE="$(adb -s "$SERIAL" exec-out uiautomator dump /dev/tty 2>/dev/null || true)"
  if [[ "$TREE" == *"$READY"* ]]; then
    if [[ -n "$OUTPUT" ]]; then
      mkdir -p "$(dirname "$OUTPUT")"
      adb -s "$SERIAL" exec-out screencap -p >"$OUTPUT"
    fi
    echo "UI catalog ready: case=$CASE_ID style=$STYLE${OUTPUT:+ screenshot=$OUTPUT}"
    exit 0
  fi
  if [[ "$TREE" == *"$FAILED"* ]]; then
    echo "UI catalog fixture failed: case=$CASE_ID style=$STYLE" >&2
    exit 1
  fi
  sleep 0.5
done

echo "Timed out waiting for UI catalog readiness: case=$CASE_ID style=$STYLE" >&2
exit 1
