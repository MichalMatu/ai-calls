"""Developer probes must keep private report content out of shared logs."""
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

    def test_reports_with_call_or_model_text_are_not_logged(self):
        probe = (APP / "DiagnosticProbeActivity.kt").read_text()
        for name in (
            "openai_text_speech_pipeline_probe", "local_phone_llm_speech_pipeline_probe",
            "generic_phone_task_acceptance_probe", "local_mac_text_backend_probe",
            "local_speech_text_pipeline_probe", "local_speech_production_probe",
            "local_phone_llm_live_call_probe",
        ):
            with self.subTest(name=name):
                self.assertNotIn(f'{name}_result:', probe)
                self.assertIn(f'Log.i(TAG, "{name}_complete=true")', probe)
        live = (APP / "LocalPhoneLlmLiveCallProbe.kt").read_text()
        self.assertIn("File(context.filesDir, REPORT_FILE).writeText(report)", live)
        router = (APP / "GemmaActionRouterProbeActivity.kt").read_text()
        self.assertNotIn('Log.i(TAG, "gemma_action_router_probe_result:', router)


if __name__ == "__main__":
    unittest.main()
