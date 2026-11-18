#!/usr/bin/env bash
set -euo pipefail

# One-shot, owner-authorized ADB bootstrap for the isolated S8 GSM lab.
# Never installs an APK, places a call, starts a scheduler or asks Shizuku.
# Do NOT run during another user's call or without explicit device approval.
#
# Usage: ANDROID_SERIAL=<verified-s22-serial> bash scripts/bin/start-s8-gsm-shell.sh --start
#
# Losing the ADB shell host after reboot requires another explicit bootstrap.
# The helper itself accepts only the installed lab app UID over LocalSocket;
# disconnect/STOP revokes both privileged PCM directions.

[[ "${1:-}" == "--start" && "$#" == 1 ]] || {
  echo 'Usage: ANDROID_SERIAL=<verified-S22-serial> bash scripts/bin/start-s8-gsm-shell.sh --start' >&2
  exit 2
}
[[ -n "${ANDROID_SERIAL:-}" ]] || { echo 'ANDROID_SERIAL is required' >&2; exit 2; }
command -v adb >/dev/null || { echo 'adb is unavailable' >&2; exit 2; }
[[ "$(adb -s "$ANDROID_SERIAL" get-state)" == "device" ]] ||
  { echo 'The exact approved device is not accessible' >&2; exit 2; }

# The command is kept literal for the device shell. No user-controlled
# package, class, path or commands are interpolated into the remote program.
adb -s "$ANDROID_SERIAL" shell '
  APP=pl.michalmatu.aicallbridge.s8lab
  APK="$(pm path "$APP" | sed -n "s/^package://p" | head -n 1)"
  test -n "$APK" && test -r "$APK" || exit 7
  CLASSPATH="$APK" nohup app_process /system/bin \
    --nice-name=aicalls_gsm_binder pl.michalmatu.aicallbridge.session.GsmShellBinderMediaHost \
    </dev/null >/dev/null 2>&1 &
'
echo 'S8 binder shell host launch requested; app verifies UID and Binder registration.'
