"""Scenario-library visible UI contract; no calls or user data mutations."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class ScenarioCleanUiContract(unittest.TestCase):
    def test_personal_first_new_scenario_and_collapsed_templates(self):
        ui=(ROOT/"app/src/main/kotlin/pl/michalmatu/aicallbridge/conversation/ScenarioLibraryActivity.kt").read_text()
        self.assertIn('tag = "scenario-new"',ui)
        self.assertIn('tag = "scenario-presets-toggle"',ui)
        self.assertIn('tag = "scenario-presets-container"',ui)
        self.assertIn('templatesExpanded = savedInstanceState?.getBoolean',ui)
        self.assertIn('visibility = if (templatesExpanded) View.VISIBLE else View.GONE',ui)
        self.assertIn("showEditor(ScenarioCatalog.custom, creating = true)",ui)
        self.assertIn("else if (creating) definition.draft()",ui)
        self.assertLess(ui.index('tag = "scenario-new"'),ui.index('tag = "scenario-presets-toggle"'))
        self.assertIn('creatingNewScenario = creating',ui)
        self.assertIn('outState.putBoolean("creating_new_scenario"',ui)
        self.assertIn('store.loadPersonalScenarios().create(',ui)
        self.assertIn('store.savePersonalScenarios(updated)',ui)
    def test_compact_primary_actions_and_retained_safety(self):
        ui=(ROOT/"app/src/main/kotlin/pl/michalmatu/aicallbridge/conversation/ScenarioLibraryActivity.kt").read_text()
        for key in ('scenario-prepare-outgoing','scenario-save','scenario-more',
                    'scenario-advanced-actions','scenario-form-status'):
            self.assertIn(key,ui)
        self.assertIn('advanced.addView(revisions',ui)
        self.assertIn('advanced.addView(UiDesign.action(this, getString(R.string.scenario_ui_delete)',ui)
        self.assertIn('leaveEditor()',ui)
        self.assertIn('ScenarioOutgoingCallActivity::class.java',ui)
        self.assertNotIn('root.addView(UiDesign.caption(this, getString(R.string.scenario_ui_intro))',ui)
        self.assertIn("val summary = if (personalTitle == null)",ui)
        self.assertNotIn("AppOwnedCampaignService",ui)
    def test_localized_visible_labels(self):
        for lang in ("values","values-en"):
            values=(ROOT/"app/src/main/res"/lang/"strings.xml").read_text()
            for key in ("scenario_ui_new","scenario_ui_new_title","scenario_ui_none",
                        "scenario_ui_show_templates","scenario_ui_hide_templates",
                        "scenario_ui_more","scenario_ui_new_invalid"):
                self.assertIn('name="'+key+'"',values)
if __name__ == "__main__":
    unittest.main()
