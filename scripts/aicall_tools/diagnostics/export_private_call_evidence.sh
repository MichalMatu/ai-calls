#!/usr/bin/env bash
# Owner-controlled private export of app-local audio/transcripts from the exact S22.
# NEVER publish this archive or its contents to GitHub, CI, issue trackers or logcat.
set -euo pipefail
umask 077
SERIAL="${1:-RFCT70L7E8J}"
PACKAGE="pl.michalmatu.aicallbridge"
MODEL="$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
PHYSICAL_SERIAL="$(adb -s "$SERIAL" shell getprop ro.serialno | tr -d '\r')"
[[ "$MODEL" == "SM-S906B" && "$PHYSICAL_SERIAL" == "RFCT70L7E8J" ]] || {
  printf 'Wrong device; no export\n' >&2
  exit 2
}
OUT="${HOME}/Documents/AI-Calls/private-exports"
mkdir -p -m 700 "$OUT"
STAMP="$(date -u '+%Y%m%dT%H%M%SZ')"
ARCHIVE="$OUT/call-evidence-$STAMP.tar.gz"
TMP="$ARCHIVE.part"
trap 'rm -f "$TMP"' EXIT
adb -s "$SERIAL" exec-out run-as "$PACKAGE" sh -c \
  'cd files && tar -czf - call-session-evidence' > "$TMP"
[[ -s "$TMP" ]] || { echo 'No private diagnostics to export' >&2; exit 3; }
tar -tzf "$TMP" >/dev/null
mv "$TMP" "$ARCHIVE"
printf 'Private local export created: %s\n' "$ARCHIVE"
printf 'Contains personal conversation content. Upload to this chat only after reviewing and when sharing is appropriate.\n'
