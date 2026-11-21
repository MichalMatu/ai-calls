#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
command -v adb >/dev/null || { echo UI_LAB_OPEN=NO_ADB; exit 4; }
serial="$(printenv ANDROID_SERIAL || true)"
if [[ -z "$serial" ]]; then
  devices="$(adb devices | awk '$1 ~ /^emulator-[0-9]+$/ && $2 == "device" {print $1}')"
  [[ "$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')" == 1 ]] || {
    echo UI_LAB_OPEN=ONE_EMULATOR_REQUIRED; exit 4;
  }
  serial="$devices"
fi
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || { echo UI_LAB_REFUSED=PHYSICAL_DEVICE; exit 5; }
[[ "$(adb -s "$serial" get-state)" == device ]] || { echo UI_LAB_OPEN=EMULATOR_OFFLINE; exit 4; }
[[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || {
   echo UI_LAB_REFUSED=NOT_EMULATED; exit 5;
}
"${GRADLE_BIN:-./gradlew}" :app:assembleDebug :app:assembleDebugAndroidTest
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
test_classes=(
  pl.michalmatu.aicallbridge.AndroidAppBottomNavigationFlowTest
  pl.michalmatu.aicallbridge.AndroidUiPolishContractTest
  pl.michalmatu.aicallbridge.AndroidLabDialogStorageAcceptanceTest
  pl.michalmatu.aicallbridge.conversation.AndroidLabUxFlowTest
)
classes="$(IFS=,; echo "${test_classes[*]}")"
if ! output="$(adb -s "$serial" shell am instrument -r -w \
  -e class "$classes" \
  pl.michalmatu.aicallbridge.s8lab.test/androidx.test.runner.AndroidJUnitRunner)"; then
  printf '%s\n' "$output" | tail -35 >&2
  echo UI_LAB_INSTRUMENTATION_COMMAND_FAIL=true >&2
  exit 6
fi
if ! count="$(printf '%s\n' "$output" | python3 scripts/aicall_tools/diagnostics/instrumentation_result.py --expected-count 12)"; then
  printf '%s\n' "$output" | tail -35 >&2
  exit 6
fi
printf '%s\n' "$count"
echo UI_LAB_EMULATOR_PASS=true
