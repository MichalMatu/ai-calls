"""UI padding uses dp instead of raw px across all product screen layouts."""
import re
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main/kotlin/pl/michalmatu/aicallbridge"
SCREENS=["conversation/ConversationHistoryActivity.kt","conversation/ConversationSkillLabActivity.kt","conversation/ConversationSkillsActivity.kt","conversation/ConversationPreparationActivity.kt","audioengines/AudioEngineSettingsActivity.kt","textagent/LanguageModelActivity.kt","incoming/IncomingAssistantSettingsActivity.kt","tools/ConversationToolsActivity.kt","debug/UsbDebugPanelActivity.kt","identity/PhoneEnrollmentActivity.kt","campaign/CampaignToolActivity.kt"]
class DensityContract(unittest.TestCase):
    def test_no_unscaled_literal_padding_on_product_screens(self):
        for rel in SCREENS:
            with self.subTest(screen=rel):
                source=(ROOT/rel).read_text()
                self.assertIn("import pl.michalmatu.aicallbridge.ui.UiDesign",source)
                self.assertIn("UiDesign.dp(",source)
                self.assertIsNone(re.search(r"setPadding\(\s*[1-9][0-9]*\s*,",source))
if __name__=="__main__": unittest.main()
