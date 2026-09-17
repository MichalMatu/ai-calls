#!/usr/bin/env python3
"""Milestone D host-side resource telemetry for the physical S22+.

This is developer test tooling only. It samples existing Android process and media
state without extending the production Binder API. JSONL output is suitable for the
20-cycle and 10-minute endurance gates.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, Sequence

from s22_call_control import Adb, PACKAGE_NAME


HELPER_PROCESS_NAME = f"{PACKAGE_NAME}:call_media"
DEFAULT_INTERVAL_SECONDS = 1.0
MAX_WATCH_SECONDS = 900.0
_CALL_ASSISTANT_STATE_RE = re.compile(r"state:(started|stopped)")


@dataclass(frozen=True)
class ProcessMetrics:
    pid: int
    vmrss_kb: int
    fd_count: int
    thread_count: int


@dataclass(frozen=True)
class TelemetrySample:
    device_uptime_s: float
    call_state: int | None
    call_assistant_state: str
    app: ProcessMetrics | None
    helper: ProcessMetrics | None


def _parse_key_values(text: str) -> dict[str, str]:
    fields: dict[str, str] = {}
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if "=" not in line:
            raise ValueError(f"invalid metrics line: {line!r}")
        key, value = line.split("=", 1)
        if not key:
            raise ValueError("empty metrics field name")
        if key in fields:
            raise ValueError(f"duplicate metrics field: {key}")
        fields[key] = value
    return fields


def _parse_non_negative_int(fields: dict[str, str], key: str, *, positive: bool = False) -> int:
    if key not in fields:
        raise ValueError(f"missing metrics field: {key}")
    try:
        value = int(fields[key])
    except ValueError as error:
        raise ValueError(f"metrics field {key} is not an integer: {fields[key]!r}") from error
    minimum = 1 if positive else 0
    if value < minimum:
        comparator = "> 0" if positive else ">= 0"
        raise ValueError(f"metrics field {key} must be {comparator}")
    return value


def parse_process_metrics(text: str) -> ProcessMetrics | None:
    fields = _parse_key_values(text)
    if fields == {"process_missing": "1"}:
        return None
    expected = {"pid", "vmrss_kb", "fd_count", "thread_count"}
    if set(fields) != expected:
        missing = sorted(expected - set(fields))
        extra = sorted(set(fields) - expected)
        raise ValueError(f"invalid process metrics shape: missing={missing} extra={extra}")
    return ProcessMetrics(
        pid=_parse_non_negative_int(fields, "pid", positive=True),
        vmrss_kb=_parse_non_negative_int(fields, "vmrss_kb"),
        fd_count=_parse_non_negative_int(fields, "fd_count"),
        thread_count=_parse_non_negative_int(fields, "thread_count"),
    )


def parse_call_assistant_state(logcat: str, helper_pid: int) -> str:
    if helper_pid <= 0:
        raise ValueError("helper_pid must be > 0")
    marker = f"u/pid:2000/{helper_pid}"
    state = "unknown"
    for line in logcat.splitlines():
        if marker not in line or "USAGE_CALL_ASSISTANT" not in line:
            continue
        match = _CALL_ASSISTANT_STATE_RE.search(line)
        if match is not None:
            state = match.group(1)
    return state


def _metric_summary(values: Iterable[int]) -> dict[str, int]:
    items = list(values)
    if not items:
        raise ValueError("metric summary requires at least one observation")
    return {
        "start": items[0],
        "end": items[-1],
        "peak": max(items),
        "delta": items[-1] - items[0],
    }


def _process_summary(metrics: Iterable[ProcessMetrics | None]) -> dict[str, object] | None:
    observed = [item for item in metrics if item is not None]
    if not observed:
        return None
    return {
        "observed_samples": len(observed),
        "pid_start": observed[0].pid,
        "pid_end": observed[-1].pid,
        "vmrss_kb": _metric_summary(item.vmrss_kb for item in observed),
        "fd_count": _metric_summary(item.fd_count for item in observed),
        "thread_count": _metric_summary(item.thread_count for item in observed),
    }


def summarize_samples(samples: Sequence[TelemetrySample]) -> dict[str, object]:
    if not samples:
        raise ValueError("at least one telemetry sample is required")
    return {
        "sample_count": len(samples),
        "start_uptime_s": samples[0].device_uptime_s,
        "end_uptime_s": samples[-1].device_uptime_s,
        "duration_s": samples[-1].device_uptime_s - samples[0].device_uptime_s,
        "call_state_start": samples[0].call_state,
        "call_state_end": samples[-1].call_state,
        "call_assistant_state_start": samples[0].call_assistant_state,
        "call_assistant_state_end": samples[-1].call_assistant_state,
        "app": _process_summary(sample.app for sample in samples),
        "helper": _process_summary(sample.helper for sample in samples),
    }


def _single_pid(raw: str, label: str) -> int | None:
    values: list[int] = []
    for token in raw.split():
        try:
            value = int(token)
        except ValueError:
            continue
        if value > 0 and value not in values:
            values.append(value)
    if not values:
        return None
    if len(values) != 1:
        raise RuntimeError(f"ambiguous {label} pids: {values}")
    return values[0]


def _find_pid(adb: Adb, process_name: str, label: str) -> int | None:
    output = adb.shell(["pidof", process_name], check=False)
    return _single_pid(output, label)


def _read_process_metrics(adb: Adb, pid: int | None) -> ProcessMetrics | None:
    if pid is None:
        return None
    script = (
        f'PID={pid}; '
        'if [ ! -d "/proc/$PID" ]; then echo process_missing=1; exit 0; fi; '
        'RSS=$(awk \'/^VmRSS:/ {print $2}\' "/proc/$PID/status" 2>/dev/null); '
        'FD=$(ls -1 "/proc/$PID/fd" 2>/dev/null | wc -l | tr -d " "); '
        'TH=$(ls -1 "/proc/$PID/task" 2>/dev/null | wc -l | tr -d " "); '
        'if [ -z "$RSS" ] || [ -z "$FD" ] || [ -z "$TH" ]; then exit 65; fi; '
        'printf "pid=%s\\nvmrss_kb=%s\\nfd_count=%s\\nthread_count=%s\\n" "$PID" "$RSS" "$FD" "$TH"'
    )
    output = adb.shell(["sh", "-c", script])
    return parse_process_metrics(output)


def _device_uptime(adb: Adb) -> float:
    output = adb.shell(["cat", "/proc/uptime"])
    first = output.strip().split(maxsplit=1)
    if not first:
        raise RuntimeError("device uptime output is empty")
    try:
        value = float(first[0])
    except ValueError as error:
        raise RuntimeError(f"invalid device uptime: {first[0]!r}") from error
    if value < 0.0:
        raise RuntimeError("device uptime must be non-negative")
    return value


def sample_device(adb: Adb) -> TelemetrySample:
    app_pid = _find_pid(adb, PACKAGE_NAME, "app")
    helper_pid = _find_pid(adb, HELPER_PROCESS_NAME, "helper")
    app = _read_process_metrics(adb, app_pid)
    helper = _read_process_metrics(adb, helper_pid)
    call_state = adb.call_state()
    call_assistant_state = "unknown"
    if helper_pid is not None:
        logcat = adb.shell(["logcat", "-d", "-v", "brief", "-t", "400"], check=False)
        call_assistant_state = parse_call_assistant_state(logcat, helper_pid)
    return TelemetrySample(
        device_uptime_s=_device_uptime(adb),
        call_state=call_state,
        call_assistant_state=call_assistant_state,
        app=app,
        helper=helper,
    )


def _sample_to_json(sample: TelemetrySample) -> str:
    return json.dumps(asdict(sample), sort_keys=True, separators=(",", ":"))


def _process_from_json(value: object) -> ProcessMetrics | None:
    if value is None:
        return None
    if not isinstance(value, dict):
        raise ValueError("process JSON must be an object or null")
    try:
        return ProcessMetrics(
            pid=int(value["pid"]),
            vmrss_kb=int(value["vmrss_kb"]),
            fd_count=int(value["fd_count"]),
            thread_count=int(value["thread_count"]),
        )
    except (KeyError, TypeError, ValueError) as error:
        raise ValueError("invalid process JSON") from error


def _sample_from_json(line: str) -> TelemetrySample:
    try:
        value = json.loads(line)
    except json.JSONDecodeError as error:
        raise ValueError("invalid telemetry JSONL line") from error
    if not isinstance(value, dict):
        raise ValueError("telemetry JSONL line must be an object")
    try:
        device_uptime_s = float(value["device_uptime_s"])
        raw_call_state = value["call_state"]
        call_state = None if raw_call_state is None else int(raw_call_state)
        call_assistant_state = str(value["call_assistant_state"])
        app = _process_from_json(value["app"])
        helper = _process_from_json(value["helper"])
    except (KeyError, TypeError, ValueError) as error:
        raise ValueError("invalid telemetry sample JSON") from error
    return TelemetrySample(
        device_uptime_s=device_uptime_s,
        call_state=call_state,
        call_assistant_state=call_assistant_state,
        app=app,
        helper=helper,
    )


def load_jsonl(path: Path) -> list[TelemetrySample]:
    samples: list[TelemetrySample] = []
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line:
            continue
        try:
            samples.append(_sample_from_json(line))
        except ValueError as error:
            raise ValueError(f"{path}:{line_number}: {error}") from error
    if not samples:
        raise ValueError(f"no telemetry samples in {path}")
    return samples


def watch(adb: Adb, output: Path, *, duration_seconds: float, interval_seconds: float) -> list[TelemetrySample]:
    if duration_seconds <= 0.0 or duration_seconds > MAX_WATCH_SECONDS:
        raise ValueError(f"duration_seconds must be > 0 and <= {MAX_WATCH_SECONDS}")
    if interval_seconds <= 0.0 or interval_seconds > duration_seconds:
        raise ValueError("interval_seconds must be > 0 and <= duration_seconds")
    output.parent.mkdir(parents=True, exist_ok=True)
    samples: list[TelemetrySample] = []
    deadline = time.monotonic() + duration_seconds
    with output.open("w", encoding="utf-8") as handle:
        while True:
            sample = sample_device(adb)
            samples.append(sample)
            handle.write(_sample_to_json(sample) + "\n")
            handle.flush()
            remaining = deadline - time.monotonic()
            if remaining <= 0.0:
                break
            time.sleep(min(interval_seconds, remaining))
    return samples


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ADB_SERIAL"))
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("sample", help="print one JSON sample")

    watch_parser = sub.add_parser("watch", help="write bounded JSONL samples")
    watch_parser.add_argument("output", type=Path)
    watch_parser.add_argument("--duration-seconds", type=float, required=True)
    watch_parser.add_argument("--interval-seconds", type=float, default=DEFAULT_INTERVAL_SECONDS)

    summarize = sub.add_parser("summarize", help="summarize an existing JSONL file")
    summarize.add_argument("input", type=Path)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.command == "summarize":
            print(json.dumps(summarize_samples(load_jsonl(args.input)), indent=2, sort_keys=True))
            return 0

        adb = Adb(args.serial)
        if args.command == "sample":
            print(_sample_to_json(sample_device(adb)))
            return 0
        if args.command == "watch":
            samples = watch(
                adb,
                args.output,
                duration_seconds=args.duration_seconds,
                interval_seconds=args.interval_seconds,
            )
            print(f"serial={adb.serial}")
            print(f"jsonl_path={args.output}")
            print(json.dumps(summarize_samples(samples), indent=2, sort_keys=True))
            return 0
        raise AssertionError(args.command)
    except (ValueError, RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f"error={error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
