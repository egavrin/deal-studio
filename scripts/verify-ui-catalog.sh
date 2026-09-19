#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"
MATRIX="tooling/deal-ui-pack/gallery/matrix.json"
OUTPUT="build/ui-catalog/evidence"

while (($#)); do
  case "$1" in
    --serial) SERIAL="${2:?missing --serial value}"; shift 2 ;;
    --matrix) MATRIX="${2:?missing --matrix value}"; shift 2 ;;
    --output) OUTPUT="${2:?missing --output value}"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

[[ -n "$SERIAL" ]] || { echo "Provide --serial or ANDROID_SERIAL" >&2; exit 2; }
command -v adb >/dev/null || { echo "adb is not available on PATH" >&2; exit 2; }
adb -s "$SERIAL" get-state >/dev/null

cd "$REPO_ROOT"
[[ -f "$MATRIX" ]] || { echo "Matrix not found: $MATRIX" >&2; exit 2; }
mkdir -p "$OUTPUT"
./gradlew :app:installDebug

while IFS=$'\t' read -r case_id style; do
  scripts/run-ui-catalog.sh \
    --serial "$SERIAL" \
    --case "$case_id" \
    --style "$style" \
    --output "$OUTPUT/$case_id-$style.png" \
    --skip-install </dev/null
done < <(python3 - "$MATRIX" <<'PY'
import json, sys
matrix = json.load(open(sys.argv[1], encoding="utf-8"))
if matrix.get("schemaVersion") != "deal-studio-ui-catalog-matrix-v1":
    raise SystemExit("Unsupported UI catalog matrix")
for case in matrix.get("cases", []):
    for style in case.get("styles", []):
        print(f"{case['id']}\t{style}")
PY
)

SIZE="$(adb -s "$SERIAL" shell wm size | tr -d '\r' | tail -1)"
DENSITY="$(adb -s "$SERIAL" shell wm density | tr -d '\r' | tail -1)"
PACK_VERSION="$(sed -n 's/^COMPONENT_PACK_VERSION=//p' tooling/deal-android-bridge/toolchain.lock)"
PACK_SHA="$(sed -n 's/^COMPONENT_PACK_SHA256=//p' tooling/deal-android-bridge/toolchain.lock)"
python3 - "$MATRIX" "$OUTPUT/evidence.json" "$SERIAL" "$SIZE" "$DENSITY" "$PACK_VERSION" "$PACK_SHA" <<'PY'
import hashlib, json, pathlib, sys
matrix_path, output_path, serial, size, density, pack_version, pack_sha = sys.argv[1:]
matrix = json.load(open(matrix_path, encoding="utf-8"))
fixtures = json.load(open("app/src/debug/assets/ui-catalog/manifest.json", encoding="utf-8"))
fixture_by_id = {case["id"]: case for case in fixtures["cases"]}
output = pathlib.Path(output_path)
records = []
for case in matrix["cases"]:
    fixture = fixture_by_id[case["id"]]
    fixture_pack_version = fixture["packVersion"]
    fixture_pack_number = fixture_pack_version.rsplit("v", 1)[1]
    fixture_pack = pathlib.Path(f"tooling/deal-ui-pack/deal-studio-v{fixture_pack_number}.dealui-pack")
    fixture_pack_sha = hashlib.sha256(fixture_pack.read_bytes()).hexdigest()
    for style in case["styles"]:
        screenshot = output.parent / f"{case['id']}-{style}.png"
        if not screenshot.is_file() or screenshot.stat().st_size == 0:
            raise SystemExit(f"Missing screenshot: {screenshot}")
        records.append({
            "caseId": case["id"],
            "style": style,
            "componentPackVersion": fixture_pack_version,
            "componentPackSha256": fixture_pack_sha,
            "compileAndRender": "PASS",
            "visualReview": "PENDING",
            "screenshot": screenshot.name,
            "screenshotSha256": hashlib.sha256(screenshot.read_bytes()).hexdigest(),
        })
report = {
    "schemaVersion": "deal-studio-ui-catalog-evidence-v1",
    "deviceSerial": serial,
    "physicalSize": size,
    "density": density,
    "activeComponentPackVersion": pack_version,
    "activeComponentPackSha256": pack_sha,
    "records": records,
}
output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

echo "UI catalog evidence written: $OUTPUT/evidence.json"
