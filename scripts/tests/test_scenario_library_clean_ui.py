"""Scenarios screen remains single-save, simple, and off-call."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class ScenarioCleanUiContract(unittest.TestCase):
    def test_library_preserves_personal_first_and_collapsed_templates(self):
        ui=(ROOT/"app/src/main/kotlin/pl/michalmatu/aicallbridge/conversation/ScenarioLibraryActivity.kt").read_text()
        for token in ('tag = "scenario-new"','tag = "scenario-presets-toggle"',
                      'tag = "scenario-presets-container"',
                      'showEditor(ScenarioCatalog.custom, creating = true)',
                      'templatesExpanded = savedInstanceState?.getBoolean',
                      'creatingNewScenario = creating'):
            self.assertIn(token,ui)
        self.assertLess(ui.index('tag = "scenario-new"'),ui.index('tag = "scenario-presets-toggle"'))
        self.assertIn("else if (creating) definition.draft()",ui)
    def test_editor_only_has_save_prepare_and_one_delete_menu(self):
        ui=(ROOT/"app/src/main/kotlin/pl/michalmatu/aicallbridge/conversation/ScenarioLibraryActivity.kt").read_text()
        for key in ('scenario-prepare-outgoing','scenario-save','scenario-delete-options',
                    'scenario-form-status','library.update(personal.id',
                    'menu.add(getString(R.string.scenario_ui_delete))'):
            self.assertIn(key,ui)
        for removed in ('scenario-advanced-actions','scenario-version-picker',
                        'scenario_ui_duplicate','scenario_ui_restore','scenario_ui_preview'):
            self.assertNotIn(removed,ui)
        self.assertIn('existing ?: run {',ui)
        self.assertIn('if (!saving)',ui)
        self.assertIn('ScenarioOutgoingCallActivity::class.java',ui)
        self.assertNotIn("AppOwnedCampaignService",ui)
    def test_labels_localized(self):
        for locale in ("values","values-en"):
            values=(ROOT/"app/src/main/res"/locale/"strings.xml").read_text()
            for key in ("scenario_ui_new","scenario_ui_new_title","scenario_ui_none",
                        "scenario_ui_save_changes","scenario_ui_save_personal",
                        "scenario_ui_delete_simple_message"):
                self.assertIn('name="'+key+'"',values)
if __name__=="__main__": unittest.main()
