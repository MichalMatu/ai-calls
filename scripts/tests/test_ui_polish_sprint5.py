"""Sprint5 UX contracts; no hardware, Telecom effects, or model invocation."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge"
ANDROID_TEST = ROOT / "app/src/androidTest/kotlin/pl/michalmatu/aicallbridge"


class Sprint5UxContract(unittest.TestCase):
    def source(self, path):
        return (APP / path).read_text(encoding="utf-8")

    def test_bilingual_strings_are_complete_and_unambiguous(self):
        strings = []
        for directory in ["values", "values-en"]:
            elems = [node for node in ET.parse(
                ROOT / "app/src/main/res" / directory / "strings.xml"
            ).getroot() if node.tag == "string"]
            keys = [node.attrib["name"] for node in elems]
            self.assertEqual(len(keys), len(set(keys)), directory)
            strings.append({node.attrib["name"]: node.text or "" for node in elems})
        self.assertEqual(set(strings[0]), set(strings[1]))
        for key in ("lab_ui_mode_quick", "eng_ui_diagnostics_heading",
                    "camp_ui_runtime_heading", "home_ui_provider_label"):
            self.assertIn(key, strings[0])
        for key in strings[0]:
            if key.startswith(("lab_ui_", "camp_ui_", "models_ui_detail_",
                               "eng_ui_primary_", "home_ui_")):
                args = lambda x: sorted(re.findall(r"%\d+\$[ds]", x))
                self.assertEqual(args(strings[0][key]), args(strings[1][key]), key)
        self.assertNotIn("Run device", strings[0]["dev_ui_capability"])
        self.assertNotIn("Run Shizuku", strings[0]["dev_ui_shizuku"])

    def test_receptionist_has_two_tones_and_no_preset_naming(self):
        basic = self.source("conversation/ConversationSkillsActivity.kt")
        advanced = self.source("conversation/ConversationSkillsAdvancedActivity.kt")
        library = self.source("conversation/ConversationSkillLibrary.kt")
        self.assertIn('tag = "receptionist-tone-options"', basic)
        self.assertIn('tag = "receptionist-personal-edit"', basic)
        self.assertIn("library.withPersonalInstructions", basic)
        self.assertIn("library.withReceptionistTone", basic)
        self.assertNotIn("copyActive(", basic)
        self.assertNotIn("skills-behavior-card", basic)
        self.assertNotIn("skills-preset-picker", basic)
        self.assertIn('tag = "skills-preset-picker"', advanced)
        self.assertIn('tag = "skills-history-toggle:"', advanced)
        self.assertIn("PERSONAL_INSTRUCTIONS_ID", library)

    def test_home_models_engines_campaigns_have_clear_hierarchy(self):
        home = self.source("MainActivity.kt")
        for name in ("home_ui_audio_mode_label", "home_ui_provider_label",
                     "home_ui_dialogue_model_label", "home_ui_readiness_label"):
            self.assertIn(name, home)
        engines = self.source("audioengines/AudioEngineSettingsActivity.kt")
        self.assertLess(engines.index('getString(R.string.eng_ui_save)'),
                        engines.index('getString(R.string.eng_ui_check)'))
        self.assertIn("eng_ui_diagnostics_heading", engines)
        models = self.source("textagent/LanguageModelActivity.kt")
        self.assertIn(
            "getString(R.string.models_ui_select), UiActionTone.SECONDARY", models)
        self.assertIn("models_ui_detail_source", models)
        campaign = self.source("campaign/CampaignToolActivity.kt")
        self.assertIn('tag = "campaign-execution-summary"', campaign)
        self.assertIn("updateLastExecutionStatus()", campaign)
        self.assertIn("camp_ui_actions_help", campaign)
        self.assertIn("statusHandler.removeCallbacks(lastExecutionRefresh)", campaign)

    def test_history_delete_only_in_detail_and_tools_clear(self):
        history = self.source("conversation/ConversationHistoryActivity.kt")
        self.assertEqual(history.count('tag = "history-delete:"'), 1)
        pos = history.index('tag = "history-delete:"')
        self.assertGreater(pos, history.index("private fun showSession"))
        self.assertIn("confirmDelete(session.id)", history)
        tools = self.source("tools/ConversationToolsActivity.kt")
        self.assertIn("card.addView(choice", tools)
        self.assertIn("tools_ui_end_call_detail", tools)

    def test_lab_quick_real_model_boundary_and_failure_handling(self):
        lab = self.source("conversation/ConversationSkillLabActivity.kt")
        self.assertIn('tag = "lab-manual-composer"', lab)
        self.assertIn('tag = "lab-case-picker"', lab)
        runner = self.source("conversation/SkillLabRunner.kt")
        factory = self.source("conversation/AndroidSkillLabSessionFactory.kt")
        self.assertIn("val selected = if (quick) corpus.take(1) else corpus", runner)
        self.assertIn("if (total == 0 || selected.any { it.turns.isEmpty() }) return false", runner)
        self.assertIn("corpus: List<SkillLabStory> = SkillLabCorpus.stories", runner)
        self.assertIn("R.string.lab_manual_empty", lab)
        self.assertIn("runner.startManual(", lab)
        self.assertIn("story.turns.take(2)", runner)
        self.assertIn("ConversationReplyProtocol.decode(raw)", runner)
        self.assertIn("ConversationResponseFormat.TOOL_REPLY", factory)
        self.assertIn("setOf(ConversationToolId.END_CALL)", factory)
        self.assertIn("renderFailure(", lab)
        for bad in ("startForegroundService(", "placeCall(", "dispatchTool("):
            self.assertNotIn(bad, lab)
        t = (ANDROID_TEST / "AndroidSkillLabRealOfflineTest.kt").read_text()
        for token in ("AndroidGemma4ModelReadiness.check", "lab-real-two-turns",
                      "lab-real-stopped", 'real_lab_two_turns_not_completed'):
            self.assertIn(token, t)
        self.assertNotIn("startForegroundService(", t)
        self.assertIn("@Ignore(", t)  # old 2-turn scenario is intentionally retired
        synthetic = (ANDROID_TEST / "AndroidSkillLabLivePreviewTest.kt").read_text()
        self.assertIn("lab-timeout-synthetic.jpg", synthetic)


if __name__ == "__main__":
    unittest.main()
