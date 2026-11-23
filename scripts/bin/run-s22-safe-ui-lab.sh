#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

# Explicit approval and exact phone only; no dialing, user data or schedulers.
[[ "$#" == 1 && "$1" == "--approved-idle" ]] ||
  { echo 'Usage: ANDROID_SERIAL=<S22> run-s22-safe-ui-lab.sh --approved-idle' >&2; exit 2; }
[[ -n "$(printenv ANDROID_SERIAL || true)" ]] || { echo SERIAL_REQUIRED=true >&2; exit 3; }
command -v adb >/dev/null || { echo ADB_REQUIRED=true >&2; exit 3; }
[[ "$(adb -s "$ANDROID_SERIAL" get-state)" == device ]] ||
  { echo DEVICE_UNAVAILABLE=true >&2; exit 3; }
[[ "$(adb -s "$ANDROID_SERIAL" shell getprop ro.product.model | tr -d '\r')" == SM-S906B ]] ||
  { echo WRONG_PHONE=true >&2; exit 3; }

pkg=pl.michalmatu.aicallbridge.s8lab
uri="content://$pkg.gsmbinder"
phone_idle() {
  local state mode
  state="$(adb -s "$ANDROID_SERIAL" shell dumpsys telephony.registry 2>/dev/null |
    sed -n 's/.*mCallState=\([0-9]\).*/\1/p' | head -n1 | tr -d '\r' || true)"
  mode="$(adb -s "$ANDROID_SERIAL" shell dumpsys audio 2>/dev/null |
    sed -n 's/.*Actual mode = \(MODE_[A-Z_]*\).*/\1/p' | head -n1 | tr -d '\r' || true)"
  [[ "$state" == 0 && "$mode" == MODE_NORMAL ]]
}
host_ready() {
  local status
  status="$(adb -s "$ANDROID_SERIAL" shell content call --uri "$uri" --method status 2>/dev/null || true)"
  [[ "$status" == *host_registered=true* ]]
}
phone_idle || { echo NOT_IDLE_REFUSED=true >&2; exit 4; }
host_ready || { echo HOST_NOT_READY_REFUSED=true >&2; exit 4; }
archive="$(mktemp -d /tmp/aicalls-s22-ui-XXXXXX)"
postflight() {
  local code="$?"
  trap - EXIT
  rm -rf "$archive"
  if ! phone_idle; then echo POSTFLIGHT_NOT_IDLE=true >&2; exit 20; fi
  if ! host_ready; then
    echo INSTRUMENTATION_DEREGISTERED_HOST=true
    bash scripts/bin/start-s8-gsm-shell.sh --start ||
      { echo HOST_RESTORE_FAILED=true >&2; exit 21; }
    echo HOST_RESTORED=true
  fi
  host_ready || { echo HOST_MISSING_POSTFLIGHT=true >&2; exit 22; }
  phone_idle || { echo CALL_NOT_IDLE_POSTFLIGHT=true >&2; exit 23; }
  [[ "$code" != 0 ]] || echo S22_UI_POSTFLIGHT_PASS=true
  exit "$code"
}
trap postflight EXIT

app_apk=app/build/outputs/apk/debug/app-debug.apk
test_apk=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
[[ -f "$app_apk" && -f "$test_apk" ]] ||
  { echo APK_FILES_MISSING=true >&2; exit 5; }
package_paths="$(adb -s "$ANDROID_SERIAL" shell pm path "$pkg" | tr -d '\r')"
[[ "$(printf '%s\n' "$package_paths" | grep -c '^package:')" == 1 ]] ||
  { echo AMBIGUOUS_PACKAGE=true >&2; exit 5; }
remote_path="$(printf '%s\n' "$package_paths" | sed -n 's/^package://p')"
adb -s "$ANDROID_SERIAL" pull "$remote_path" "$archive/installed.apk" >/dev/null
installed_sha="$(shasum -a 256 "$archive/installed.apk" | awk '{print $1}')"
candidate_sha="$(shasum -a 256 "$app_apk" | awk '{print $1}')"
[[ "$installed_sha" == "$candidate_sha" ]] ||
  { echo APP_APK_SHA_MISMATCH=true >&2; exit 5; }
adb -s "$ANDROID_SERIAL" install -r "$test_apk" >/dev/null
phone_idle || { echo CALL_CHANGED_REFUSED=true >&2; exit 6; }

# Select the two S22-safe Lab tests explicitly. The third test is emulator-only
# and is correctly rejected as a skipped test by instrumentation_result.py.
set +e
output="$(adb -s "$ANDROID_SERIAL" shell am instrument -r -w \
  -e class 'pl.michalmatu.aicallbridge.AndroidAppBottomNavigationFlowTest,pl.michalmatu.aicallbridge.AndroidUiPolishContractTest,pl.michalmatu.aicallbridge.AndroidLabDialogStorageAcceptanceTest#encryptedCaseAndReportRoundTripAcrossFreshStoreInstances,pl.michalmatu.aicallbridge.AndroidLabDialogStorageAcceptanceTest#labManualAndAdaptiveControlsPresentWithoutStartingModel' \
  "$pkg.test/androidx.test.runner.AndroidJUnitRunner")"
runner_code="$?"
set -e
[[ "$runner_code" == 0 ]] ||
  { printf '%s\n' "$output" | tail -35 >&2; echo INSTRUMENTATION_ERROR=true >&2; exit 7; }
if ! summary="$(printf '%s\n' "$output" |
  python3 scripts/aicall_tools/diagnostics/instrumentation_result.py --expected-count 9)"; then
  printf '%s\n' "$output" | tail -35 >&2
  exit 8
fi
echo "$summary"
echo S22_SAFE_UI_LAB_ACCEPTANCE_PASS=true
