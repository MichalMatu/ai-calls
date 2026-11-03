#!/usr/bin/env python3
"""Repeatable Mac Local Agent -> authorized S22 diagnostic/build/deploy cycle.

Never creates a campaign grant, changes the dialer role, globally hangs up a call,
or publishes raw logs, numbers, audio and transcripts to Git task results.
Source repair remains the calling engineering agent's responsibility; a failed
cycle returns a concrete failure for the next fix/build/install/retest iteration.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

SERIAL = "RFCT70L7E8J"
MODEL = "SM-S906B"
PACKAGE = "pl.michalmatu.aicallbridge"
PROBES = (
    ("speech", "run_local_speech_capability_probe", "local_speech_capability_probe_result", 18),
    ("local_ai", "run_local_phone_llm_speech_pipeline_probe", "local_phone_llm_speech_pipeline_probe_result", 38),
    ("shizuku", "run_shizuku_probe", "shizuku_probe_result", 14),
)


def read_report(text: str) -> dict[str, str]:
    """Parse only bounded machine-owned keys, not transcripts or grant details."""
    return dict(re.findall(r"^([a-z_]+)=([A-Z_]+)$", text, re.M))


def call_states(text: str) -> set[str]:
    return set(re.findall(r"mCallState=(\d+)", text))


def authorized_devices(text: str) -> list[str]:
    return [
        match.group(1)
        for line in text.splitlines()
        if (match := re.fullmatch(r"([A-Za-z0-9]+)\s+device", line.strip()))
    ]


def parse_pipeline_metrics(text: str) -> dict[str, object]:
    """The private runtime report is filtered down to numeric counters and stable codes."""
    numeric = {
        "telephony_rx_pcm_bytes", "telephony_tx_pcm_bytes",
        "turns_completed", "gemma_action_decision_count",
        "gemma_skill_decision_count",
    }
    safe: dict[str, object] = {}
    stt_turns = 0
    approved_turns = 0
    for line in text.splitlines():
        match = re.fullmatch(r"([a-z0-9_]+)=([A-Za-z0-9_.-]{1,100})", line.strip())
        if not match:
            continue
        key, value = match.groups()
        if key in numeric and value.isdecimal():
            safe[key] = int(value)
        elif re.fullmatch(r"turn_\\d+_stt_chars", key) and value.isdecimal():
            stt_turns += int(value) > 0
        elif re.fullmatch(r"turn_\\d+_approved_chars", key) and value.isdecimal():
            approved_turns += int(value) > 0
        elif key in ("chat_relay_live_call_success", "probe_complete") and value in ("true", "false"):
            safe[key] = value == "true"
        elif key == "failure_reason" and re.fullmatch(r"[a-z0-9_]{1,90}", value):
            safe["failure_reason_code"] = value
    safe["stt_turns"] = stt_turns
    safe["approved_turns"] = approved_turns
    return safe


def technical_pipeline_evidence(metrics: dict[str, object]) -> bool:
    return (
        metrics.get("probe_complete") is True
        and int(metrics.get("telephony_rx_pcm_bytes", 0)) > 0
        and int(metrics.get("telephony_tx_pcm_bytes", 0)) > 0
        and int(metrics.get("stt_turns", 0)) > 0
        and int(metrics.get("approved_turns", 0)) > 0
        and int(metrics.get("gemma_action_decision_count", 0)) > 0
    )


def classify_runtime_status(xml_document: str) -> str:
    """Only a fixed machine-state vocabulary can escape the Mac's private logs."""
    try:
        xml = ET.fromstring(xml_document)
    except ET.ParseError:
        return "UNAVAILABLE"
    status = next(
        ((element.text or "") for element in xml.iter("string") if element.get("name") == "status"),
        "",
    )
    states = (
        ("Wi-Fi Calling", "WIFI_CALLING_MEDIA_GATE"),
        ("Call activation timeout", "ACTIVATION_TIMEOUT"),
        ("Exact owned cellular call active", "LOCAL_RUNTIME_STARTED"),
        ("Campaign runtime launch failed", "LOCAL_RUNTIME_LAUNCH_FAILED"),
        ("Campaign call ended without accepted success", "CALL_ENDED_INCOMPLETE"),
        ("Live-call readiness failed", "READINESS_FAILED"),
        ("Authorized campaign dial started", "DIAL_STARTED"),
        ("Campaign call setup failed", "DIAL_SETUP_FAILED"),
        ("STOP AI", "STOP_DISPATCHED"),
        ("Autoryzowana kontynuacja", "CONTINUATION_SCHEDULED"),
    )
    phase = next((code for needle, code in states if needle.lower() in status.lower()), "OTHER")
    if phase == "ACTIVATION_TIMEOUT":
        mode = re.search(r"audio_mode=(MODE_IN_CALL|MODE_IN_COMMUNICATION|MODE_NORMAL)", status)
        if mode:
            phase += "_" + mode.group(1)
    return phase


