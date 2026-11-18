"""S8 SIP registrar lab must remain independent of GSM privilege and Shizuku."""
from pathlib import Path
from unittest import TestCase

ROOT = Path(__file__).resolve().parents[2]
LAB = ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge/ui/S8SipAccountLabActivity.kt"


class S8SipLabIndependenceTest(TestCase):
    def test_explicit_sip_register_needs_no_cellular_permission_or_shizuku(self):
        # Ignore imports and descriptive comments; inspect the Activity body.
        source = LAB.read_text(encoding="utf-8").split("class S8SipAccountLabActivity", 1)[1]
        for forbidden in (
            "READ_PHONE_STATE",
            "requestPermissions(",
            "onRequestPermissionsResult(",
            "TelecomManager",
            "AudioManager",
            "Shizuku",
            "isPhoneIdle(",
        ):
            self.assertNotIn(forbidden, source, forbidden)

    def test_manual_opt_in_cancel_and_private_credentials_remain_intact(self):
        source = LAB.read_text(encoding="utf-8")
        for required in (
            "startRegisterOnce()",
            "S8SipsTlsRegisterClient.Attempt()",
            "once.run(config, secret, 4500, true)",
            "password.text.clear()",
            "Arrays.fill(secret, Char.MIN_VALUE)",
            "current.close()",
            "override fun onStop()",
            "worker.shutdownNow()",
        ):
            self.assertIn(required, source, required)
