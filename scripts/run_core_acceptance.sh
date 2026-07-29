#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

python3 scripts/check_core_scope.py
./gradlew \
  testDebugUnitTest \
  :core:test \
  :deepseek-connector:testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin \
  ktlintCheck \
  detekt \
  lintDebug \
  assembleDebug
