"""Developer UI does not echo arbitrary probe text into global Logcat."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
APP=ROOT/"app/src/main/kotlin/pl/michalmatu/aicallbridge"

class ProductionLogSafetyTest(unittest.TestCase):
    def test_developer_ui_reports_status_without_raw_payload(self):
        src=(APP/"MainActivityDeveloperProbes.kt").read_text()
        self.assertIn("shizuku_probe_completed=true",src)
        self.assertNotIn('shizuku_probe_result:\\n$result',src)
    def test_diagnostic_relay_contract_is_preserved(self):
        src=(APP/"DiagnosticProbeActivity.kt").read_text()
        self.assertIn('Log.i(TAG, "$logKey:\\n$result")',src)

if __name__=="__main__":unittest.main()
