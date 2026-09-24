#!/usr/bin/env bash
set -euo pipefail

GRADLE_BIN="${GRADLE_BIN:-./gradlew}"

allowed_root_entries=(
  .github
  .gitignore
  AGENTS.md
  README.md
  app
  audio-bridge
  benchmarks
  build.gradle.kts
  docs
  gradle
  gradle.properties
  gradlew
  gradlew.bat
  privileged-helper
  realtime-client
  scripts
  service-packs
  settings.gradle.kts
)

tracked_root_entries="$(git ls-files | awk -F/ '{print $1}' | sort -u)"
unexpected_root_entries="$(comm -23 \
  <(printf '%s\n' "$tracked_root_entries") \
  <(printf '%s\n' "${allowed_root_entries[@]}" | sort -u))"
if [[ -n "$unexpected_root_entries" ]]; then
  echo "repository_layout_failed=unexpected_top_level_entries" >&2
  printf '%s\n' "$unexpected_root_entries" >&2
  echo "See docs/REPOSITORY_LAYOUT.md before adding a new top-level path." >&2
  exit 1
fi

allowed_scripts_entries=(
  README.md
  aicall_tools
  bin
  local_agent
  tests
  verify_host.sh
)

tracked_scripts_entries="$(git ls-files scripts | awk -F/ 'NF >= 2 {print $2}' | sort -u)"
unexpected_scripts_entries="$(comm -23 \
  <(printf '%s\n' "$tracked_scripts_entries") \
  <(printf '%s\n' "${allowed_scripts_entries[@]}" | sort -u))"
if [[ -n "$unexpected_scripts_entries" ]]; then
  echo "scripts_layout_failed=unexpected_top_level_entries" >&2
  printf '%s\n' "$unexpected_scripts_entries" >&2
  echo "See docs/REPOSITORY_LAYOUT.md and scripts/README.md for placement rules." >&2
  exit 1
fi

flat_python_files="$(find scripts -maxdepth 1 -type f -name '*.py' -print)"
if [[ -n "$flat_python_files" ]]; then
  echo "scripts_layout_failed=top_level_python_files" >&2
  printf '%s\n' "$flat_python_files" >&2
  exit 1
fi

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  if [[ -d "$HOME/Library/Android/sdk" ]]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
  elif [[ -d "$HOME/Android/Sdk" ]]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
  fi
fi

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
PYTHONPATH=scripts python3 -m unittest discover -s scripts/tests -t scripts -p 'test_*.py'

if grep -RInE --include='*.kt' --include='*.java' --include='*.xml' \
  '(OPENAI_API_KEY|sk-[A-Za-z0-9_-]{8,}|apiKey|api_key)' \
  app/src/main realtime-client/src/main privileged-helper/src/main audio-bridge/src/main; then
  echo "production_secret_scan_failed=true" >&2
  exit 1
fi

if grep -nE '(tel:|KEYCODE_CALL|KEYCODE_ENDCALL|ACTION_CALL)' scripts/aicall_tools/realtime/realtime_live_call_smoke.py; then
  echo "live_runner_telephony_control_scan_failed=true" >&2
  exit 1
fi

git diff --check
echo "host_quality_gate_green=true"
