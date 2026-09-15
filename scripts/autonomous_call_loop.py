#!/usr/bin/env python3
"""Bounded live-call runner for the physical S22+.

The runner is intentionally conservative:
- it refuses to start while another call is active;
- it waits for OFFHOOK and then for real downlink energy;
- it captures only a bounded WAV;
- it always requests hangup in a finally block;
- emergency/short-number blocking is inherited from s22_call_control.

This is Phase 1 developer tooling, not the final product call engine.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from s22_call_control import Adb, PACKAGE_NAME, PROBE_CLASS, normalize_number


DEFAULT_PROBE_MS = 250
DEFAULT_SIGNAL_TIMEOUT_SECONDS = 20.0
DEFAULT_ACTIVE_TIMEOUT_SECONDS = 30.0
DEFAULT_MAX_CALL_SECONDS = 60.0
DEFAULT_MIN_RMS = 20.0
DEFAULT_MIN_PEAK = 200


@dataclass(frozen=True)
class ProbeMetrics:
    samples_read: int
    non_zero_samples: int
    peak: int
    rms: float
    read_errors: int


_METRIC_PATTERNS = {
    "samples_read": re.compile(r"^samples_read=(\d+)$", re.MULTILINE),
    "non_zero_samples": re.compile(r"^non_zero_samples=(\d+)$", re.MULTILINE),
    "peak": re.compile(r"^peak=(\d+)$", re.MULTILINE),
    "rms": re.compile(r"^rms=([0-9]+(?:\.[0-9]+)?)$", re.MULTILINE),
    "read_errors": re.compile(r"^read_errors=(\d+)$", re.MULTILINE),
}


def parse_probe_metrics(output: str) -> ProbeMetrics:
    values: dict[str, str] = {}
    for name, pattern in _METRIC_PATTERNS.items():
        match = pattern.search(output)
        if match is None:
            raise ValueError(f"probe output missing {name}")
        values[name] = match.group(1)
    return ProbeMetrics(
        samples_read=int(values["samples_read"]),
        non_zero_samples=int(values["non_zero_samples"]),
        peak=int(values["peak"]),
        rms=float(values["rms"]),
        read_errors=int(values["read_errors"]),
    )


def audio_signal_present(
    metrics: ProbeMetrics,
    *,
    min_rms: float = DEFAULT_MIN_RMS,
    min_peak: int = DEFAULT_MIN_PEAK,
) -> bool:
    if metrics.read_errors != 0 or metrics.samples_read <= 0:
        return False
    return metrics.rms >= min_rms or metrics.peak >= min_peak


def _installed_apk_path(adb: Adb) -> str:
    output = adb.shell(["pm", "path", PACKAGE_NAME])
    for line in output.splitlines():
        if line.startswith("package:"):
            return line.removeprefix("package:")
    raise RuntimeError(f"{PACKAGE_NAME} is not installed")


def capture_probe_metrics(adb: Adb, duration_ms: int = DEFAULT_PROBE_MS) -> ProbeMetrics:
    if adb.call_state() != 2:
        raise RuntimeError("audio probe requires an active call")
    apk_path = _installed_apk_path(adb)
    command = (
        f"CLASSPATH={apk_path} app_process /system/bin {PROBE_CLASS} "
        f"capture-downlink {duration_ms}"
    )
    output = adb.run(["shell", command]).stdout
    return parse_probe_metrics(output)


def wait_for_active_call(adb: Adb, timeout_seconds: float) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        state = adb.call_state()
        print(f"call_state={state}")
        if state == 2:
            return
        time.sleep(0.5)
    raise TimeoutError("call did not reach OFFHOOK before timeout")


def wait_for_audio_signal(
    adb: Adb,
    *,
    timeout_seconds: float,
    probe_ms: int = DEFAULT_PROBE_MS,
    min_rms: float = DEFAULT_MIN_RMS,
    min_peak: int = DEFAULT_MIN_PEAK,
) -> ProbeMetrics:
    deadline = time.monotonic() + timeout_seconds
    probe_index = 0
    last: ProbeMetrics | None = None
    while time.monotonic() < deadline:
        if adb.call_state() != 2:
            raise RuntimeError("call ended before downlink audio appeared")
        probe_index += 1
        last = capture_probe_metrics(adb, probe_ms)
        print(
            "signal_probe="
            f"{probe_index},rms:{last.rms:.3f},peak:{last.peak},"
            f"non_zero:{last.non_zero_samples},errors:{last.read_errors}"
        )
        if audio_signal_present(last, min_rms=min_rms, min_peak=min_peak):
            return last
        time.sleep(0.15)
    if last is None:
        raise TimeoutError("no downlink probes completed")
    raise TimeoutError(
        f"downlink stayed below signal threshold; last rms={last.rms:.3f}, peak={last.peak}"
    )


def transcribe_wav(wav_path: Path, locale: str) -> str:
    script = Path(__file__).with_name("transcribe_audio.swift")
    result = subprocess.run(
        ["swift", str(script), "--locale", locale, str(wav_path)],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
        timeout=45,
    )
    print(result.stdout, end="" if result.stdout.endswith("\n") else "\n")
    if result.returncode != 0:
        raise RuntimeError(f"transcription failed with exit code {result.returncode}")
    for line in result.stdout.splitlines():
        if line.startswith("transcript="):
            return line.removeprefix("transcript=")
    raise RuntimeError("transcriber returned no transcript line")


def wait_for_idle(adb: Adb, timeout_seconds: float = 8.0) -> bool:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if adb.call_state() == 0:
            return True
        time.sleep(0.25)
    return adb.call_state() == 0


def run_once(
    adb: Adb,
    number: str,
    output_path: Path,
    *,
    capture_ms: int,
    active_timeout_seconds: float,
    signal_timeout_seconds: float,
    max_call_seconds: float,
    transcribe: bool,
    locale: str,
) -> str | None:
    normalize_number(number)
    if adb.call_state() != 0:
        raise RuntimeError("refusing to start: phone is not IDLE")

    call_started_at = time.monotonic()
    dial_requested = False
    try:
        adb.dial(number)
        dial_requested = True
        print("dial_requested=true")
        wait_for_active_call(adb, active_timeout_seconds)

        signal = wait_for_audio_signal(adb, timeout_seconds=signal_timeout_seconds)
        print(f"audio_signal=true rms={signal.rms:.3f} peak={signal.peak}")

        if time.monotonic() - call_started_at >= max_call_seconds:
            raise TimeoutError("max call duration reached before capture")

        probe_output = adb.capture_downlink(capture_ms, output_path)
        print(probe_output, end="" if probe_output.endswith("\n") else "\n")
        print(f"wav_path={output_path}")

        if time.monotonic() - call_started_at >= max_call_seconds:
            raise TimeoutError("max call duration reached after capture")

        if transcribe:
            transcript = transcribe_wav(output_path, locale)
            print(f"transcript_final={transcript}")
            return transcript
        return None
    finally:
        if dial_requested and adb.call_state() != 0:
            try:
                adb.hangup()
                print("hangup_requested=true")
            finally:
                print(f"idle_after_hangup={wait_for_idle(adb)}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("number")
    parser.add_argument("--serial")
    parser.add_argument("--output", type=Path, default=Path("/tmp/aicallbridge-live/capture.wav"))
    parser.add_argument("--capture-ms", type=int, default=3000)
    parser.add_argument("--active-timeout", type=float, default=DEFAULT_ACTIVE_TIMEOUT_SECONDS)
    parser.add_argument("--signal-timeout", type=float, default=DEFAULT_SIGNAL_TIMEOUT_SECONDS)
    parser.add_argument("--max-call-seconds", type=float, default=DEFAULT_MAX_CALL_SECONDS)
    parser.add_argument("--transcribe", action="store_true")
    parser.add_argument("--locale", default="pl-PL")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    adb = Adb(args.serial)
    try:
        run_once(
            adb,
            args.number,
            args.output,
            capture_ms=args.capture_ms,
            active_timeout_seconds=args.active_timeout,
            signal_timeout_seconds=args.signal_timeout,
            max_call_seconds=args.max_call_seconds,
            transcribe=args.transcribe,
            locale=args.locale,
        )
        return 0
    except (ValueError, RuntimeError, TimeoutError, subprocess.CalledProcessError) as error:
        print(f"error={error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
