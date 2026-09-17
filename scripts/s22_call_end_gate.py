#!/usr/bin/env python3
"""Milestone D natural-call-end observer for the physical S22+.

The timing-critical observer runs on the phone. It never triggers hangup: the call must end
externally while the protected ShizukuCallEndProbe keeps media and heartbeat active.
"""

from __future__ import annotations

import argparse
import os
import shlex
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from s22_call_control import Adb


REMOTE_SCRIPT_PATH = "/data/local/tmp/aicall_callend_observer.sh"
REMOTE_RESULT_PATH = "/data/local/tmp/aicall_callend_observer.txt"
DEFAULT_MAX_CLEANUP_MS = 3_000


@dataclass(frozen=True)
class CallEndResult:
    boot_before: str
    start_uptime: float
    call_state_before: int
    call_assistant_started_before: bool
    call_end_seen: bool
    call_end_observed_ms: int
    call_state_after: int
    call_assistant_stop_seen: bool
    media_stop_after_call_end_ms: int
    shizuku_server_alive: bool
    boot_after: str
    boot_same: bool
    done: bool


def _parse_fields(text: str) -> dict[str, str]:
    fields: dict[str, str] = {}
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if "=" not in line:
            raise ValueError(f"invalid call-end observer line: {line!r}")
        key, value = line.split("=", 1)
        if not key:
            raise ValueError("call-end observer field name is empty")
        if key in fields:
            raise ValueError(f"duplicate call-end observer field: {key}")
        fields[key] = value
    return fields


def _required(fields: dict[str, str], key: str) -> str:
    try:
        return fields[key]
    except KeyError as error:
        raise ValueError(f"missing call-end observer field: {key}") from error


def _parse_int(fields: dict[str, str], key: str) -> int:
    raw = _required(fields, key)
    try:
        return int(raw)
    except ValueError as error:
        raise ValueError(f"call-end observer field {key} is not an integer: {raw!r}") from error


def _parse_float(fields: dict[str, str], key: str) -> float:
    raw = _required(fields, key)
    try:
        return float(raw)
    except ValueError as error:
        raise ValueError(f"call-end observer field {key} is not numeric: {raw!r}") from error


def _parse_bool(fields: dict[str, str], key: str) -> bool:
    raw = _required(fields, key)
    if raw == "1":
        return True
    if raw == "0":
        return False
    raise ValueError(f"call-end observer field {key} must be 0 or 1, got {raw!r}")


def parse_call_end_result(text: str) -> CallEndResult:
    fields = _parse_fields(text)
    boot_before = _required(fields, "boot_before")
    boot_after = _required(fields, "boot_after")
    if not boot_before or not boot_after:
        raise ValueError("boot id must not be empty")
    return CallEndResult(
        boot_before=boot_before,
        start_uptime=_parse_float(fields, "start_uptime"),
        call_state_before=_parse_int(fields, "call_state_before"),
        call_assistant_started_before=_parse_bool(fields, "call_assistant_started_before"),
        call_end_seen=_parse_bool(fields, "call_end_seen"),
        call_end_observed_ms=_parse_int(fields, "call_end_observed_ms"),
        call_state_after=_parse_int(fields, "call_state_after"),
        call_assistant_stop_seen=_parse_bool(fields, "call_assistant_stop_seen"),
        media_stop_after_call_end_ms=_parse_int(fields, "media_stop_after_call_end_ms"),
        shizuku_server_alive=_parse_bool(fields, "shizuku_server_alive"),
        boot_after=boot_after,
        boot_same=_parse_bool(fields, "boot_same"),
        done=_parse_bool(fields, "done"),
    )


