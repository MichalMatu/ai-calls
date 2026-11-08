"""UX08 navigation, Parakeet offline verification and recording-status safety source gate."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class Ux08SourceContract(unittest.TestCase):
    def read(self, relative):
        return (ROOT / relative).read_text()

    def test_verify_cannot_accidentally_start_a_615mb_transfer(self):
        activity = self.read("textagent/LanguageModelActivity.kt")
        service = self.read("audioengines/ParakeetModelTransferService.kt")
        downloader = self.read("audioengines/ParakeetModelDownloader.kt")
        for token in ("ParakeetModelTransferService.VERIFY", "ParakeetModelTransferService.START",
                      "needsVerification"):
            self.assertIn(token, activity)
        self.assertIn("ParakeetModelFile.validPinnedModel(candidate)", service)
        self.assertIn("else if (verifying)", service)
        self.assertIn("else checkNotNull(transfer).download", service)
        self.assertIn('const val VERIFY = "verify_parakeet_model"', service)
        self.assertIn('if (cancelled) return ParakeetModelDownloadResult.Failure("cancelled")', downloader)

    def test_recording_policy_must_not_claim_active_recording(self):
        usb = self.read("debug/UsbDebugPanelActivity.kt")
        bridge = self.read("debug/UsbDebugBridge.kt")
        self.assertIn("ux08_usb_recording_future", usb)
        self.assertIn("ux08_usb_recording_current", usb)
        self.assertIn("ux08_usb_recording_unconfirmed", usb)
        self.assertIn("grant.revokedAtEpochMs == null", bridge)

    def test_configuration_owners_remain_reachable_without_duplicate_links(self):
        agent = self.read("ui/AgentAiHubActivity.kt")
        incoming = self.read("incoming/IncomingAssistantSettingsActivity.kt")
        campaign = self.read("campaign/CampaignToolActivity.kt")
        model = self.read("textagent/LanguageModelActivity.kt")
        for entry in ("ConversationPreparationActivity", "ConversationSkillsActivity",
                      "ScenarioLibraryActivity", "ConversationToolsActivity"):
            self.assertIn(entry, agent)
            self.assertNotIn("startActivity(Intent(this, " + entry + "::class.java))", incoming)
        self.assertIn("AudioEngineSettingsActivity", model)
        self.assertNotIn("campaign-skills-card", campaign)
        self.assertNotIn("campaign-scenario-library-card", campaign)
        for action in ("startButton", "stopSupervisedAudioButton", "revokeButton",
                       "recordEvidenceChoice", "evidenceSection"):
            self.assertIn(action, campaign)


if __name__ == "__main__":
    unittest.main()
