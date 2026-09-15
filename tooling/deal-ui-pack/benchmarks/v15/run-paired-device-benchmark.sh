#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 --serial SERIAL --output DIR --baseline-app APK --baseline-test APK --candidate-app APK --candidate-test APK [--repeats N] [--package ID] [--test-package ID]" >&2
  exit 2
}

SERIAL=""
OUTPUT=""
BASELINE_APP=""
BASELINE_TEST=""
CANDIDATE_APP=""
CANDIDATE_TEST=""
REPEATS=5
PACKAGE_ID="com.dealstudio.app.debug"
TEST_PACKAGE_ID="com.dealstudio.app.debug.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.offlineassistant.app.generatedapp.CanonicalPackV15GenerationDeviceTest"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) SERIAL="${2:-}"; shift 2 ;;
    --output) OUTPUT="${2:-}"; shift 2 ;;
    --baseline-app) BASELINE_APP="${2:-}"; shift 2 ;;
    --baseline-test) BASELINE_TEST="${2:-}"; shift 2 ;;
    --candidate-app) CANDIDATE_APP="${2:-}"; shift 2 ;;
    --candidate-test) CANDIDATE_TEST="${2:-}"; shift 2 ;;
    --repeats) REPEATS="${2:-}"; shift 2 ;;
    --package) PACKAGE_ID="${2:-}"; shift 2 ;;
    --test-package) TEST_PACKAGE_ID="${2:-}"; shift 2 ;;
    *) usage ;;
  esac
done

[[ -n "$SERIAL" && -n "$OUTPUT" ]] || usage
[[ "$REPEATS" =~ ^[1-9][0-9]*$ ]] || { echo "--repeats must be a positive integer" >&2; exit 2; }
for apk in "$BASELINE_APP" "$BASELINE_TEST" "$CANDIDATE_APP" "$CANDIDATE_TEST"; do
  [[ -f "$apk" ]] || { echo "Required APK is missing: $apk" >&2; exit 2; }
done
command -v adb >/dev/null || { echo "adb is required" >&2; exit 2; }
command -v python3 >/dev/null || { echo "python3 is required to read the checked benchmark dataset" >&2; exit 2; }
adb devices | awk 'NR > 1 && $2 == "device" {print $1}' | grep -Fxq "$SERIAL" || {
  echo "The explicitly selected adb serial is not connected and authorized: $SERIAL" >&2
  exit 2
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
DATASET="$REPO_ROOT/app/src/androidTest/assets/pack-v15-benchmark-v1.json"
CASES=()
while IFS= read -r case_id; do
  CASES+=("$case_id")
done < <(python3 - "$DATASET" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as source:
    data = json.load(source)
for case in data["cases"]:
    print(case["id"])
PY
)
[[ ${#CASES[@]} -gt 0 ]] || { echo "Benchmark dataset has no cases" >&2; exit 2; }

mkdir -p "$OUTPUT"
SCHEDULE="$OUTPUT/schedule.csv"
echo "sequence,repetition,case_id,variant,run_id,outcome" > "$SCHEDULE"
cat > "$OUTPUT/visual-evidence.json" <<'JSON'
{
  "schemaVersion": "deal-studio-visual-evidence-v1",
  "compactLight": "PENDING",
  "phoneLight": "PENDING",
  "phoneDark": "PENDING",
  "unfoldedLight": "PENDING",
  "fontScale": "PENDING",
  "talkBack": "PENDING",
  "reason": "No stable benchmark navigation and screenshot API is available; collect these gates manually."
}
JSON

sequence=0
failures=0
run_one() {
  local variant="$1" app_apk="$2" test_apk="$3" repetition="$4" case_id="$5"
  local expected_pack_version
  if [[ "$variant" == "v14-baseline" ]]; then
    expected_pack_version="deal-studio-dealui-pack-v14"
  else
    expected_pack_version="deal-studio-dealui-pack-v15"
  fi
  local run_id="${variant}-r${repetition}-${case_id}"
  local destination="$OUTPUT/$variant/repetition-$repetition/$case_id"
  mkdir -p "$destination"
  sequence=$((sequence + 1))
  if ! adb -s "$SERIAL" install -r -d "$app_apk" > "$destination/app-install.txt" 2>&1 ||
      ! adb -s "$SERIAL" install -r -d "$test_apk" > "$destination/test-install.txt" 2>&1; then
    echo "$sequence,$repetition,$case_id,$variant,$run_id,FAIL_INSTALL" >> "$SCHEDULE"
    failures=$((failures + 1))
    return
  fi
  if adb -s "$SERIAL" shell am instrument -w -r \
      -e class "$TEST_CLASS" \
      -e benchmark_case "$case_id" \
      -e run_id "$run_id" \
      -e expected_pack_version "$expected_pack_version" \
      "$TEST_PACKAGE_ID/$RUNNER" > "$destination/instrumentation.txt"; then
    outcome="PASS"
  else
    outcome="FAIL"
  fi
  if ! adb -s "$SERIAL" exec-out run-as "$PACKAGE_ID" \
      tar -C files/pack-v15-generation -cf - "$run_id" 2> "$destination/pull-error.txt" \
      | tar -xf - -C "$destination"; then
    echo "$sequence,$repetition,$case_id,$variant,$run_id,FAIL_PULL" >> "$SCHEDULE"
    echo "Could not pull benchmark artifacts for $run_id" >&2
    failures=$((failures + 1))
    return
  fi
  echo "$sequence,$repetition,$case_id,$variant,$run_id,$outcome" >> "$SCHEDULE"
  if [[ "$outcome" != "PASS" ]]; then
    echo "Instrumentation failed for $run_id; see $destination/instrumentation.txt" >&2
    failures=$((failures + 1))
  fi
}

for ((repetition = 1; repetition <= REPEATS; repetition++)); do
  if ((repetition % 2 == 1)); then
    variants=("v14-baseline" "v15-candidate")
  else
    variants=("v15-candidate" "v14-baseline")
  fi
  for case_id in "${CASES[@]}"; do
    for variant in "${variants[@]}"; do
      if [[ "$variant" == "v14-baseline" ]]; then
        run_one "$variant" "$BASELINE_APP" "$BASELINE_TEST" "$repetition" "$case_id"
      else
        run_one "$variant" "$CANDIDATE_APP" "$CANDIDATE_TEST" "$repetition" "$case_id"
      fi
    done
  done
done

echo "Completed paired benchmark with $failures failed runs. Schedule and artifacts: $OUTPUT"
((failures == 0)) || exit 1
