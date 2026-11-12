"""Sprint4 UX contracts: live offline Skill Lab and source-only display safety."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge"
TEST = ROOT / "app/src/androidTest/kotlin/pl/michalmatu/aicallbridge"

class Sprint4UxContract(unittest.TestCase):
    def read(self, path):
        return (SRC / path).read_text(encoding="utf-8")

    def test_lab_renders_pending_and_parsed_completion_per_turn(self):
        lab = self.read("conversation/ConversationSkillLabActivity.kt")
        for token in (
            "showTurnPending(", "showTurnCompleted(", "SkillLabRunner(",
            'tag = "lab-turn-timeline"', 'tag = "lab-live-progress"',
            'tag = "lab-current-turn"', 'tag = "lab-conversation-card"',
            "userReadingOlderTurns",
            'tag = "lab-rating-group"', "followCardIfEnabled",
            "progress.progress = completed", "record.reply.trim()",
            "runButton.isEnabled = next.manualStartAllowed",
            "sendButton.isEnabled = next.phase == SkillLabPhase.INPUT_READY",
            "next.phase == SkillLabPhase.STARTING && timeline.childCount == 0", "conversationMessageView(this, ConversationChatFeed.Line"
        ):
            self.assertIn(token, lab)
        runner = self.read("conversation/SkillLabRunner.kt")
        self.assertIn("ConversationReplyProtocol.decode(raw)", runner)
        self.assertIn("SkillLabPhase.TURN_COMPLETED", runner)
        self.assertIn("SkillLabUiState(", runner)
        self.assertNotIn('results.joinToString("\\n\\n")', lab)
        self.assertNotIn('output.text =', lab)

    def test_lab_no_external_effects_and_cancel_kept(self):
        lab = self.read("conversation/ConversationSkillLabActivity.kt")
        runner = self.read("conversation/SkillLabRunner.kt")
        factory = self.read("conversation/AndroidSkillLabSessionFactory.kt")
        for token in ("runner.stop()", "override fun onStop()", "override fun onDestroy()"):
            self.assertIn(token, lab)
        for token in ("job.stopped.set(true)", "thread.interrupt()", "backend?.cancel()",
                      "if (!active(job)) break", "TimeUnit.SECONDS.toMillis(120)",
                      "generation++", "revision == version", "backend.close()"):
            self.assertIn(token, runner)
        for forbidden in ("startActivityForResult(", "startService(", "startForegroundService(",
                          "placeCall(", "executeTool(", "dispatchTool("):
            for source in (lab, runner, factory):
                self.assertNotIn(forbidden, source)

    def test_synthetic_device_preview_never_starts_model(self):
        preview = (TEST / "AndroidSkillLabLivePreviewTest.kt").read_text()
        self.assertIn("SkillLabUiState", preview)
        self.assertIn("SkillLabCompletedTurn", preview)
        self.assertIn("lab-live-synthetic.jpg", preview)
        self.assertIn("ui-audit-20261107-sprint6", preview)
        self.assertNotIn("runLab(", preview)
        self.assertNotIn("generate(", preview)

    def test_bilingual_labels_match_and_format_arguments(self):
        files = [ROOT / "app/src/main/res" / path for path in
                 ("values/strings.xml", "values-en/strings.xml")]
        sets = [{e.attrib["name"]: (e.text or "") for e in ET.parse(path).getroot()
                 if e.tag == "string"} for path in files]
        self.assertEqual(set(sets[0]), set(sets[1]))
        for key in ("lab_ui_progress", "lab_ui_decision", "lab_ui_score_decisions",
                    "lab_ui_score_errors", "home_ui_task_heading", "incoming_ui_mode_label"):
            self.assertIn(key, sets[0])
        for name in sets[0]:
            if name.startswith(("lab_ui_", "home_ui_task_", "incoming_ui_mode_label",
                                "incoming_ui_sim_label")):
                import re
                p0 = sorted(re.findall(r"%\d+\$[ds]", sets[0][name]))
                p1 = sorted(re.findall(r"%\d+\$[ds]", sets[1][name]))
                self.assertEqual(p0, p1, name)

    def test_history_compact_and_skills_visual_hierarchy(self):
        history = self.read("conversation/ConversationHistoryActivity.kt")
        skills = self.read("conversation/ConversationSkillsActivity.kt")
        incoming = self.read("incoming/IncomingAssistantSettingsActivity.kt")
        home = self.read("MainActivity.kt")
        for token in ("DateFormat.getTimeInstance", 'text = direction + " · " + time',
                      'tag = "history-delete:"', "UiDesign.secondary"):
            self.assertIn(token, history)
        for token in ("UiDesign.card(this)", "UiActionTone.SECONDARY", 'tag = "receptionist-tone-options"'):
            self.assertIn(token, skills)
        self.assertIn("content.addView(feedback)", incoming)
        self.assertIn('tag = "home-task-start"', home)
        self.assertIn("PhoneTaskWizardActivity::class.java", home)
        self.assertNotIn("grants.save(", home)

if __name__ == "__main__":
    unittest.main()
