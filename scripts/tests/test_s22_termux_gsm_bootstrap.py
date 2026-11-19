"""No-device tests for the explicit, local-only S22+ Termux ADB bootstrap."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/bin/s22-termux-gsm-bootstrap.sh"


class LocalS22BootstrapTest(unittest.TestCase):
    def invoke(self, serial, *, wrong_model=False, offline=False):
        with tempfile.TemporaryDirectory() as root:
            adb = Path(root) / "adb"
            adb.write_text("""#!/bin/sh
case " $* " in
  *" get-state "*) if [ "$MOCK_OFFLINE" = 1 ]; then echo offline; else echo device; fi;;
  *" getprop ro.product.model "*) if [ "$MOCK_WRONG_MODEL" = 1 ]; then echo UNKNOWN; else echo SM-S906B; fi;;
  *" getprop ro.build.version.release "*) echo 16;;
  *" getprop sys.boot_completed "*) echo 1;;
  *" shell id -u "*) echo 2000;;
  *" dumpsys telephony.registry "*) echo mCallState=0;;
  *" dumpsys audio "*) echo '- Actual mode = MODE_NORMAL';;
  *" content call "*) echo 'Result: Bundle[{host_registered=true}]';;
  *) echo "UNEXPECTED_ADB_CALL" >&2; exit 21;;
esac
""")
            adb.chmod(0o700)
            env = dict(os.environ)
            env.update(PATH=root + os.pathsep + os.environ.get("PATH", ""),
                       ANDROID_SERIAL=serial, MOCK_WRONG_MODEL=str(int(wrong_model)),
                       MOCK_OFFLINE=str(int(offline)))
            return subprocess.run(["bash", str(SCRIPT), "--start"], env=env,
                                  text=True, capture_output=True, timeout=10)

    def test_valid_local_owner_authorized_transport(self):
        result = self.invoke("127.0.0.1:39199")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("S8_GSM_BINDER_HOST_READY=true", result.stdout)

    def test_refuses_non_local_remote_host(self):
        result = self.invoke("192.168.1.99:39199")
        self.assertEqual(3, result.returncode)

    def test_refuses_missing_authorized_connection(self):
        result = self.invoke("127.0.0.1:39199", offline=True)
        self.assertEqual(5, result.returncode)

    def test_refuses_wrong_phone(self):
        result = self.invoke("127.0.0.1:39199", wrong_model=True)
        self.assertEqual(6, result.returncode)

    def test_never_automatically_enables_debugging_or_root(self):
        active = "\n".join(x for x in SCRIPT.read_text().splitlines()
                           if x.strip() and not x.lstrip().startswith("#"))
        for forbidden in ("adb pair ", "adb connect ", "adb reboot ",
                          "am start ", "su -c ", "settings put "):
            self.assertNotIn(forbidden, active)


if __name__ == "__main__":
    unittest.main()