def validate_call_end_result(
    result: CallEndResult,
    *,
    max_cleanup_ms: int = DEFAULT_MAX_CLEANUP_MS,
) -> list[str]:
    if max_cleanup_ms <= 0:
        raise ValueError("max_cleanup_ms must be > 0")

    errors: list[str] = []
    if not result.done:
        errors.append("done is false")
    if result.call_state_before != 2:
        errors.append("call_state_before is not active")
    if not result.call_assistant_started_before:
        errors.append("call_assistant_started_before is false")
    if not result.call_end_seen:
        errors.append("call_end_seen is false")
    if result.call_end_observed_ms < 0:
        errors.append("call_end_observed_ms is invalid")
    if result.call_state_after != 0:
        errors.append("call_state_after is not idle")
    if not result.call_assistant_stop_seen:
        errors.append("call_assistant_stop_seen is false")
    if result.media_stop_after_call_end_ms < 0:
        errors.append("media_stop_after_call_end_ms is invalid")
    elif result.media_stop_after_call_end_ms > max_cleanup_ms:
        errors.append(f"media_stop_after_call_end_ms exceeds {max_cleanup_ms}")
    if not result.shizuku_server_alive:
        errors.append("shizuku_server_alive is false")
    if not result.boot_same:
        errors.append("boot_same is false")
    if result.boot_before != result.boot_after:
        errors.append("boot ids differ")
    return errors


def build_device_observer_script() -> str:
    return r'''#!/system/bin/sh
HELPER_PID="$1"
OUT="$2"
TMP="${OUT}.tmp"

rm -f "$OUT" "$TMP"

write_line() {
  printf '%s\n' "$1" >>"$TMP"
}

delta_ms() {
  awk -v s="$1" -v e="$2" 'BEGIN { printf "%.0f", (e-s)*1000 }'
}

call_state() {
  dumpsys telephony.registry 2>/dev/null | sed -n 's/.*mCallState=\([0-9][0-9]*\).*/\1/p' | head -n1
}

assistant_state() {
  logcat -d -v brief 2>/dev/null \
    | grep "u/pid:2000/$HELPER_PID" \
    | grep "USAGE_CALL_ASSISTANT" \
    | tail -n1 \
    | sed -n 's/.*state:\([a-zA-Z][a-zA-Z]*\).*/\1/p'
}

BOOT_BEFORE=$(cat /proc/sys/kernel/random/boot_id)
START=$(cut -d' ' -f1 /proc/uptime)
STATE_BEFORE=$(call_state)
if [ -z "$STATE_BEFORE" ]; then
  STATE_BEFORE=-1
fi
ASSISTANT_BEFORE=$(assistant_state)
ASSISTANT_STARTED=0
if [ "$ASSISTANT_BEFORE" = "started" ]; then
  ASSISTANT_STARTED=1
fi

write_line "boot_before=$BOOT_BEFORE"
write_line "start_uptime=$START"
write_line "call_state_before=$STATE_BEFORE"
write_line "call_assistant_started_before=$ASSISTANT_STARTED"

CALL_END_SEEN=0
CALL_END_UPTIME=""
i=0
while [ "$i" -lt 7200 ]; do
  CURRENT_STATE=$(call_state)
  if [ "$CURRENT_STATE" = "0" ]; then
    CALL_END_SEEN=1
    CALL_END_UPTIME=$(cut -d' ' -f1 /proc/uptime)
    break
  fi
  i=$((i + 1))
  sleep 0.025
done

write_line "call_end_seen=$CALL_END_SEEN"
if [ -n "$CALL_END_UPTIME" ]; then
  write_line "call_end_observed_ms=$(delta_ms "$START" "$CALL_END_UPTIME")"
else
  write_line "call_end_observed_ms=-1"
fi

STATE_AFTER=$(call_state)
if [ -z "$STATE_AFTER" ]; then
  STATE_AFTER=-1
fi
write_line "call_state_after=$STATE_AFTER"

STOP_SEEN=0
STOP_UPTIME=""
if [ "$CALL_END_SEEN" = "1" ]; then
  i=0
  while [ "$i" -lt 120 ]; do
    ASSISTANT_NOW=$(assistant_state)
    if [ "$ASSISTANT_NOW" = "stopped" ]; then
      STOP_SEEN=1
      STOP_UPTIME=$(cut -d' ' -f1 /proc/uptime)
      break
    fi
    i=$((i + 1))
    sleep 0.025
  done
fi
write_line "call_assistant_stop_seen=$STOP_SEEN"
if [ -n "$STOP_UPTIME" ] && [ -n "$CALL_END_UPTIME" ]; then
  write_line "media_stop_after_call_end_ms=$(delta_ms "$CALL_END_UPTIME" "$STOP_UPTIME")"
else
  write_line "media_stop_after_call_end_ms=-1"
fi

if ps -A -o USER,PID,NAME | grep -E '^shell[[:space:]]+[0-9]+[[:space:]]+shizuku_server$' >/dev/null; then
  write_line "shizuku_server_alive=1"
else
  write_line "shizuku_server_alive=0"
fi

BOOT_AFTER=$(cat /proc/sys/kernel/random/boot_id)
write_line "boot_after=$BOOT_AFTER"
if [ "$BOOT_BEFORE" = "$BOOT_AFTER" ]; then
  write_line "boot_same=1"
else
  write_line "boot_same=0"
fi
write_line "done=1"

mv "$TMP" "$OUT"
'''


