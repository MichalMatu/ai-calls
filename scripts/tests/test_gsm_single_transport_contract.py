"""Regression guard: only the accepted GSM shell Binder media backend is packaged."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app"
PROD = APP / "src/main"


class SingleGsmTransportContractTest(unittest.TestCase):
    def test_no_privilege_manager_api_or_manifest_entrypoint(self):
        gradle = (APP / "build.gradle.kts").read_text()
        self.assertNotIn("dev.rikka.shizuku", gradle)
        for manifest in (PROD / "AndroidManifest.xml", APP / "src/debug/AndroidManifest.xml"):
            xml = manifest.read_text()
            self.assertNotIn("ShizukuProvider", xml)
            self.assertNotIn("moe.shizuku.manager.permission", xml)
        for root in (PROD / "java", PROD / "kotlin"):
            for file in (*root.rglob("*.java"), *root.rglob("*.kt")):
                self.assertNotIn("import rikka.shizuku", file.read_text(), str(file))

    def test_only_verified_gsm_backend_is_selectable(self):
        session = PROD / "java/pl/michalmatu/aicallbridge/session"
        mode = (session / "CallMediaBackendMode.java").read_text()
        runtime = (session / "CallMediaSessionRuntime.java").read_text()
        protocol = (session / "GsmShellMediaProtocol.java").read_text()
        self.assertIn("GSM_SHELL_ADB", mode)
        self.assertIn("UNAVAILABLE", mode)
        self.assertNotIn("SHIZUKU_LEGACY", mode)
        self.assertIn("GsmShellMediaProtocol.S8_PACKAGE.equals(packageName)", mode)
        self.assertIn("return new GsmShellBinderCallMediaSessionBackend()", runtime)
        self.assertNotIn("ShizukuCallMediaSessionBackend", runtime)
        self.assertIn('S8_PACKAGE = "pl.michalmatu.aicallbridge.s8lab"', protocol)


if __name__ == "__main__":
    unittest.main()
