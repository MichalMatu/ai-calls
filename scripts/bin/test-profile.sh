#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
mode="${1:-}"
if [[ "$#" -gt 0 ]]; then shift; fi
gradle="${GRADLE_BIN:-./gradlew}"
# The full host gate already discovers local Android SDKs; focused/auto builds need
# the same environment, otherwise they fail before Gradle can reuse its cache.
if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  if [[ -d "$HOME/Library/Android/sdk" ]]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
  elif [[ -d "$HOME/Android/Sdk" ]]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
  fi
fi
case "$mode" in
  preflight)
    [[ "$#" -eq 0 ]] || exit 2
    git diff --check
    git diff --cached --check
    python3 scripts/aicall_tools/diagnostics/audit_product_friction.py
    python3 scripts/aicall_tools/diagnostics/module_boundary_gate.py
    python3 scripts/aicall_tools/diagnostics/docs_check.py
    echo DEV_PREFLIGHT_PASS=true ;;
  auto)
    [[ "$#" -le 1 ]] || exit 2
    python3 -m scripts.aicall_tools.diagnostics.dev_change_gate "${1:-HEAD^}" ;;
  quick)
    [[ "$#" -eq 0 ]] || exit 2
    "$gradle" :conversation-core:test :conversation-tools:test ;;
  changed)
    [[ "$#" -eq 1 ]] || exit 2
    module="$1"
    case "$module" in
      conversation-core|conversation-tools) task=":$module:test" ;;
      app|audio-bridge|privileged-helper|realtime-client) task=":$module:testDebugUnitTest" ;;
      *) echo 'usage: test-profile.sh changed <module>' >&2; exit 2 ;;
    esac
    "$gradle" "$task" ;;
  focus)
    [[ "$#" -eq 2 ]] || { echo 'usage: test-profile.sh focus <module> <test-class-filter>' >&2; exit 2; }
    module="$1"; filter="$2"
    case "$module" in
      conversation-core|conversation-tools) task=":$module:test" ;;
      app|audio-bridge|privileged-helper|realtime-client) task=":$module:testDebugUnitTest" ;;
      *) exit 2 ;;
    esac
    "$gradle" "$task" --tests "$filter" ;;
  full)
    [[ "$#" -eq 0 ]] || exit 2
    bash scripts/verify_host.sh ;;
  device-build)
    [[ "$#" -eq 0 ]] || exit 2
    "$gradle" :app:assembleDebug :app:assembleDebugAndroidTest
    echo DEV_APK_PACKAGED=true ;;
  lab-ui-prepare)
    [[ "$#" -eq 0 ]] || exit 2
    "$gradle" :app:compileDebugAndroidTestKotlin
    echo DEV_UI_INSTRUMENTATION_COMPILED=true ;;
  lab-ui)
    [[ "$#" -eq 0 ]] || exit 2
    bash scripts/bin/run-ui-lab.sh ;;
  hooks-enable)
    [[ "$#" -eq 0 ]] || exit 2
    git config --local core.hooksPath scripts/bin/git-hooks
    echo DEV_LOCAL_LIGHT_HOOKS_ENABLED=true ;;
  *)
    echo 'usage: test-profile.sh preflight | auto [base-SHA] | quick | changed <module> | focus <module> <test-filter> | full | device-build | lab-ui-prepare | lab-ui | hooks-enable' >&2
    echo 'device-build packages APKs. lab-ui builds and installs on an emulator.' >&2
    exit 2 ;;
esac
