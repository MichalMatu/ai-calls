#!/usr/bin/env python3
"""Milestone D normal-app-death observer for the physical S22+.

The timing-critical observer runs on the phone so a temporary host ADB disconnect does
not corrupt cleanup latency measurements. This is developer test tooling only.
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

from s22_call_control import Adb, PACKAGE_NAME


REMOTE_SCRIPT_PATH = "/data/local/tmp/aicall_appdeath_observer.sh"
REMOTE_RESULT_PATH = "/data/local/tmp/aicall_appdeath_observer.txt"
DEFAULT_MAX_CLEANUP_MS = 3_000


@dataclass(frozen=True)
class ObserverResult:
    termination_method: str
    termination_rc: int
    boot_before: str
    start_uptime: float
    app_process_gone: bool
    app_cleanup_ms: int
    userservice_process_gone: bool
    helper_cleanup_ms: int
    call_assistant_stop_seen: bool
    media_stop_observed_ms: int
    call_state_after: int
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
            raise ValueError(f"invalid observer line: {line!r}")
        key, value = line.split("=", 1)
        if not key:
            raise ValueError("observer field name is empty")
        if key in fields:
            raise ValueError(f"duplicate observer field: {key}")
        fields[key] = value
    return fields


def _required(fields: dict[str, str], key: str) -> str:
    try:
        return fields[key]
    except KeyError as error:
        raise ValueError(f"missing observer field: {key}") from error


def _parse_int(fields: dict[str, str], key: str) -> int:
    raw = _required(fields, key)
    try:
        return int(raw)
    except ValueError as error:
        raise ValueError(f"observer field {key} is not an integer: {raw!r}") from error


def _parse_float(fields: dict[str, str], key: str) -> float:
    raw = _required(fields, key)
    try:
        return float(raw)
    except ValueError as error:
        raise ValueError(f"observer field {key} is not numeric: {raw!r}") from error


def _parse_bool(fields: dict[str, str], key: str) -> bool:
    raw = _required(fields, key)
    if raw == "1":
        return True
    if raw == "0":
        return False
    raise ValueError(f"observer field {key} must be 0 or 1, got {raw!r}")


def parse_observer_result(text: str) -> ObserverResult:
    fields = _parse_fields(text)
    termination_method = _required(fields, "termination_method")
    if termination_method not in {"kill-pid", "force-stop"}:
        raise ValueError(f"unsupported termination_method: {termination_method!r}")

    boot_before = _required(fields, "boot_before")
    boot_after = _required(fields, "boot_after")
    if not boot_before or not boot_after:
        raise ValueError("boot id must not be empty")

    return ObserverResult(
        termination_method=termination_method,
        termination_rc=_parse_int(fields, "termination_rc"),
        boot_before=boot_before,
        start_uptime=_parse_float(fields, "start_uptime"),
        app_process_gone=_parse_bool(fields, "app_process_gone"),
        app_cleanup_ms=_parse_int(fields, "app_cleanup_ms"),
        userservice_process_gone=_parse_bool(fields, "userservice_process_gone"),
        helper_cleanup_ms=_parse_int(fields, "helper_cleanup_ms"),
        call_assistant_stop_seen=_parse_bool(fields, "call_assistant_stop_seen"),
        media_stop_observed_ms=_parse_int(fields, "media_stop_observed_ms"),
        call_state_after=_parse_int(fields, "call_state_after"),
        shizuku_server_alive=_parse_bool(fields, "shizuku_server_alive"),
        boot_after=boot_after,
        boot_same=_parse_bool(fields, "boot_same"),
        done=_parse_bool(fields, "done"),
    )


def validate_observer_result(
    result: ObserverResult,
    *,
    max_cleanup_ms: int = DEFAULT_MAX_CLEANUP_MS,
) -> list[str]:
    if max_cleanup_ms <= 0:
        raise ValueError("max_cleanup_ms must be > 0")

    errors: list[str] = []
    if not result.done:
        errors.append("done is false")
    if result.termination_rc != 0:
        errors.append("termination_rc is not zero")
    if not result.app_process_gone:
        errors.append("app_process_gone is false")
    if result.app_cleanup_ms < 0:
        errors.append("app_cleanup_ms is invalid")
    elif result.app_cleanup_ms > max_cleanup_ms:
        errors.append(f"app_cleanup_ms exceeds {max_cleanup_ms}")
    if not result.userservice_process_gone:
        errors.append("userservice_process_gone is false")
    if result.helper_cleanup_ms < 0:
        errors.append("helper_cleanup_ms is invalid")
    elif result.helper_cleanup_ms > max_cleanup_ms:
        errors.append(f"helper_cleanup_ms exceeds {max_cleanup_ms}")
    if not result.call_assistant_stop_seen:
        errors.append("call_assistant_stop_seen is false")
    if result.media_stop_observed_ms < 0:
        errors.append("media_stop_observed_ms is invalid")
    elif result.media_stop_observed_ms > max_cleanup_ms:
        errors.append(f"media_stop_observed_ms exceeds {max_cleanup_ms}")
    if result.call_state_after != 2:
        errors.append("call_state_after is not active")
    if not result.shizuku_server_alive:
        errors.append("shizuku_server_alive is false")
    if not result.boot_same:
        errors.append("boot_same is false")
    if result.boot_before != result.boot_after:
        errors.append("boot ids differ")
    return errors


def build_device_observer_script() -> str:
    return r'''#!/system/bin/sh
APP_PID="$1"
HELPER_PID="$2"
MODE="$3"
PKG="$4"
OUT="$5"
TMP="${OUT}.tmp"

rm -f "$OUT" "$TMP"

write_line() {
  printf '%s\n' "$1" >>"$TMP"
}

delta_ms() {
  awk -v s="$1" -v e="$2" 'BEGIN { printf "%.0f", (e-s)*1000 }'
}

TRACK_LINE=$(dumpsys audio 2>/dev/null \
  | grep "uid/pid:2000/$HELPER_PID" \
  | grep "USAGE_CALL_ASSISTANT" \
  | tail -n1)
CALL_ASSISTANT_PIID=$(printf '%s\n' "$TRACK_LINE" \
  | sed -n 's/.*piid:\([0-9][0-9]*\).*/\1/p')
CALL_ASSISTANT_SESSION=$(printf '%s\n' "$TRACK_LINE" \
  | sed -n 's/.*session:\([0-9][0-9]*\).*/\1/p')

BOOT_BEFORE=$(cat /proc/sys/kernel/random/boot_id)
START=$(cut -d' ' -f1 /proc/uptime)
write_line "termination_method=$MODE"
write_line "boot_before=$BOOT_BEFORE"
write_line "start_uptime=$START"
write_line "call_assistant_piid=$CALL_ASSISTANT_PIID"
write_line "call_assistant_session_id=$CALL_ASSISTANT_SESSION"

case "$MODE" in
  "kill-pid")
    kill -9 "$APP_PID" >/dev/null 2>&1
    TERMINATION_RC=$?
    ;;
  "force-stop")
    am force-stop "$PKG" >/dev/null 2>&1
    TERMINATION_RC=$?
    ;;
  *)
    TERMINATION_RC=64
    ;;
esac
write_line "termination_rc=$TERMINATION_RC"

APP_GONE=0
HELPER_GONE=0
APP_GONE_UPTIME=""
HELPER_GONE_UPTIME=""
i=0
while [ "$i" -lt 160 ]; do
  if [ "$APP_GONE" = "0" ] && [ ! -d "/proc/$APP_PID" ]; then
    APP_GONE=1
    APP_GONE_UPTIME=$(cut -d' ' -f1 /proc/uptime)
  fi
  if [ "$HELPER_GONE" = "0" ] && [ ! -d "/proc/$HELPER_PID" ]; then
    HELPER_GONE=1
    HELPER_GONE_UPTIME=$(cut -d' ' -f1 /proc/uptime)
  fi
  if [ "$APP_GONE" = "1" ] && [ "$HELPER_GONE" = "1" ]; then
    break
  fi
  i=$((i + 1))
  sleep 0.025
done

write_line "app_process_gone=$APP_GONE"
if [ -n "$APP_GONE_UPTIME" ]; then
  write_line "app_cleanup_ms=$(delta_ms "$START" "$APP_GONE_UPTIME")"
else
  write_line "app_cleanup_ms=-1"
fi

write_line "userservice_process_gone=$HELPER_GONE"
if [ -n "$HELPER_GONE_UPTIME" ]; then
  write_line "helper_cleanup_ms=$(delta_ms "$START" "$HELPER_GONE_UPTIME")"
else
  write_line "helper_cleanup_ms=-1"
fi

STOP_SEEN=0
STOP_UPTIME=""
if [ -n "$CALL_ASSISTANT_PIID" ]; then
  i=0
  while [ "$i" -lt 60 ]; do
    AUDIO_STATE=$(dumpsys audio 2>/dev/null)
    if printf '%s\n' "$AUDIO_STATE" \
      | grep -F "player piid:$CALL_ASSISTANT_PIID event:stopped" >/dev/null \
      || printf '%s\n' "$AUDIO_STATE" \
      | grep -F "releasing player piid:$CALL_ASSISTANT_PIID" >/dev/null; then
      STOP_SEEN=1
      STOP_UPTIME=$(cut -d' ' -f1 /proc/uptime)
      break
    fi
    i=$((i + 1))
    sleep 0.050
done
fi
write_line "call_assistant_stop_seen=$STOP_SEEN"
if [ -n "$STOP_UPTIME" ]; then
  write_line "media_stop_observed_ms=$(delta_ms "$START" "$STOP_UPTIME")"
else
  write_line "media_stop_observed_ms=-1"
fi

CALL_STATE=$(dumpsys telephony.registry 2>/dev/null | sed -n 's/.*mCallState=\([0-9][0-9]*\).*/\1/p' | head -n1)
if [ -z "$CALL_STATE" ]; then
  CALL_STATE=-1
fi
write_line "call_state_after=$CALL_STATE"

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


def _validate_pid(value: int, name: str) -> int:
    if value <= 0:
        raise ValueError(f"{name} must be > 0")
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


def arm_observer(
    adb: Adb,
    *,
    app_pid: int,
    helper_pid: int,
    termination_method: str,
) -> None:
    _validate_pid(app_pid, "app_pid")
    _validate_pid(helper_pid, "helper_pid")
    if termination_method not in {"kill-pid", "force-stop"}:
        raise ValueError(f"unsupported termination method: {termination_method}")

    install_observer(adb)
    adb.shell(["rm", "-f", REMOTE_RESULT_PATH, REMOTE_RESULT_PATH + ".tmp"], check=False)
    command = " ".join(
        [
            "sh",
            shlex.quote(REMOTE_SCRIPT_PATH),
            str(app_pid),
            str(helper_pid),
            shlex.quote(termination_method),
            shlex.quote(PACKAGE_NAME),
            shlex.quote(REMOTE_RESULT_PATH),
            ">/dev/null",
            "2>&1",
            "</dev/null",
            "&",
        ]
    )
    adb.run(["shell", command])


def collect_observer(adb: Adb, *, max_cleanup_ms: int) -> tuple[str, ObserverResult, list[str]]:
    raw = adb.shell(["cat", REMOTE_RESULT_PATH])
    result = parse_observer_result(raw)
    errors = validate_observer_result(result, max_cleanup_ms=max_cleanup_ms)
    return raw, result, errors


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ADB_SERIAL"))
    sub = parser.add_subparsers(dest="command", required=True)

    arm = sub.add_parser("arm", help="install and launch the phone-side observer")
    arm.add_argument("--app-pid", required=True, type=int)
    arm.add_argument("--helper-pid", required=True, type=int)
    arm.add_argument(
        "--termination-method",
        choices=("kill-pid", "force-stop"),
        default="kill-pid",
    )

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
            arm_observer(
                adb,
                app_pid=args.app_pid,
                helper_pid=args.helper_pid,
                termination_method=args.termination_method,
            )
            print(f"serial={adb.serial}")
            print(f"termination_method={args.termination_method}")
            print(f"remote_result={REMOTE_RESULT_PATH}")
            print("observer_armed=true")
            return 0

        if args.command == "collect":
            raw, _result, errors = collect_observer(adb, max_cleanup_ms=args.max_cleanup_ms)
            print(raw, end="" if raw.endswith("\n") else "\n")
            if errors:
                for error in errors:
                    print(f"gate_error={error}", file=sys.stderr)
                print("app_death_gate_ok=false")
                return 2
            print("app_death_gate_ok=true")
            return 0

        raise AssertionError(args.command)
    except (ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"error={error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
