#!/usr/bin/env python3
"""Owner-side, modular USB/ADB console for the existing app-owned DUMP bridge.

No root, no direct SharedPreferences writes, no grant fabrication, no live calls.
Private transcripts/WAV are exported only to the Mac through the existing exporter.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

PACKAGE = "pl.michalmatu.aicallbridge"
MODEL = "SM-S906B"
SERIAL = "RFCT70L7E8J"
ACTIVITY = PACKAGE + "/.DiagnosticProbeActivity"
RESULT = "files/usb-debug-bridge/response.json"
PREFS = {
    "grant": "campaign_authorization_grant_v1",
    "skills": "campaign_gemma_direct_prompt_skills_v1",
    "audio": "audio_engine_profile_v1",
}
BRIDGE_ACTIONS = frozenset({
    "status", "evidence_status", "profile_set", "profile_reset",
    "profile_check", "audio_compat_check", "audio_route_check", "audio_corpus_check",
    "audio_transcript_corpus_check", "audio_staged_corpus_check", "grant_revoke", "evidence_disable",
})
TESTS = {
    "android-stt": (
        "pl.michalmatu.aicallbridge.localspeech.AndroidLocalSpeechMvpContractTest"
        "#polishOnDeviceSttStartupResolvesWithoutSilentHang"
    ),
    "android-tts": (
        "pl.michalmatu.aicallbridge.localspeech.AndroidLocalSpeechMvpContractTest"
        "#polishLocalTtsProducesNonemptyMonoPcm16At16k"
    ),
    "gemma-e2b": (
        "pl.michalmatu.aicallbridge.textagent.AndroidGemmaDirectOnDeviceTest"
        "#fourSyntheticGemmaDirectTurnsReturnNaturalText"
    ),
    "gemma-audio": (
        "pl.michalmatu.aicallbridge.audioengines.AndroidGemmaAudioNativeOffcallTest"
        "#audioBytesNativeEngineHandlesSyntheticPcmWithoutCrash"
    ),
    "usb-panel": (
        "pl.michalmatu.aicallbridge.debug.AndroidUsbDebugPanelTest"
        "#launchesPrivateStatusPanelWithoutCallOrSettingsMutation"
    ),
    "parakeet-pl": (
        "pl.michalmatu.aicallbridge.audioengines.AndroidParakeetPolishRoundTripTest"
        "#recognizesSynthesizedPolishSpeechWithLexicalOverlap"
    ),
    "parakeet-native": (
        "pl.michalmatu.aicallbridge.audioengines.AndroidParakeetNativeOffcallTest"
        "#statefulParakeetPrewarmsAndProcessesSyntheticPcmWithoutNativeFailure"
    ),
    "gemma-audio-pl": (
        "pl.michalmatu.aicallbridge.audioengines.AndroidPolishAudioQualityTest"
        "#gemmaAudioRecognizesPolishSpeech"
    ),
    "parakeet-quality": (
        "pl.michalmatu.aicallbridge.audioengines.AndroidPolishAudioQualityTest"
        "#parakeetRecognizesPolishSpeech"
    ),
    "piper-pl": (
        "pl.michalmatu.aicallbridge.audioengines.AndroidPolishAudioQualityTest"
        "#piperSynthesizesPolishSpeechForParakeet"
    ),
}


class DiagnosticError(RuntimeError):
    pass


def invoke(argv: list[str], timeout: int = 30) -> subprocess.CompletedProcess[str]:
    try:
        # ADB shell commands can drain inherited stdin. The owner console reserves
        # it for a private lab-send message until the mailbox write itself.
        return subprocess.run(argv, stdin=subprocess.DEVNULL,
                              capture_output=True, text=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise DiagnosticError("transport_or_command_unavailable") from exc


class Device:
    def __init__(self, adb: str):
        self.adb = adb
        listed = invoke([adb, "devices", "-l"])
        if listed.returncode:
            raise DiagnosticError("adb_devices_failed")
        endpoints = [
            line.split()[0] for line in listed.stdout.splitlines()[1:]
            if len(line.split()) >= 2 and line.split()[1] == "device"
        ]
        matches = []
        for endpoint in endpoints:
            model = self._call(endpoint, "shell", "getprop", "ro.product.model")
            serial = self._call(endpoint, "shell", "getprop", "ro.serialno")
            if (model.returncode == serial.returncode == 0
                    and model.stdout.strip() == MODEL and serial.stdout.strip() == SERIAL):
                matches.append(endpoint)
        if not endpoints:
            if "unauthorized" in listed.stdout:
                raise DiagnosticError("adb_authorization_required")
            if "offline" in listed.stdout:
                raise DiagnosticError("adb_device_offline")
            raise DiagnosticError("adb_no_connected_device_check_usb")
        if len(matches) != 1:
            raise DiagnosticError("exact_s22_not_uniquely_available")
        self.endpoint = matches[0]

    def _call(self, endpoint: str, *args: str, timeout: int = 30):
        return invoke([self.adb, "-s", endpoint, *args], timeout=timeout)

    def call(self, *args: str, timeout: int = 30):
        return self._call(self.endpoint, *args, timeout=timeout)

    def checked(self, *args: str, timeout: int = 30) -> str:
        result = self.call(*args, timeout=timeout)
        if result.returncode:
            raise DiagnosticError("device_command_failed")
        return result.stdout

    def idle(self) -> bool:
        state = self.checked("shell", "dumpsys", "telephony.registry")
        return set(re.findall(r"mCallState=(\d+)", state)) == {"0"}

    def safe_preflight(self) -> None:
        for index in range(2):
            if not self.idle():
                raise DiagnosticError("phone_not_idle")
            if index == 0:
                time.sleep(2)
        holders = self.checked("shell", "cmd", "role", "get-role-holders",
                               "android.app.role.DIALER")
        if PACKAGE not in holders:
            raise DiagnosticError("dialer_role_mismatch")
        running = self.checked("shell", "dumpsys", "activity", "services", PACKAGE)
        if "AppOwnedCampaignService" in running:
            raise DiagnosticError("campaign_active")
        continuation = self.call("shell", "run-as", PACKAGE, "cat",
                                 "shared_prefs/campaign_continuation_v1.xml")
        if continuation.returncode == 0:
            try:
                root = ET.fromstring(continuation.stdout)
            except ET.ParseError as exc:
                raise DiagnosticError("continuation_state_unknown") from exc
            if any(n.attrib.get("name") == "grant_id"
                   and (n.attrib.get("value") or n.text or "").strip()
                   for n in root.iter()):
                raise DiagnosticError("pending_retry")
        # A missing continuation preference is allowed; other read failures fail closed.
        elif "No such file" not in continuation.stderr:
            raise DiagnosticError("continuation_unreadable")
        if self.call("shell", "run-as", PACKAGE, "pwd").returncode:
            raise DiagnosticError("debug_run_as_unavailable")

    def preference_hashes(self) -> dict[str, str | None]:
        hashes = {}
        for key, name in PREFS.items():
            result = self.call("shell", "run-as", PACKAGE, "cat",
                               f"shared_prefs/{name}.xml")
            if result.returncode == 0:
                hashes[key] = hashlib.sha256(result.stdout.encode()).hexdigest()
            else:
                hashes[key] = None
        return hashes


def bridge_extras(action: str, nonce: str, params: dict[str, str]) -> list[str]:
    if action not in BRIDGE_ACTIONS:
        raise DiagnosticError("unknown_bridge_action")
    if not re.fullmatch(r"[a-zA-Z0-9_-]{16,64}", nonce):
        raise DiagnosticError("invalid_nonce")
    args = ["--es", "usb_debug_action", action, "--es", "usb_debug_nonce", nonce]
    for key, value in params.items():
        if key == "audio_case_id":
            if action != "audio_route_check" or value not in {
                "greeting", "appointment", "correction", "long", "goodbye",
            }:
                raise DiagnosticError("invalid_fixed_audio_case")
        elif key not in {"input", "dialogue", "output", "voice"}:
            raise DiagnosticError("unknown_bridge_parameter")
        args.extend(["--es", key, value])
    return args


def validated_result(data: str, nonce: str, action: str) -> dict | None:
    try:
        response = json.loads(data)
    except (ValueError, TypeError):
        return None
    if not isinstance(response, dict) or response.get("protocol") != 1:
        return None
    if response.get("nonce") != nonce or response.get("action") != action:
        return None
    if not isinstance(response.get("ok"), bool):
        return None
    return response


def run_bridge(device: Device, action: str, params: dict[str, str] | None = None) -> dict:
    nonce = uuid.uuid4().hex
    arguments = bridge_extras(action, nonce, params or {})
    launch = device.call("shell", "am", "start", "-n", ACTIVITY, *arguments, timeout=30)
    if launch.returncode or "Error:" in launch.stdout or "Error:" in launch.stderr:
        raise DiagnosticError("diagnostic_activity_launch_failed")
    deadline = time.monotonic() + (
        (155 if params and "audio_case_id" in params else 500)
        if action == "audio_route_check" else
        390 if action in {"audio_corpus_check", "audio_transcript_corpus_check",
                          "audio_staged_corpus_check"} else
        205 if action == "audio_compat_check" else
        35 if action in {"profile_set", "profile_check"} else 15)
    while time.monotonic() < deadline:
        response = device.call("shell", "run-as", PACKAGE, "cat", RESULT, timeout=15)
        if response.returncode == 0:
            parsed = validated_result(response.stdout, nonce, action)
            if parsed is not None:
                return parsed
        time.sleep(0.5)
    raise DiagnosticError("diagnostic_response_not_received")


def existing_export(device: Device) -> dict:
    script = Path(__file__).with_name("export_private_call_evidence.sh")
    if not script.is_file():
        raise DiagnosticError("existing_exporter_missing")
    env = os.environ.copy()
    env["PATH"] = str(Path(device.adb).parent) + os.pathsep + env.get("PATH", "")
    try:
        export = subprocess.run(["bash", str(script), SERIAL], env=env,
                                capture_output=True, text=True, timeout=180)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise DiagnosticError("existing_exporter_failed") from exc
    if export.returncode:
        raise DiagnosticError("existing_exporter_failed")
    # Do not print private paths or the archive's content into remote task receipts.
    success = "USB_EXPORT=PASS" in export.stdout
    no_data = "USB_EXPORT=NO_DATA" in export.stdout
    return {"ok": success or no_data, "code": "export_saved_on_mac" if success else
            ("no_recorded_sessions" if no_data else "unknown_export_result")}


def parse_audio_quality_metrics(output: str) -> dict[str, int]:
    fields = {
        "TTS_MS", "AUDIO_MS", "INIT_MS", "INFER_MS", "WORD_ERRORS",
        "REF_WORDS", "CHAR_ERRORS", "REF_CHARS", "FINAL_CALLBACKS",
        "PASSED", "PREWARM_OVERLAPPED_TTS",
    }
    allowed = {f"{engine}_CASE_{case}_{field}"
               for engine in ("GEMMA", "PARAKEET", "PIPER")
               for case in range(1, 4) for field in fields}
    metrics = {}
    for line in output.splitlines():
        line = line.removeprefix("INSTRUMENTATION_STATUS: stream=").strip()
        match = re.fullmatch(r"AUDIO_QA_([A-Z0-9_]+)=([0-9]{1,12})", line)
        if match and match[1] in allowed:
            metrics[match[1]] = int(match[2])
    return metrics


def run_instrumentation(device: Device, test: str) -> dict:
    if test not in TESTS:
        raise DiagnosticError("unknown_offcall_test")
    if "package:" not in device.checked("shell", "cmd", "package", "path", PACKAGE + ".test"):
        raise DiagnosticError("test_apk_not_installed")
    response = device.call(
        "shell", "am", "instrument", "-w", "-e", "class", TESTS[test],
        PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=720,
    )
    output = response.stdout + "\n" + response.stderr
    return {
        "ok": response.returncode == 0 and "OK (1 test)" in output
              and "FAILURES!!!" not in output and "INSTRUMENTATION_FAILED" not in output,
        "code": "instrumentation_pass" if response.returncode == 0
                and "OK (1 test)" in output and "FAILURES!!!" not in output
                else "instrumentation_failed",
        "test": test,
        "audio_quality_metrics": parse_audio_quality_metrics(output),
    }



def recent_error_counts(device: Device) -> dict:
    """Return only technical counts; never print or persist logcat text."""
    logs = device.call("logcat", "-d", "-t", "2500", "-v", "threadtime", timeout=40)
    if logs.returncode:
        raise DiagnosticError("logcat_unavailable")
    lines = logs.stdout.splitlines()
    app = [line for line in lines if "AiCallBridge" in line or "AiCalls" in line]
    return {
        "ok": True,
        "code": "redacted_error_counts",
        "app_error_lines": sum(bool(re.search(r"\sE\s", line)) for line in app),
        "app_warning_lines": sum(bool(re.search(r"\sW\s", line)) for line in app),
        "android_runtime_fatal_lines": sum("FATAL EXCEPTION" in line for line in lines),
        "lines_scanned": len(lines),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=[
        "status", "errors", "evidence-status", "evidence-export", "evidence-disable",
        "profile-check", "profile-set", "profile-reset", "grant-revoke", "audio-route-check", "probe",
    ])
    parser.add_argument("probe_name", nargs="?", choices=list(TESTS))
    parser.add_argument("--input", default="ANDROID_STT")
    parser.add_argument("--dialogue", default="GEMMA_4_E2B")
    parser.add_argument("--output", default="ANDROID_TTS_PL")
    parser.add_argument("--voice", default="auto")
    parser.add_argument("--adb", default=os.environ.get(
        "ADB", str(Path.home() / "Library/Android/sdk/platform-tools/adb")))
    args = parser.parse_args()
    try:
        device = Device(args.adb)
        mutation = args.action in {
            "profile-set", "profile-reset", "grant-revoke", "evidence-disable"
        }
        hardware_test = args.action in {"probe", "evidence-export", "audio-route-check"}
        if mutation or hardware_test:
            device.safe_preflight()
        before = device.preference_hashes() if mutation or hardware_test else None
        if args.action == "errors":
            result = recent_error_counts(device)
        elif args.action == "evidence-export":
            result = existing_export(device)
        elif args.action == "probe":
            if args.probe_name is None:
                raise DiagnosticError("probe_name_required")
            result = run_instrumentation(device, args.probe_name)
        else:
            action = args.action.replace("-", "_")
            params = {}
            if action in {"profile_check", "profile_set"}:
                params = {"input": args.input, "dialogue": args.dialogue,
                          "output": args.output, "voice": args.voice}
            result = run_bridge(device, action, params)
        if before is not None:
            after = device.preference_hashes()
            expected = {"audio"} if args.action in {"profile-set", "profile-reset"} \
                       else ({"grant"} if args.action in {"grant-revoke", "evidence-disable"}
                             else set())
            unexpected = [key for key in PREFS if key not in expected
                          and before[key] != after[key]]
            result["unexpected_preference_change"] = bool(unexpected)
            result["idle_after"] = device.idle()
            if unexpected or not result["idle_after"]:
                result["ok"] = False
                result["code"] = "postcondition_failed"
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0 if result["ok"] else 2
    except DiagnosticError as exc:
        print(json.dumps({"ok": False, "code": str(exc)}, sort_keys=True))
        return 2


if __name__ == "__main__":
    sys.exit(main())
