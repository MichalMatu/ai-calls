#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# Readiness is useful, but it cannot establish ownership of a subsequent call.
# The old relay-live path used device-wide END_CALL and could terminate another call.
if [[ "${1:-}" != "--allow-clir-enable" || "$#" -ne 1 ]]; then
  echo "usage: scripts/local_agent/clir_iteration.sh --allow-clir-enable" >&2
  exit 2
fi
echo "clir_iteration_failed=exact_call_lifecycle_controller_unavailable" >&2
echo "use scripts/local_agent/device_readiness.sh for non-dialing preflight" >&2
echo "the legacy global hangup path must not be resumed" >&2
exit 3
