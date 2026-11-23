"""Minimal UX source contracts: shared style, native selectors and safe compact history."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]/"app/src/main"
K=ROOT/"kotlin/pl/michalmatu/aicallbridge"
D=ROOT/"res/drawable"

class MinimalUiContract(unittest.TestCase):
    def test_tabler_vector_set_and_license(self):
        for name in ("home","history","lab","settings","phone_incoming","phone_outgoing",
                     "phone","trash","chevron_right","chevron_down","chevron_up"):
            source=D/("ic_aicalls_"+name+".xml")
            text=source.read_text()
            self.assertIn("Tabler Icons:",text)
            self.assertEqual("vector",ET.fromstring(text).tag)
        self.assertIn("MIT License",(ROOT/"assets/licenses/tabler-icons-MIT.txt").read_text())

    def test_flat_buttons_and_accessible_compact_history(self):
        styles=(ROOT/"res/values/styles.xml").read_text()
        history=(K/"conversation/ConversationHistoryActivity.kt").read_text()
        controller=(K/"conversation/ConversationHistoryController.kt").read_text()
        archive=(K/"conversation/RecordedConversationHistory.kt").read_text()
        design=(K/"ui/UiDesign.kt").read_text()
        self.assertIn('name="android:stateListAnimator">@null', styles)
        self.assertIn('name="android:elevation">0dp', styles)
        self.assertIn("UiDesign.touchSurface", history)
        for token in ("history-row:","history-open:","history-delete:",
                      "R.drawable.ic_aicalls_phone_incoming","R.drawable.ic_aicalls_phone_outgoing",
                      "contentDescription = direction",
                      "if (state.canDelete || state.canDeleteAbandoned)","confirmDelete(session.id, abandoned)"):
            self.assertIn(token,history)
        self.assertIn("ConversationHistoryController(history",history)
        self.assertIn("controller.deleteCompletedConfirmed(id, callback)",history)
        self.assertIn("session.complete && archive.canDelete(id)",controller)
        self.assertIn("archive.canDeleteAbandoned(session.id)", controller)
        self.assertIn("phoneIsIdle() && archive.deleteAbandoned(id)", controller)
        self.assertIn("archive.deleteCompleted(id)",controller)
        self.assertIn('File(directory, "COMPLETE").isFile',archive)
        self.assertIn("load(id).complete",archive)
        self.assertNotIn('tag = "history-back-to-list"',history)
        self.assertGreater(history.index('tag = "history-delete:"'),
                           history.index("private fun showSession"))
        self.assertIn("UiActionTone.REVOCATION", history)
        self.assertIn("minimumWidth = dp(context, 48)",design)

    def test_one_consistent_dropdown_and_direct_language(self):
        selector=(K/"ui/UiSelect.kt").read_text()
        hub=(K/"ui/SettingsHubActivity.kt").read_text()
        self.assertIn("override fun getDropDownView",selector)
        self.assertIn("UiDesign.surfaceDrawable",selector)
        self.assertIn('tag = "ui-language-picker"',hub)
        self.assertNotIn('tag = "ui-language-toggle"',hub)
        self.assertNotIn('tag = "ui-language-group"',hub)
        self.assertIn("UiSelect.setOptions(this, languageLabels)",hub)
        self.assertNotIn("RadioGroup(this)",hub)
        # Keep native choices after separating the coordinator from Android views.
        # Assert owner wiring and the actual controls, not their former file location.
        owner=(K/"audioengines/ModelVoiceSettings.kt").read_text()
        input_dialog=(K/"ui/modelvoice/ModelVoiceSpeechDialogs.kt").read_text()
        model_dialog=(K/"ui/modelvoice/ModelVoiceModelDialog.kt").read_text()
        voice_view=(K/"ui/modelvoice/ModelVoiceVoiceOptionsView.kt").read_text()
        self.assertIn("ModelVoiceInputDialog(",owner)
        self.assertIn("ModelVoiceModelDialog(",owner)
        self.assertIn("ModelVoiceVoiceOptionsView(",owner)
        self.assertIn("RadioGroup(activity)",input_dialog)
        self.assertIn("RadioButton(activity)",input_dialog)
        self.assertIn("if (onSelected(choice.input)) dialog.dismiss()",input_dialog)
        self.assertIn("setSingleChoiceItems(",model_dialog)
        self.assertIn("if (saved) dialog.dismiss()",model_dialog)
        self.assertIn("RadioButton(activity)",voice_view)
        self.assertIn("setOnClickListener { onSelectVoice(id) }",voice_view)
        self.assertIn("UiSelect.setOptions(",
                      (K/"conversation/ConversationSkillLabActivity.kt").read_text())

if __name__=="__main__": unittest.main()
