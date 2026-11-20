"""MVP Agent screen contracts: concise UX without changes to call authority."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]/"app/src/main/kotlin/pl/michalmatu/aicallbridge"

class MvpAgentSections(unittest.TestCase):
    def test_agent_tools_first_notebook_disclosed_and_home_notebook_visible(self):
        ui=(ROOT/"tools/ConversationToolsActivity.kt").read_text()
        self.assertIn('tag = "tools-notebook-section"',ui)
        self.assertIn('tag = "tools-notebook-section-toggle"',ui)
        self.assertIn('notebookExpanded = notebookOnly ||',ui)
        self.assertIn('notebookSection.visibility = if (notebookExpanded) View.VISIBLE else View.GONE',ui)
        self.assertIn('NOTEBOOK_EXPANDED_STATE',ui)
        self.assertIn('outState.putBoolean(NOTEBOOK_EXPANDED_STATE, notebookExpanded)',ui)
        self.assertLess(ui.index('R.string.notebook_ui_tools_heading'),ui.index('content.addView(notebookSection,'))
        self.assertIn('tag = "tools-save-settings"',ui)
        self.assertIn('tag = "tools-notebook-delete:"',ui)
        self.assertIn('repository.deleteNotebook(book.sessionId)',ui)
        self.assertNotIn('getString(R.string.notebook_ui_history)',ui)
        self.assertNotIn('startActivity(Intent(this, ConversationHistoryActivity::class.java))',ui)
        self.assertIn('if (hasUnsavedSettings()) confirmDiscardSettings(action)',ui)

    def test_profile_retains_one_save_and_data_scope_with_dirty_back_guard(self):
        ui=(ROOT/"conversation/ConversationPreparationActivity.kt").read_text()
        self.assertEqual(ui.count('tag = "preparation-save"'),1)
        self.assertNotIn('tag = "preparation-preview"',ui)
        for token in ('tag = "preparation-direction-options"',
                      'tag = "preparation-context-notes"',
                      'tag = "preparation-vault-fields"',
                      'store.save(selected)',
                      'confirmDiscard(action)',
                      'profile.skills'):
            self.assertIn(token,ui)

    def test_skills_still_configurable_without_call_side_effects(self):
        ui=(ROOT/"conversation/ConversationSkillsActivity.kt").read_text()
        for token in ('tag = "receptionist-tone-options"',
                      'tag = "receptionist-personal-edit"',
                      'saveSkillLibrary(updated)', 'withPersonalInstructions'):
            self.assertIn(token,ui)
        self.assertNotIn('placeCall(',ui)

    def test_pl_en_notebook_labels(self):
        root=ROOT.parents[3]/"res"
        # app/src/main/kotlin/... -> app/src/main/res
        root=Path(__file__).resolve().parents[2]/"app/src/main/res"
        for locale in ("values","values-en"):
            values=(root/locale/"strings.xml").read_text()
            for key in ("notebook_ui_show_section","notebook_ui_hide_section"):
                self.assertIn('name="'+key+'"',values)

if __name__=="__main__": unittest.main()
