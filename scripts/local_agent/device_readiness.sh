#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# macOS ships Bash 3.2; an empty Bash array under 'set -u' is unbound.
# Positional arguments are safe even when no serial override was supplied.
if [[ -n "${AICALL_SERIAL:-}" ]]; then
  set -- --serial "$AICALL_SERIAL"
else
  set --
fi
SERIAL="$(PYTHONPATH=scripts python3 -m aicall_tools.device.s22_adb_discovery "$@")" || {
  echo "device_readiness_failed=exact_s22_not_connected" >&2
  exit 1
}
STATE="$(adb -s "$SERIAL" shell dumpsys telephony.registry | sed -n 's/.*mCallState=\([0-9]\).*/\1/p' | head -n1)"
if [[ "$STATE" != "0" ]]; then
  echo "device_readiness_failed=phone_not_idle,state:${STATE:-unknown}" >&2
  exit 1
fi
scripts/bin/aicalls readiness --serial "$SERIAL"
echo "selected_adb_serial=$SERIAL"
echo "device_readiness=true"
echo "call_state=IDLE"
