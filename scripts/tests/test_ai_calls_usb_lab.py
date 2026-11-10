"""USB/ADB Lab control: private stdin, one-shot nonce and exact-action contracts."""
import json
import os
import shlex
from pathlib import Path
import sys
import tempfile
import unittest
import uuid
from unittest.mock import patch
import subprocess

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "aicall_tools" / "diagnostics"))
import ai_calls_usb_lab as channel  # noqa: E402


class FakeDevice:
    adb = "adb"
    endpoint = channel.SERIAL

    def __init__(self, private_json: dict | None = None):
        self.broadcast_args = []
        self.private_json = private_json

    def call(self, *args, timeout=30):
        if args[:3] == ("shell", "am", "broadcast"):
            self.broadcast_args = list(args)
            return subprocess.CompletedProcess(args, 0, "Broadcast completed: result=0\n", "")
        if args[:4] == ("shell", "run-as", channel.PACKAGE, "cat"):
            if self.private_json is not None:
                return subprocess.CompletedProcess(
                    args, 0, json.dumps(self.private_json), "",
                )
        return subprocess.CompletedProcess(args, 1, "", "unavailable")


class UsbLabControlTests(unittest.TestCase):
    def test_rejects_any_call_placing_or_arbitrary_private_action(self):
        for forbidden in ("place_call", "end_random_call", "grant_create",
                          "change_sim", "read_vault", "raw_pref_write"):
            self.assertNotIn(forbidden, channel.ACTIONS)
            with self.assertRaises(channel.DiagnosticError):
                channel.device_request(FakeDevice(), forbidden)
        self.assertIn("call_takeover", channel.INTERVENTIONS)
        self.assertIn("call_stop_owned", channel.INTERVENTIONS)
        self.assertIn("call_resume", channel.INTERVENTIONS)

    def test_user_message_is_stdin_only_and_receiver_sees_only_nonce(self):
        with tempfile.TemporaryDirectory() as folder:
            nonce = uuid.UUID("cfcfeeee-1111-2222-3333-444444444444")
            received = {}
            fake = FakeDevice({
                "protocol": 1, "nonce": nonce.hex,
                "action": "lab_send", "ok": True,
                "code": "lab_turn_queued", "data": {},
            })

            def capture(argv, input, **kwargs):
                received["argv"] = list(argv)
                received["input"] = input
                return subprocess.CompletedProcess(argv, 0, "", "")

            secret = "Moje prywatne zdanie o rozmowie — 123."
            with patch.object(channel, "private_directory", return_value=Path(folder)), \
                 patch.object(channel.uuid, "uuid4", return_value=nonce), \
                 patch.object(channel.subprocess, "run", side_effect=capture):
                result = channel.device_request(fake, "lab_send",
                    session_id="session-test", text=secret)

            self.assertTrue(result["ok"])
            self.assertIn(secret, received["input"])
            self.assertNotIn(secret, " ".join(received["argv"]))
            # ADB joins argv into a remote shell command. The entire script
            # must survive as one argument to the device-side 'sh -c'.
            self.assertEqual(received["argv"][-3:-1], ["sh", "-c"])
            remote_tokens = shlex.split("sh -c " + received["argv"][-1])
            self.assertEqual(remote_tokens[:2], ["sh", "-c"])
            self.assertEqual(len(remote_tokens), 3)
            self.assertIn("cat > files/usb-lab-control/request.json.new", remote_tokens[2])
            self.assertIn("mv files/usb-lab-control/request.json.new", remote_tokens[2])
            self.assertNotIn(secret, " ".join(fake.broadcast_args))
            self.assertIn(nonce.hex, " ".join(fake.broadcast_args))
            self.assertEqual(os.stat(Path(folder) / ".usb-lab-channel.lock").st_mode & 0o777, 0o600)

    def test_rejects_stale_nonce_and_action_in_private_response(self):
        with tempfile.TemporaryDirectory() as folder:
            nonce = uuid.UUID("ccccdddd-1111-2222-3333-444444444444")
            fake = FakeDevice({
                "protocol": 1, "nonce": "f" * 32,
                "action": "lab_send", "ok": True, "code": "stale",
            })
            with patch.object(channel, "private_directory", return_value=Path(folder)), \
                 patch.object(channel.uuid, "uuid4", return_value=nonce), \
                 patch.object(channel.subprocess, "run",
                              return_value=subprocess.CompletedProcess([], 0, "", "")), \
                 patch.object(channel.time, "monotonic", side_effect=[1.0, 11.0]):
                with self.assertRaises(channel.DiagnosticError) as error:
                    channel.device_request(fake, "lab_send", text="test")
            self.assertEqual(str(error.exception), "diagnostic_response_missing")

    def test_private_transcript_saved_with_0600_and_no_console_echo(self):
        transcript = {
            "session_id": "12345678-1234-1234-1234-123456789abc",
            "turns": [{"user": "secret input", "assistant": "secret output"}],
        }
        with tempfile.TemporaryDirectory() as folder:
            fake = FakeDevice(transcript)
            with patch.object(channel, "private_directory", return_value=Path(folder)):
                result = channel.save_private_transcript(fake)
            self.assertTrue(result["ok"])
            self.assertNotIn("secret output", json.dumps(result))
            written = list((Path(folder) / "lab-sessions").glob("*.json"))
            self.assertEqual(len(written), 1)
            self.assertEqual(written[0].stat().st_mode & 0o777, 0o600)
            self.assertIn("secret output", written[0].read_text())

    def test_chat_input_requires_explicit_session_and_stdin(self):
        namespace = type("Namespace", (), {"command": "lab-send", "session_id": None})
        with self.assertRaises(channel.DiagnosticError):
            channel.run_once(FakeDevice(), namespace)



