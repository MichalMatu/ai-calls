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
    """Private report -> numeric counts, bounded action IDs and latency; never transcript."""
    numeric = {
        "telephony_rx_pcm_bytes", "telephony_tx_pcm_bytes",
        "turns_completed", "gemma_action_decision_count",
        "gemma_skill_decision_count",
    }
    safe: dict[str, object] = {}
    stt_turns = 0
    approved_turns = 0
    latencies: list[int] = []
    action_counts: dict[str, int] = {}
    valid_actions = {
        "ASK_REPEAT", "ASK_CLARIFY", "ACKNOWLEDGE_NEUTRAL",
        "STATE_TASK_SUBJECT", "EXPLAIN_SERVICE_SCOPE", "ASK_NEXT_SERVICE_STEP",
        "REQUEST_SERVICE_TRANSFER", "DISCLOSE_AUTHORIZED_FACT",
        "CONFIRM_AUTHORIZED_EFFECT", "TAKE_OVER",
    }
    subject_streak = 0
    max_subject_streak = 0
    for line in text.splitlines():
        match = re.fullmatch(r"([a-z0-9_]+)=([A-Za-z0-9_.-]{1,100})", line.strip())
        if not match:
            continue
        key, value = match.groups()
        if key in numeric and value.isdecimal():
            safe[key] = int(value)
        elif re.fullmatch(r"turn_\d+_stt_chars", key) and value.isdecimal():
            stt_turns += int(value) > 0
        elif re.fullmatch(r"turn_\d+_approved_chars", key) and value.isdecimal():
            approved_turns += int(value) > 0
        elif re.fullmatch(r"turn_\d+_eos_to_first_tx_ms", key) and value.isdecimal():
            latencies.append(int(value))
        elif re.fullmatch(r"turn_\d+_dialogue_action", key) and value in valid_actions:
            action_counts[value] = action_counts.get(value, 0) + 1
            subject_streak = subject_streak + 1 if value == "STATE_TASK_SUBJECT" else 0
            max_subject_streak = max(max_subject_streak, subject_streak)
        elif key in ("chat_relay_live_call_success", "probe_complete") and value in ("true", "false"):
            safe[key] = value == "true"
        elif key == "failure_reason" and re.fullmatch(r"[a-z0-9_]{1,90}", value):
            safe["failure_reason_code"] = value
    safe["stt_turns"] = stt_turns
    safe["approved_turns"] = approved_turns
    if latencies:
        safe["first_eos_to_tx_ms"] = latencies[0]
        safe["max_eos_to_tx_ms"] = max(latencies)
    if action_counts:
        safe["action_counts"] = action_counts
        safe["max_subject_streak"] = max_subject_streak
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
    if report.get("after_autonomous_idle") is not True:
        return "INCOMPLETE_AUTONOMOUS_CALL_TEARDOWN"
    if report.get("dialogue_failure_code") == "backend_supervisor_relay_disabled":
        return "FAIL_LOCAL_ONLY_DIALOGUE_FALLBACK"
    if technical_pipeline_evidence(report.get("pipeline_metrics", {})):
        # Nonzero captured RX, STT, approved speech and accepted TX bytes are
        # actual runtime data-path evidence, not proof of remote audibility or task outcome.
        return "LIVE_TECHNICAL_PIPELINE_VERIFIED_EXTERNAL_RESULT_OPEN"
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
             "--tests", "pl.michalmatu.aicallbridge.GateCHybridDialogueBackendTest",
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

    def collect_pipeline_metrics(self, started_epoch: int | None) -> None:
        """Wait for an asynchronously finalized report, not for a new user action."""
        file_name = "files/chat-relay-live-call-report.txt"
        deadline = time.monotonic() + (30 if started_epoch is not None else 0)
        last_state = "NOT_AVAILABLE"
        while True:
            try:
                mtime = int(
                    self.adb_run("shell", "run-as", PACKAGE, "stat", "-c", "%Y", file_name).strip()
                )
                if started_epoch is not None and mtime < started_epoch - 2:
                    last_state = "PENDING_CURRENT_SESSION_FLUSH"
                else:
                    raw = self.adb_run("shell", "run-as", PACKAGE, "cat", file_name)
                    metrics = parse_pipeline_metrics(raw)
                    if metrics.get("probe_complete") is True:
                        self.report["pipeline_report"] = (
                            "CURRENT_SESSION" if started_epoch is not None
                            else "HISTORICAL_SESSION"
                        )
                        self.report["pipeline_metrics"] = metrics
                        self.report["live_rx_tx"] = (
                            "REAL_PIPELINE_COUNTERS_PRESENT"
                            if technical_pipeline_evidence(metrics)
                            else "INCOMPLETE_PIPELINE_COUNTERS"
                        )
                        if "failure_reason_code" in metrics:
                            self.report["dialogue_failure_code"] = metrics["failure_reason_code"]
                        return
                    last_state = "PENDING_CURRENT_SESSION_FLUSH"
            except (RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
                if "android_permission_denial" in str(error):
                    self.report["pipeline_report"] = "ANDROID_PERMISSION_DENIED"
                    return
                last_state = "NOT_AVAILABLE"
            if time.monotonic() >= deadline:
                self.report["pipeline_report"] = last_state
                return
            time.sleep(2)

    def phone_state(self) -> set[str]:
        return call_states(self.adb_run("shell", "dumpsys", "telephony.registry"))

    def authorized_call(self, observe_seconds: int) -> None:
        preflight = self.app_command("run_campaign_agent_preflight")
        self.report["campaign_readiness"] = preflight.get("campaign_agent_readiness", "UNKNOWN")
        if preflight.get("campaign_agent_readiness") != "READY":
            self.report["call"] = "NOT_STARTED_NO_VALID_EXACT_GRANT_OR_ROLE"
            return
        self.assert_device()
        call_started_epoch = int(time.time())
        started = self.app_command("run_campaign_agent_start")
        status = started.get("campaign_agent_start", "UNKNOWN")
        self.report["start_command"] = status
        if status != "DISPATCHED_TO_INTERNAL_SERVICE":
            self.report["call"] = "NOT_DISPATCHED"
            return
        seen: list[str] = []
        phases: list[str] = []
        offhook = False
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
        # An observation window is NOT a product call limit. The Android app
        # owns its authorized call, retries and exact teardown. Never interrupt
        # a healthy task just because the Mac diagnostic observation has ended.
        final = self.phone_state()
        self.report["call_states"] = seen
        self.report["runtime_phases"] = phases
        self.report["diagnosis"] = diagnose_phases(phases)
        self.report["offhook_observed"] = offhook
        self.report["after_autonomous_idle"] = final == {"0"}
        self.report["owned_stop"] = "NOT_SENT_AUTONOMOUS_CALL_LIFECYCLE"
        if final == {"0"}:
            self.collect_pipeline_metrics(call_started_epoch)
        else:
            self.report["call"] = "AUTONOMOUS_CAMPAIGN_STILL_ACTIVE"
            self.report["pipeline_report"] = "PENDING_CALL_END"

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
    parser.add_argument("--inspect-last-call", action="store_true", help="Read existing private numeric counters without new dial or install")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    private = Path.home() / "Documents/AI-Calls/private-exports"
    cycle = LocalCycle(args.adb, root, private)
    try:
        cycle.assert_device()
        if args.inspect_last_call:
            cycle.collect_pipeline_metrics(None)
            evidence = cycle.report.get("pipeline_metrics", {})
            verdict = ("TECHNICAL_COUNTS_PRESENT_HISTORICAL"
                       if technical_pipeline_evidence(evidence)
                       else "NO_COMPLETE_TECHNICAL_COUNTERS")
            cycle.report["verdict"] = verdict
            print("S22_AUTOLOOP=" + verdict)
            print("LIVE_RX_TX=" + str(cycle.report.get("live_rx_tx", "UNPROVEN")))
            print("FAILURE_REASON_CODE=" + str(cycle.report.get("dialogue_failure_code", "NONE")))
            return 0 if verdict == "TECHNICAL_COUNTS_PRESENT_HISTORICAL" else 2
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
                      "owned_stop", "diagnosis", "live_rx_tx", "dialogue_failure_code",
                      "usb", "final_idle"):
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
