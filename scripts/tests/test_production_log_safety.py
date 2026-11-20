"""Legacy Shizuku developer probes must not reappear after GSM-only cleanup."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class ProductionLogSafetyTest(unittest.TestCase):
    def test_developer_ui_only_opens_existing_owner_diagnostics(self):
        src = (APP / "MainActivityDeveloperProbes.kt").read_text()
        self.assertIn("CapabilityDiagnosticsActivity::class.java", src)
        self.assertIn("fun runCapabilityProbe()", src)
        self.assertNotIn("Shizuku", src)
        self.assertNotIn("Log.i(TAG", src)

    def test_diagnostic_relay_fails_closed_on_missing_gsm_host(self):
        src = (APP / "DiagnosticProbeActivity.kt").read_text()
        self.assertIn("GsmMediaTransportReadiness.isAvailable(this)", src)
        self.assertIn('finishWithError("gsm_shell_host_unavailable")', src)
        self.assertNotIn("ShizukuUserServiceProbe", src)
        self.assertNotIn('Log.i(TAG, "$logKey:\\n$result")', src)


if __name__ == "__main__":
    unittest.main()
