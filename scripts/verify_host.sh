#!/usr/bin/env bash
set -euo pipefail

GRADLE_BIN="${GRADLE_BIN:-gradle}"

"$GRADLE_BIN" \
  :realtime-client:testDebugUnitTest \
  :app:testDebugUnitTest \
  :privileged-helper:testDebugUnitTest \
  :audio-bridge:testDebugUnitTest \
  --no-daemon

"$GRADLE_BIN" \
  :app:lintDebug \
  :realtime-client:lintDebug \
  :privileged-helper:lintDebug \
  :audio-bridge:lintDebug \
  --no-daemon

"$GRADLE_BIN" \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest \
  --no-daemon
python3 -m unittest discover -s scripts -p 'test_*.py'

if grep -RInE --include='*.kt' --include='*.java' --include='*.xml' \
  '(OPENAI_API_KEY|sk-[A-Za-z0-9_-]{8,}|apiKey|api_key)' \
  app/src/main realtime-client/src/main privileged-helper/src/main audio-bridge/src/main; then
  echo "production_secret_scan_failed=true" >&2
  exit 1
fi

if grep -nE '(tel:|KEYCODE_CALL|KEYCODE_ENDCALL|ACTION_CALL)' scripts/realtime_live_call_smoke.py; then
  echo "live_runner_telephony_control_scan_failed=true" >&2
  exit 1
fi

git diff --check
echo "host_quality_gate_green=true"
