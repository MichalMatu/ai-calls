#!/usr/bin/env bash
set -euo pipefail

# Owner-authorized, one-shot S22+ fallback using Termux's local ADB client.
# NOT autonomous after reboot: Android Wireless debugging must be enabled,
# manually paired, and connected on the phone before this command is run.
# Termux setup (user action): pkg install android-tools
# Pair with "adb pair 127.0.0.1:<pair-port>", then
# "adb connect 127.0.0.1:<debug-port>" from the Wireless debugging settings.
# Run: ANDROID_SERIAL=127.0.0.1:<debug-port> bash s22-termux-gsm-bootstrap.sh --start
# No root, scheduler, Shizuku, calls, permission changes or background service.

[[ "$#" == 1 && "$1" == "--start" ]] || {
  echo "Usage: ANDROID_SERIAL=127.0.0.1:<debug-port> bash s22-termux-gsm-bootstrap.sh --start" >&2
  exit 2
}
SERIAL="$ANDROID_SERIAL"
[[ "$SERIAL" =~ ^127\.0\.0\.1:([0-9]{1,5})$ ]] || {
  echo "A manually authorized local loopback ADB endpoint is required" >&2
  exit 3
}
PORT="$(printf '%s' "$SERIAL" | cut -d: -f2)"
(( 10#$PORT >= 1 && 10#$PORT <= 65535 )) || {
  echo "Invalid local ADB port" >&2
  exit 3
}
command -v adb >/dev/null || {
  echo "ADB unavailable in Termux. Install android-tools through its package manager." >&2
  exit 4
}
[[ "$(adb -s "$SERIAL" get-state 2>/dev/null)" == "device" ]] || {
  echo "Local ADB must be paired and connected before bootstrap" >&2
  exit 5
}
property() { adb -s "$SERIAL" shell getprop "$1" | tr -d '\r'; }
[[ "$(property ro.product.model)" == "SM-S906B" &&
   "$(property ro.build.version.release)" == "16" &&
   "$(property sys.boot_completed)" == "1" &&
   "$(adb -s "$SERIAL" shell id -u | tr -d '\r')" == "2000" ]] || {
  echo "Refusing bootstrap: device identity, boot, or shell UID mismatch" >&2
  exit 6
}
BASE="$(cd -- "$(dirname -- "$0")" && pwd)"
[[ -f "$BASE/start-s8-gsm-shell.sh" ]] || {
  echo "Missing paired, reviewed GSM bootstrap script" >&2
  exit 7
}
ANDROID_SERIAL="$SERIAL" bash "$BASE/start-s8-gsm-shell.sh" --start