def diagnose_phases(phases: list[str]) -> str:
    """Do not guess an audio root cause from OFFHOOK or build success."""
    if any("WIFI_CALLING_MEDIA_GATE" in phase for phase in phases):
        return "WIFI_CALLING_UNSUPPORTED_AUDIO_ROUTE"
    if any("ACTIVATION_TIMEOUT_MODE_IN_COMMUNICATION" in phase for phase in phases):
        return "CELLULAR_AUDIO_MODE_NOT_IN_CALL"
    if any("ACTIVATION_TIMEOUT" in phase for phase in phases):
        return "CALL_ACTIVATION_NOT_VERIFIED"
    if "LOCAL_RUNTIME_LAUNCH_FAILED" in phases:
        return "LOCAL_AUDIO_RUNTIME_LAUNCH_FAILURE"
    if "READINESS_FAILED" in phases:
        return "TELECOM_OR_MEDIA_READINESS_FAILED"
    if "LOCAL_RUNTIME_STARTED" in phases:
        return "LOCAL_RUNTIME_STARTED_RX_TX_NOT_PROVEN"
    if "DIAL_STARTED" in phases:
        return "DIAL_STARTED_NO_RUNTIME_EVIDENCE"
    return "NO_RUNTIME_PHASE_EVIDENCE"


def cycle_verdict(report: dict[str, object], *, call_requested: bool, probes_skipped: bool) -> str:
    """A completed build or idle phone is never proof of live RX/TX."""
    if report.get("final_idle") is not True:
        return "INCOMPLETE_PHONE_NOT_IDLE"
    probes = report.get("probes", {})
    if not probes_skipped and (
        not isinstance(probes, dict)
        or any(probes.get(name) != "CALLBACK_OBSERVED" for name, *_ in PROBES)
    ):
        return "INCOMPLETE_OFFCALL_PROBES"
    if not call_requested:
        return "PASS_OFFCALL_ONLY"
    if report.get("campaign_readiness") != "READY":
        return "NOT_EXECUTED_NO_VALID_CAMPAIGN_AUTHORITY"
    if report.get("start_command") != "DISPATCHED_TO_INTERNAL_SERVICE":
        return "INCOMPLETE_CAMPAIGN_START"
    if report.get("offhook_observed") is not True:
        return "INCOMPLETE_OFFHOOK_NOT_OBSERVED"
    if (report.get("owned_stop") != "DISPATCHED_TO_INTERNAL_SERVICE"
            or report.get("after_owned_stop_idle") is not True):
        return "INCOMPLETE_EXACT_TEARDOWN"
    # Private WAVs, a synthetic response, or Telecom OFFHOOK alone do not establish
    # a verified audible RX/STT/AI/TTS/TX conversation with the counterparty.
    return "LIVE_AUDIO_ACCEPTANCE_UNVERIFIED"



