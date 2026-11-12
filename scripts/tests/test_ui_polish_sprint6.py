"""Sprint 6 UX contracts: safe bulk clear, bubble WAV toggle, lab status, campaign summary."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge"
NATIVE = ROOT / "app/src/androidTest/kotlin/pl/michalmatu/aicallbridge"


class Sprint6UXContract(unittest.TestCase):
    def read(self, path):
        return (SRC / path).read_text(encoding="utf-8")

    def test_bulk_clear_is_completed_only_and_no_root_delete(self):
        archive = self.read("conversation/RecordedConversationHistory.kt")
        for token in ("fun deleteAllCompleted()", "canDelete(candidate.name)",
                      "load(candidate.name).complete", "deleteCompleted(candidate.name)",
                      "ClearCompletedResult(deleted, skipped, failed)"):
            self.assertIn(token, archive)
        self.assertNotIn("root.deleteRecursively()", archive)
        self.assertNotIn("root.toPath().toFile().deleteRecursively()", archive)
        screen = self.read("conversation/ConversationHistoryActivity.kt")
        self.assertIn('tag = "history-delete-all"', screen)
        self.assertIn("confirmDeleteAll(deletable)", screen)
        self.assertIn("history_clear_all_warning", screen)
        self.assertIn("controller.isDeletionRunning", screen)
        self.assertLess(screen.index('tag = "history-delete-all"'),
                        screen.index("private fun confirmDeleteAll("))
        self.assertNotIn("history.deleteAllCompleted()\n", screen.split("private fun confirmDeleteAll(")[0])

    def test_audio_from_bubble_not_full_width_button(self):
        screen = self.read("conversation/ConversationHistoryActivity.kt")
        self.assertIn("session.lines.forEachIndexed", screen)
        self.assertIn('history-audio-bubble:', screen)
        self.assertIn("onAudioTap = tap", screen)
        self.assertIn("audioPlayer.isPlaying(id, fileName)", screen)
        self.assertIn("history_playback_stopped", screen)
        self.assertNotIn("getString(R.string.history_play_fragment)", screen)
        player = self.read("conversation/ArchivedConversationPlayer.kt")
        self.assertIn("fun isPlaying(id: String, fileName: String)", player)
        self.assertIn("playingKey = null", player)
        bubble = self.read("conversation/ConversationMessageView.kt")
        self.assertIn("onAudioTap: (() -> Unit)? = null", bubble)
        self.assertIn("setOnClickListener { onAudioTap() }", bubble)
        self.assertIn("setTextIsSelectable(onAudioTap == null)", bubble)

    def test_native_tests_never_bulk_confirm_on_real_archive(self):
        t = (NATIVE / "conversation/AndroidHistoryBulkClearContractTest.kt").read_text()
        self.assertIn("BUTTON_NEGATIVE", t)
        self.assertNotIn("BUTTON_POSITIVE", t)
        self.assertIn("synthetic_cleanup_failed", t)
        wav = (NATIVE / "conversation/AndroidArchivedChatAudioPlaybackTest.kt").read_text()
        self.assertIn("history-audio-bubble:0", wav)
        self.assertIn("bubble_second_tap_did_not_stop", wav)

    def test_lab_status_and_score_are_honest(self):
        lab = self.read("conversation/ConversationSkillLabActivity.kt")
        for token in ("showStopNotice()", 'tag = "lab-stopped-banner"',
                      "followLatestCardIfEnabled()", "scroll.smoothScrollTo(0, target.coerceAtMost(bottom))",
                      "userReadingOlderTurns",
                      "lab_ui_quality_not_scored"):
            self.assertIn(token, lab)
        core = self.read("conversation/ConversationSkillLab.kt")
        self.assertIn("mayNeedQuestionReview", core)
        self.assertIn("it.expectedQuestion == false", core)
        self.assertNotIn('it.expectedQuestion == false && "?" in it.reply', core)
        self.assertNotIn("(?iuU)", core)
        self.assertIn("char.isLetterOrDigit()", core)

    def test_campaign_hides_targeted_disconnect_code_but_preserves_diagnostics(self):
        presenter = self.read("campaign/CampaignExecutionStatusPresenter.kt")
        for token in ('DISCONNECT_REQUESTED = "targeted_disconnect_requested"',
                      "CampaignExecutionMeaning.REQUESTED", "CampaignExecutionMeaning.CONFIRMED",
                      "CampaignExecutionMeaning.UNVERIFIED"):
            self.assertIn(token, presenter)
        screen = self.read("campaign/AppointmentMvpActivity.kt")
        self.assertIn("store.review(task.grantId)", screen)
        self.assertNotIn("review.text = raw", screen)

    def test_campaign_cancel_is_scoped_to_outgoing(self):
        screen = self.read("campaign/AppointmentMvpActivity.kt")
        service = self.read("campaign/AppOwnedCampaignService.kt")
        self.assertIn("ACTION_CANCEL_OUTGOING_TASK", screen)
        self.assertNotIn("CampaignAgentControl.stopOwnedCampaign(this@CampaignToolActivity)", screen)
        cancel_branch = service.split("ACTION_CANCEL_OUTGOING_TASK -> {", 1)[1].split(
            "ACTION_STOP_SUPERVISED_AUDIO", 1)[0]
        self.assertIn("authorizationStore.revoke", cancel_branch)
        self.assertIn("AppOwnedClirCampaignExecutor.stop", cancel_branch)
        self.assertNotIn("stopIncomingAi(", cancel_branch)
        self.assertNotIn("supervisedAudioSession?.stopAudio", cancel_branch)

    def test_resource_parity_and_format_specifiers(self):
        dictionaries = []
        for directory in ("values", "values-en"):
            d = ROOT / "app/src/main/res" / directory / "strings.xml"
            elements = [e for e in ET.parse(d).getroot() if e.tag == "string"]
            names = [e.attrib["name"] for e in elements]
            self.assertEqual(len(names), len(set(names)), directory)
            dictionaries.append({e.attrib["name"]: e.text or "" for e in elements})
        pl, en = dictionaries
        self.assertEqual(set(pl), set(en))
        names = ["history_clear_all", "history_clear_all_title", "history_clear_all_warning",
                 "history_audio_play_hint", "history_audio_stop_hint",
                 "lab_ui_stop_banner", "lab_ui_quality_not_scored",
                 "campaign_ui_disconnect_requested", "lab_ui_score_errors"]
        for name in names:
            self.assertIn(name, pl)
            fmt = lambda s: sorted(re.findall(r"%\d+\$[ds]", s))
            self.assertEqual(fmt(pl[name]), fmt(en[name]), name)


if __name__ == "__main__":
    unittest.main()
