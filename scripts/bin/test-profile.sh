#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
mode="${1:-}"
module="${2:-}"
gradle="${GRADLE_BIN:-./gradlew}"

case "$mode" in
  quick)
    "$gradle" :conversation-core:test :conversation-tools:test --no-daemon
    ;;
  changed)
    case "$module" in
      conversation-core|conversation-tools)
        task=":${module}:test"
        ;;
      app|audio-bridge|privileged-helper|realtime-client)
        task=":${module}:testDebugUnitTest"
        ;;
      *)
        echo "Usage: bash scripts/bin/test-profile.sh changed <module>" >&2
        echo "Modules: app, conversation-core, conversation-tools, audio-bridge, privileged-helper, realtime-client" >&2
        exit 2
        ;;
    esac
    "$gradle" "$task" --no-daemon
    ;;
  full)
    bash scripts/verify_host.sh
    ;;
  *)
    echo "Usage: bash scripts/bin/test-profile.sh quick | changed <module> | full" >&2
    echo "Hardware/S22 verification remains a separate, explicitly authorized gate." >&2
    exit 2
    ;;
esac