class LocalCycle:
    def __init__(self, adb: Path, root: Path, private_dir: Path) -> None:
        self.adb, self.root, self.private_dir = adb, root, private_dir
        self.report: dict[str, object] = {"source": self._source_sha(), "probes": {}}

    def _source_sha(self) -> str:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=self.root,
            check=True, capture_output=True, text=True, timeout=15,
        )
        return result.stdout.strip()

    def command(self, args: list[str], *, timeout: int = 45, cwd: Path | None = None) -> str:
        result = subprocess.run(
            args, cwd=cwd or self.root, capture_output=True, text=True, timeout=timeout,
        )
        if result.returncode:
            raise RuntimeError(f"command_failed:{Path(args[0]).name}:rc={result.returncode}")
        if "Permission Denial" in result.stdout + result.stderr:
            raise RuntimeError("android_permission_denial")
        return result.stdout

    def adb_run(self, *args: str, timeout: int = 45) -> str:
        return self.command([str(self.adb), "-s", SERIAL, *args], timeout=timeout)

    def assert_device(self, require_idle: bool = True) -> None:
        connected = authorized_devices(self.command([str(self.adb), "devices"]))
        if connected != [SERIAL]:
            raise RuntimeError("not_exactly_one_authorized_s22")
        if self.adb_run("shell", "getprop", "ro.product.model").strip() != MODEL:
            raise RuntimeError("unexpected_android_model")
        states = call_states(self.adb_run("shell", "dumpsys", "telephony.registry"))
        if require_idle and states != {"0"}:
            raise RuntimeError("phone_not_idle")
        self.report["adb"] = "EXACT_S22"

    def build_and_install(self) -> None:
        self.assert_device()
        services = self.adb_run("shell", "dumpsys", "activity", "services", PACKAGE)
        if "ServiceRecord" in services and "AppOwnedCampaignService" in services:
            raise RuntimeError("active_campaign_do_not_replace_apk")
        self.command([sys.executable, "scripts/aicall_tools/diagnostics/audit_product_friction.py"])
        self.command(
            ["./gradlew", "--no-daemon", ":app:testDebugUnitTest", "--tests",
             "pl.michalmatu.aicallbridge.campaign.CampaignAgentLaunchPolicyTest",
             ":app:assembleDebug", ":app:lintDebug"],
            timeout=3600,
        )
        apk = self.root / "app/build/outputs/apk/debug/app-debug.apk"
        if not apk.is_file():
            raise RuntimeError("debug_apk_missing")
        self.assert_device()
        self.adb_run("install", "-r", str(apk), timeout=240)
        self.report["build_install"] = "PASS"

    def app_command(self, extra: str) -> dict[str, str]:
        self.adb_run("shell", "run-as", PACKAGE, "rm", "-f", "files/campaign-agent-report.txt")
        self.adb_run(
            "shell", "am", "start", "-n", f"{PACKAGE}/.DiagnosticProbeActivity",
            "--ez", extra, "true",
        )
        time.sleep(1.5)
        raw = self.adb_run("shell", "run-as", PACKAGE, "cat", "files/campaign-agent-report.txt")
        return read_report(raw)

    def offcall_probes(self) -> None:
        for name, extra, marker, delay in PROBES:
            self.assert_device()
            before = self.adb_run("logcat", "-d", "-t", "3000", "-s", "AiCallBridge:I")
            self.adb_run(
                "shell", "am", "start", "-n", f"{PACKAGE}/.DiagnosticProbeActivity",
                "--ez", extra, "true",
            )
            time.sleep(delay)
            after = self.adb_run("logcat", "-d", "-t", "3000", "-s", "AiCallBridge:I")
            recent = after[len(before):] if after.startswith(before) else after
            # A callback is evidence of execution, not proof of a live call or RX/TX.
            self.report["probes"][name] = (
                "CALLBACK_OBSERVED" if marker in recent else "NO_RESULT_MARKER"
            )

    def runtime_phase(self) -> str:
        try:
            report = self.adb_run(
                "shell", "run-as", PACKAGE, "cat",
                "shared_prefs/campaign_runtime_status_v1.xml",
                timeout=20,
            )
            return classify_runtime_status(report)
        except (RuntimeError, subprocess.TimeoutExpired):
            return "UNAVAILABLE"

    def phone_state(self) -> set[str]:
        return call_states(self.adb_run("shell", "dumpsys", "telephony.registry"))

    def authorized_call(self, observe_seconds: int) -> None:
        preflight = self.app_command("run_campaign_agent_preflight")
        self.report["campaign_readiness"] = preflight.get("campaign_agent_readiness", "UNKNOWN")
        if preflight.get("campaign_agent_readiness") != "READY":
            self.report["call"] = "NOT_STARTED_NO_VALID_EXACT_GRANT_OR_ROLE"
            return
        self.assert_device()
        started = self.app_command("run_campaign_agent_start")
        status = started.get("campaign_agent_start", "UNKNOWN")
        self.report["start_command"] = status
        if status != "DISPATCHED_TO_INTERNAL_SERVICE":
            self.report["call"] = "NOT_DISPATCHED"
            return
        seen: list[str] = []
        phases: list[str] = []
        offhook = False
        try:
            until = time.monotonic() + observe_seconds
            while time.monotonic() < until:
                states = self.phone_state()
                state = ",".join(sorted(states)) or "UNKNOWN"
                if not seen or seen[-1] != state:
                    seen.append(state)
                phase = self.runtime_phase()
                if not phases or phases[-1] != phase:
                    phases.append(phase)
                offhook = offhook or "2" in states
                if offhook and states == {"0"}:
                    break
                time.sleep(3)
        finally:
            # Internal service uses the exact owned Call object. Never issue global endCall.
            stopped = self.app_command("run_campaign_agent_stop")
            self.report["owned_stop"] = stopped.get("campaign_agent_stop", "UNKNOWN")
            self.report["runtime_phases"] = phases
            self.report["diagnosis"] = diagnose_phases(phases)
        for _ in range(15):
            if self.phone_state() == {"0"}:
                break
            time.sleep(2)
        self.report["call_states"] = seen
        self.report["offhook_observed"] = offhook
        self.report["after_owned_stop_idle"] = self.phone_state() == {"0"}
        self.report["live_rx_tx"] = "UNPROVEN_UNLESS_PRIVATE_SESSION_EVIDENCE_EXISTS"

    def export_private(self) -> None:
        if self.phone_state() != {"0"}:
            self.report["usb"] = "NOT_EXPORTED_DURING_CALL"
            return
        script = self.root / "scripts/aicall_tools/diagnostics/export_private_call_evidence.sh"
        env = os.environ.copy()
        env["PATH"] = str(self.adb.parent) + os.pathsep + env.get("PATH", "")
        result = subprocess.run(
            ["bash", str(script)], cwd=self.root, env=env,
            capture_output=True, text=True, timeout=180,
        )
        match = re.search(r"USB_EXPORT=(PASS|NO_DATA|NOTHING_TO_COPY)", result.stdout)
        self.report["usb"] = match.group(1) if match else f"ERROR_RC_{result.returncode}"

    def save(self) -> None:
        self.private_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(self.private_dir, 0o700)
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        import tempfile
        fd, name = tempfile.mkstemp(prefix=f"s22-autoloop-{timestamp}-", suffix=".json", dir=self.private_dir)
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(self.report, stream, indent=2, sort_keys=True)
            stream.write("\n")
        os.chmod(name, 0o600)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, default=Path.home() / "Library/Android/sdk/platform-tools/adb")
    parser.add_argument("--reuse-installed", action="store_true", help="Skip rebuild/reinstall of unchanged source")
    parser.add_argument("--execute-authorized", action="store_true", help="Use only a currently valid app-owned grant")
    parser.add_argument("--observe-seconds", type=int, default=190)
    parser.add_argument("--skip-offcall-probes", action="store_true", help="Reuse prior matching hardware receipts")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    private = Path.home() / "Documents/AI-Calls/private-exports"
    cycle = LocalCycle(args.adb, root, private)
    try:
        cycle.assert_device()
        if not args.reuse_installed:
            cycle.build_and_install()
        cycle.adb_run("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
        if not args.skip_offcall_probes:
            cycle.offcall_probes()
        if args.execute_authorized:
            cycle.authorized_call(args.observe_seconds)
        else:
            preflight = cycle.app_command("run_campaign_agent_preflight")
            cycle.report["campaign_readiness"] = preflight.get("campaign_agent_readiness", "UNKNOWN")
            cycle.report["call"] = "NOT_REQUESTED"
        cycle.export_private()
        cycle.report["final_idle"] = cycle.phone_state() == {"0"}
        verdict = cycle_verdict(
            cycle.report,
            call_requested=args.execute_authorized,
            probes_skipped=args.skip_offcall_probes,
        )
        cycle.report["verdict"] = verdict
        print("S22_AUTOLOOP=" + verdict)
        for field in ("build_install", "campaign_readiness", "call", "offhook_observed",
                      "owned_stop", "diagnosis", "usb", "final_idle"):
            if field in cycle.report:
                print(field.upper() + "=" + str(cycle.report[field]))
        return 0 if verdict == "PASS_OFFCALL_ONLY" else 2
    except Exception as error:
        cycle.report["error_class"] = type(error).__name__
        cycle.report["failure"] = str(error).splitlines()[0][:180]
        print("S22_AUTOLOOP=FAIL")
        print("FAILURE_CLASS=" + cycle.report["error_class"])
        print("FAILURE_REASON=" + cycle.report["failure"])
        return 1
    finally:
        cycle.save()


if __name__ == "__main__":
    raise SystemExit(main())