def _validate_pid(value: int) -> int:
    if value <= 0:
        raise ValueError("helper_pid must be > 0")
    return value


def install_observer(adb: Adb) -> None:
    local_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile("w", delete=False, encoding="utf-8") as handle:
            handle.write(build_device_observer_script())
            local_path = Path(handle.name)
        adb.run(["push", str(local_path), REMOTE_SCRIPT_PATH])
        adb.shell(["chmod", "700", REMOTE_SCRIPT_PATH])
    finally:
        if local_path is not None:
            local_path.unlink(missing_ok=True)


def arm_observer(adb: Adb, *, helper_pid: int) -> None:
    _validate_pid(helper_pid)
    install_observer(adb)
    adb.shell(["rm", "-f", REMOTE_RESULT_PATH, REMOTE_RESULT_PATH + ".tmp"], check=False)
    command = " ".join(
        [
            "sh",
            shlex.quote(REMOTE_SCRIPT_PATH),
            str(helper_pid),
            shlex.quote(REMOTE_RESULT_PATH),
            ">/dev/null",
            "2>&1",
            "</dev/null",
            "&",
        ]
    )
    adb.run(["shell", command])


def collect_observer(adb: Adb, *, max_cleanup_ms: int) -> tuple[str, CallEndResult, list[str]]:
    raw = adb.shell(["cat", REMOTE_RESULT_PATH])
    result = parse_call_end_result(raw)
    errors = validate_call_end_result(result, max_cleanup_ms=max_cleanup_ms)
    return raw, result, errors


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ADB_SERIAL"))
    sub = parser.add_subparsers(dest="command", required=True)

    arm = sub.add_parser("arm", help="install and launch the phone-side observer")
    arm.add_argument("--helper-pid", required=True, type=int)

    collect = sub.add_parser("collect", help="read and validate a completed observer result")
    collect.add_argument("--max-cleanup-ms", type=int, default=DEFAULT_MAX_CLEANUP_MS)

    sub.add_parser("show-script", help="print the generated device observer script")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.command == "show-script":
        print(build_device_observer_script(), end="")
        return 0

    adb = Adb(args.serial)
    try:
        if args.command == "arm":
            arm_observer(adb, helper_pid=args.helper_pid)
            print(f"serial={adb.serial}")
            print(f"helper_pid={args.helper_pid}")
            print(f"remote_result={REMOTE_RESULT_PATH}")
            print("observer_armed=true")
            return 0

        if args.command == "collect":
            raw, _result, errors = collect_observer(adb, max_cleanup_ms=args.max_cleanup_ms)
            print(raw, end="" if raw.endswith("\n") else "\n")
            if errors:
                for error in errors:
                    print(f"gate_error={error}", file=sys.stderr)
                print("call_end_gate_ok=false")
                return 2
            print("call_end_gate_ok=true")
            return 0

        raise AssertionError(args.command)
    except (ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"error={error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
