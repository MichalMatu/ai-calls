"""USB private evidence export contract, using a fake ADB and synthetic data only."""
from __future__ import annotations

import io
import os
import stat
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/aicall_tools/diagnostics/export_private_call_evidence.sh"

FAKE_ADB = r'''#!/usr/bin/env python3
import io
import os
import sys
import tarfile

args = sys.argv[1:]
if len(args) < 4 or args[0] != "-s":
    sys.exit(90)
if args[2:] == ["shell", "getprop", "ro.product.model"]:
    print(os.environ.get("FAKE_MODEL", "SM-S906B"))
elif args[2:] == ["shell", "getprop", "ro.serialno"]:
    print("RFCT70L7E8J")
elif args[2:] == ["shell", "run-as", "pl.michalmatu.aicallbridge", "pwd"]:
    if os.environ.get("FAKE_RUNAS") == "DENY":
        sys.exit(1)
    print("/data/user/0/pl.michalmatu.aicallbridge")
elif args[2:] == [
    "shell", "run-as", "pl.michalmatu.aicallbridge", "ls", "-d",
    "files/call-session-evidence",
]:
    sys.exit(0 if os.environ.get("FAKE_EVIDENCE") == "YES" else 1)
elif args[2:] == [
    "shell", "run-as", "pl.michalmatu.aicallbridge", "sh", "-c",
    "test -d files/call-session-evidence",
]:
    # Physical Samsung ADB can break sh -c argument forwarding even with real files.
    sys.exit(1 if os.environ.get("FAKE_SHELL_C_BROKEN") == "YES"
             else (0 if os.environ.get("FAKE_EVIDENCE") == "YES" else 1))
elif args[2:] == [
    "exec-out", "run-as", "pl.michalmatu.aicallbridge",
    "tar", "-czf", "-", "-C", "files", "call-session-evidence",
]:
    if os.environ.get("FAKE_EVIDENCE") != "YES":
        sys.exit(1)
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz") as archive:
        for name, data in (
            ("call-session-evidence/test/COMPLETE", b"local_complete\\n"),
            ("call-session-evidence/test/timeline.jsonl", b'{"type":"TEST_SYNTHETIC"}\\n'),
        ):
            info = tarfile.TarInfo(name)
            info.size = len(data)
            archive.addfile(info, io.BytesIO(data))
    sys.stdout.buffer.write(buf.getvalue())
else:
    sys.exit(91)
'''


class UsbPrivateExportContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name) / "home"
        self.home.mkdir()
        fake_bin = Path(self.temp.name) / "bin"
        fake_bin.mkdir()
        adb = fake_bin / "adb"
        adb.write_text(FAKE_ADB)
        adb.chmod(0o700)
        self.env = dict(os.environ, HOME=str(self.home),
                        PATH=str(fake_bin) + os.pathsep + os.environ.get("PATH", ""))
        self.out = self.home / "Documents/AI-Calls/private-exports"

    def run_export(self, **overrides: str) -> subprocess.CompletedProcess[str]:
        env = dict(self.env, **overrides)
        return subprocess.run(
            ["bash", str(SCRIPT), "RFCT70L7E8J"],
            env=env,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_connected_but_no_recordings_is_normal_no_data(self) -> None:
        result = self.run_export(FAKE_EVIDENCE="NO")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("USB_EXPORT=NO_DATA", result.stdout)
        self.assertTrue(self.out.is_dir())
        self.assertEqual(0o700, stat.S_IMODE(self.out.stat().st_mode))
        self.assertEqual([], list(self.out.glob("*.tar.gz")))

    def test_real_export_stays_private_valid_and_never_overwrites_same_second(self) -> None:
        for _ in range(2):
            result = self.run_export(FAKE_EVIDENCE="YES")
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("USB_EXPORT=PASS", result.stdout)
            self.assertNotIn("TEST_SYNTHETIC", result.stdout)
            self.assertNotIn("timeline.jsonl", result.stdout)
        archives = list(self.out.glob("call-evidence-*.tar.gz"))
        self.assertEqual(2, len(archives))
        for archive in archives:
            self.assertEqual(0o600, stat.S_IMODE(archive.stat().st_mode))
            with tarfile.open(archive, mode="r:gz") as source:
                self.assertIn("call-session-evidence/test/COMPLETE", source.getnames())
                self.assertIn("call-session-evidence/test/timeline.jsonl", source.getnames())

    def test_real_folder_survives_broken_android_shell_c_quoting(self) -> None:
        # Regression for physical S22 F-041: old check returned false NO_DATA.
        result = self.run_export(FAKE_EVIDENCE="YES", FAKE_SHELL_C_BROKEN="YES")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("USB_EXPORT=PASS", result.stdout)
        self.assertNotIn("USB_EXPORT=NO_DATA", result.stdout)
        self.assertEqual(1, len(list(self.out.glob("*.tar.gz"))))

    def test_wrong_hardware_is_rejected(self) -> None:
        result = self.run_export(FAKE_MODEL="other")
        self.assertEqual(2, result.returncode)
        self.assertFalse(self.out.exists())

    def test_private_app_access_failure_is_not_misreported_as_no_data(self) -> None:
        result = self.run_export(FAKE_RUNAS="DENY", FAKE_EVIDENCE="NO")
        self.assertEqual(4, result.returncode)
        self.assertIn("APP_PRIVATE_ACCESS_UNAVAILABLE", result.stderr)
        self.assertNotIn("USB_EXPORT=NO_DATA", result.stdout)


if __name__ == "__main__":
    unittest.main()