class AppointmentOwnerConsoleTests(unittest.TestCase):
    def test_stage_draft_uses_stdin_only_and_cannot_pregrant_or_dial(self):
        import io
        from types import SimpleNamespace
        draft = {"target": "+48717117400", "goal": "Zapytaj o wolny termin",
                 "from": "2026-11-11 09:00", "until": "2026-12-31 17:00",
                 "terms": "NFZ"}
        device = FakeDevice()
        device.safe_preflight = lambda: None
        with patch.object(channel.sys, "stdin", io.StringIO(json.dumps(draft))), \
             patch.object(channel, "device_request", return_value={
                 "ok": True, "code": "appointment_editor_staged"}) as request:
            result = channel.run_once(device, SimpleNamespace(command="appointment-stage"))
        self.assertTrue(result["ok"])
        self.assertNotIn(draft["target"], json.dumps(result))
        self.assertEqual(request.call_args.args[1], "appointment_stage")
        self.assertEqual(request.call_args.kwargs, draft)
        for forbidden in ("sim", "grant", "disclosureFields", "dial"):
            self.assertNotIn(forbidden, request.call_args.kwargs)

    def test_stage_rejects_extra_authority_without_writing_mailbox(self):
        import io
        from types import SimpleNamespace
        draft = {"target": "+48717117400", "goal": "Zapytaj o wolny termin",
                 "from": "2026-11-11 09:00", "until": "2026-12-31 17:00",
                 "terms": "NFZ", "grant": "forged"}
        device = FakeDevice()
        device.safe_preflight = lambda: None
        with patch.object(channel.sys, "stdin", io.StringIO(json.dumps(draft))), \
             patch.object(channel, "device_request") as request:
            with self.assertRaises(channel.DiagnosticError) as error:
                channel.run_once(device, SimpleNamespace(command="appointment-stage"))
        self.assertEqual(str(error.exception), "appointment_draft_fields_invalid")
        request.assert_not_called()

    def test_open_editor_uses_dump_guarded_activity_and_never_dials(self):
        from types import SimpleNamespace
        class Device:
            def __init__(self):
                self.calls = []
            def safe_preflight(self):
                return None
            def call(self, *args, timeout=30):
                self.calls.append(args)
                output = ("mResumedActivity: ActivityRecord{xyz " +
                          channel.PACKAGE + "/.campaign.AppointmentMvpActivity}")
                if args[:3] != ("shell", "dumpsys", "activity"):
                    output = "Starting: Intent"
                return subprocess.CompletedProcess(args, 0, output, "")
        device = Device()
        result = channel.run_once(device, SimpleNamespace(command="appointment-open"))
        self.assertTrue(result["ok"])
        self.assertFalse(result["call_started"])
        self.assertEqual(device.calls[0], ("shell", "am", "start", "-n",
            channel.ACTIVITY, "--ez", "open_appointment_editor", "true"))
        self.assertEqual(result["code"], "appointment_editor_open_verified")
        self.assertNotIn("placeCall", str(device.calls))

    def test_open_editor_must_not_claim_success_while_vault_remains_foreground(self):
        from types import SimpleNamespace
        class Device:
            def safe_preflight(self): return None
            def call(self, *args, timeout=30):
                return subprocess.CompletedProcess(
                    args, 0, "mResumedActivity: .identity.IdentityVaultManagerActivity", "")
        with patch.object(channel.time, "sleep"), \
             patch.object(channel, "_appointment_open_ui_fallback", return_value=False):
            with self.assertRaises(channel.DiagnosticError) as error:
                channel.run_once(Device(), SimpleNamespace(command="appointment-open"))
        self.assertEqual(str(error.exception), "appointment_editor_not_foreground")

    def test_open_editor_via_on_screen_fallback_is_verified_without_call(self):
        from types import SimpleNamespace
        class Device:
            def safe_preflight(self): return None
            def call(self, *args, timeout=30):
                return subprocess.CompletedProcess(
                    args, 0, "mResumedActivity: .identity.IdentityVaultManagerActivity", "")
        with patch.object(channel.time, "sleep"), \
             patch.object(channel, "_appointment_open_ui_fallback", return_value=True) as ui:
            result = channel.run_once(Device(), SimpleNamespace(command="appointment-open"))
        self.assertEqual(result["code"], "appointment_editor_open_verified_fallback")
        self.assertFalse(result["call_started"])
        ui.assert_called_once()

    def test_owner_shell_dispatches_call_status_to_usb_lab(self):
        path = Path(__file__).resolve().parents[1] / "bin" / "aicalls"
        source = path.read_text(encoding="utf-8")
        self.assertIn("appointment-stage|appointment-open|call-status)", source)
        self.assertNotIn("place-call", source)

if __name__ == "__main__":
    unittest.main()
