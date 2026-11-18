#!/usr/bin/env bash
set -euo pipefail

# Explicit Mac Local Agent deployment route for S8 GSM. An APK update may
# kill the ADB-launched Binder host: readiness requires re-registration.
# Never touches a stable package, Shizuku, active calls or a scheduler.
# Usage: ANDROID_SERIAL=<verified-S22-serial> bash scripts/bin/install-s8-gsm-lab.sh --install

[[ "$#" == 1 && "$1" == "--install" ]] || {
  echo 'Usage: ANDROID_SERIAL=<approved-S22-serial> install-s8-gsm-lab.sh --install' >&2
  exit 2
}
root="$(cd "$(dirname "$0")/../.." && pwd)"
apk="$root/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$apk" ]] || { echo 'S8 debug APK not built; refusing installation' >&2; exit 2; }
command -v adb >/dev/null || { echo 'ADB unavailable' >&2; exit 2; }
[[ "$(adb -s "$ANDROID_SERIAL" get-state)" == device ]] || exit 3
[[ "$(adb -s "$ANDROID_SERIAL" shell getprop ro.product.model | tr -d '\r')" == SM-S906B ]] ||
  { echo 'Device is not approved S22+' >&2; exit 3; }
call_state="$(adb -s "$ANDROID_SERIAL" shell dumpsys telephony.registry |
  sed -n 's/.*mCallState=\([0-9]\).*/\1/p' | head -n1 | tr -d '\r')"
audio_mode="$(adb -s "$ANDROID_SERIAL" shell dumpsys audio |
  sed -n 's/.*Actual mode = \(MODE_[A-Z_]*\).*/\1/p' | head -n1 | tr -d '\r')"
[[ "$call_state" == 0 && "$audio_mode" == MODE_NORMAL ]] || {
  echo 'Cannot install or change GSM host while call status is active/unknown' >&2
  exit 4
}

# The repository debug build has applicationIdSuffix=.s8lab, no arbitrary APK.
adb -s "$ANDROID_SERIAL" install -r "$apk" >/dev/null

ANDROID_SERIAL="$ANDROID_SERIAL" bash "$root/scripts/bin/start-s8-gsm-shell.sh" --start

adb -s "$ANDROID_SERIAL" shell run-as pl.michalmatu.aicallbridge.s8lab \
  rm -f files/s8-gsm-binder-roundtrip.txt
adb -s "$ANDROID_SERIAL" shell am start -n \
  pl.michalmatu.aicallbridge.s8lab/pl.michalmatu.aicallbridge.developerrelay.S8GsmBinderRoundtripProbeActivity >/dev/null
sleep 2
report="$(adb -s "$ANDROID_SERIAL" shell run-as pl.michalmatu.aicallbridge.s8lab \
  cat files/s8-gsm-binder-roundtrip.txt | tr -d '\r')"
printf '%s\n' "$report" | grep -qx 'host_binder_uid_verified=true' || {
  echo 'S8 GSM host identity probe failed' >&2; exit 5;
}
printf '%s\n' "$report" | grep -qx 'probe_complete=true' || {
  echo 'S8 GSM host probe incomplete' >&2; exit 5;
}
echo 'S8_GSM_LAB_INSTALL_AND_HOST_GATE_PASS=true'
echo 'NO_SHIZUKU_REQUIRED=true'
