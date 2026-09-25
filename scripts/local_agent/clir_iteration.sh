#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SERIAL="${AICALL_SERIAL:-RFCT70L7E8J}"
cd "$ROOT"

if [[ "${1:-}" != "--allow-clir-enable" ]]; then
  echo "usage: scripts/local_agent/clir_iteration.sh --allow-clir-enable" >&2
  exit 2
fi
shift
if [[ "$#" -ne 0 ]]; then
  echo "clir_iteration_failed=unexpected_arguments" >&2
  exit 2
fi

scripts/local_agent/device_readiness.sh

RUN_RC=0
scripts/bin/aicalls relay-live --serial "$SERIAL" --allow-clir-enable || RUN_RC=$?
echo "clir_live_runner_rc=$RUN_RC"

STATE=""
for _ in 1 2 3 4 5 6 7 8 9 10; do
  STATE="$(adb -s "$SERIAL" shell dumpsys telephony.registry 2>/dev/null | sed -n 's/.*mCallState=\([0-9]\).*/\1/p' | head -n1 || true)"
  [[ "$STATE" == "0" ]] && break
  sleep 1
done

if [[ "$STATE" != "0" ]]; then
  echo "clir_iteration_failed=phone_not_idle_after_runner,state:${STATE:-unknown}" >&2
  exit 1
fi

echo "clir_iteration_call_state=IDLE"
exit "$RUN_RC"
