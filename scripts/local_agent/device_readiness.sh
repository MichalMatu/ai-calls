#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SERIAL="${AICALL_SERIAL:-RFCT70L7E8J}"
cd "$ROOT"
if ! adb devices -l | grep -qE "^${SERIAL}[[:space:]]+device([[:space:]]|$)"; then
  echo "device_readiness_failed=exact_s22_not_connected" >&2
  exit 1
fi
STATE="$(adb -s "$SERIAL" shell dumpsys telephony.registry | sed -n 's/.*mCallState=\([0-9]\).*/\1/p' | head -n1)"
if [[ "$STATE" != "0" ]]; then
  echo "device_readiness_failed=phone_not_idle,state:${STATE:-unknown}" >&2
  exit 1
fi
scripts/bin/aicalls readiness --serial "$SERIAL"
echo "device_readiness=true"
echo "call_state=IDLE"
