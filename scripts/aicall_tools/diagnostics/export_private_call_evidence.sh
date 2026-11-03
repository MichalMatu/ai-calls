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
chmod 700 "$OUT"
# A valid USB/debug connection with no session is a normal initial state,
# not a reason to stop the diagnostic/development workflow.
if ! adb -s "$SERIAL" shell run-as "$PACKAGE" pwd >/dev/null; then
  echo 'USB_EXPORT=APP_PRIVATE_ACCESS_UNAVAILABLE' >&2
  exit 4
fi
if ! adb -s "$SERIAL" shell run-as "$PACKAGE" sh -c 'test -d files/call-session-evidence'; then
  printf 'USB_EXPORT=NO_DATA\n'
  printf 'Private export directory ready: %s\n' "$OUT"
  exit 0
fi
STAMP="$(date -u '+%Y%m%dT%H%M%SZ')"
# BSD/macOS mktemp requires Xs at the *end* of the template.
# A hard link creates the final name only if absent, never overwriting evidence.
TMP="$(mktemp "$OUT/.call-evidence-$STAMP.XXXXXXXX")"
SUFFIX="${TMP##*.}"
ARCHIVE="$OUT/call-evidence-$STAMP-$SUFFIX.tar.gz"
trap 'rm -f "$TMP"' EXIT
adb -s "$SERIAL" exec-out run-as "$PACKAGE" sh -c \
  'cd files && tar -czf - call-session-evidence' > "$TMP"
[[ -s "$TMP" ]] || { echo 'USB_EXPORT=EMPTY_ARCHIVE_ERROR' >&2; exit 3; }
tar -tzf "$TMP" >/dev/null
ln "$TMP" "$ARCHIVE"
rm -f "$TMP"
chmod 600 "$ARCHIVE"
printf 'USB_EXPORT=PASS\n'
printf 'Private local export created: %s\n' "$ARCHIVE"
printf 'Contains personal conversation content. Upload to this chat only after reviewing and when sharing is appropriate.\n'
