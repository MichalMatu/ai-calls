"""Read-only source contracts for context/data UI and its authorization boundary."""
import unittest
from pathlib import Path

BASE = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"

class ConversationPreparationScopeUiContract(unittest.TestCase):
    def test_context_and_data_only_without_legacy_skills_or_duplicate_links(self):
        ui = (BASE / "conversation/ConversationPreparationActivity.kt").read_text()
        for value in (
            'tag = "preparation-direction-options"',
            'tag = "preparation-context-notes"',
            'tag = "preparation-vault-fields"',
            'tag = "preparation-save"',
            'tag = "preparation-preview"',
            'confirmDiscard(',
            'store.save(selected)',
            'profile.skills',
        ):
            self.assertIn(value, ui)
        for old in (
            'tag = "preparation-skills"',
            'prep_ui_edit_phone',
            'prep_ui_presets',
            'PhoneEnrollmentActivity',
            'ConversationSkillsActivity',
            'ConversationInstructionBuilder.build',
        ):
            self.assertNotIn(old, ui)

    def test_profile_scope_limits_appointment_disclosures_and_outbound_notes_are_used(self):
        service = (BASE / "campaign/AppOwnedCampaignService.kt").read_text()
        appointment = (BASE / "campaign/AppointmentMvpConversationScenario.kt").read_text()
        self.assertGreaterEqual(service.count(
            "preparationStore.load(ConversationPreparation.OUTGOING_ID)"), 3)
        self.assertIn("userContext.scopedDataFields(task.disclosureFields)", service)
        self.assertIn("userContext.scopedDataFields(grant.allowedDisclosureFields)", service)
        self.assertIn("field !in configuration.preparation.dataFields", appointment)

    def test_tabs_and_top_back_can_guard_dirty_form(self):
        chrome = (BASE / "ui/UiChromeActivity.kt").read_text()
        nav = (BASE / "ui/AppNavigation.kt").read_text()
        ui = (BASE / "conversation/ConversationPreparationActivity.kt").read_text()
        self.assertIn("onTabNavigationRequested", chrome)
        self.assertIn("activity.onTabNavigationRequested(navigate)", nav)
        self.assertIn("override fun consumeChromeBack()", ui)
        self.assertIn("override fun onBackPressed()", ui)
        self.assertIn("override fun onTabNavigationRequested(action:", ui)
        self.assertIn("resetDiscardedDraft()", ui)
        self.assertIn("PreparationDraftViewModel", ui)
        self.assertIn("isSaveEnabled = false", ui)
        self.assertNotIn('outState.putString("draft_notes"', ui)

if __name__ == "__main__":
    unittest.main()
