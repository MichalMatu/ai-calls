package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampaignDialogueModeTest {
    @Test fun existingCampaignStartsInUnchangedScriptMode() {
        assertEquals(CampaignDialogueMode.SCRIPT_GEMMA, CampaignDialogueMode.explicitStart(null))
        assertEquals(CampaignDialogueMode.SCRIPT_GEMMA,
            CampaignDialogueMode.explicitStart(CampaignDialogueMode.SCRIPT_GEMMA.name))
    }

    @Test fun newModeMustBeExplicitAndRecognized() {
        assertEquals(CampaignDialogueMode.GEMMA_DIRECT,
            CampaignDialogueMode.explicitStart("GEMMA_DIRECT"))
        assertNull(CampaignDialogueMode.explicitStart("RAW_UNGUARDED_LLM"))
        assertNull(CampaignDialogueMode.fromWire(""))
        assertNull(CampaignDialogueMode.fromWire("UNKNOWN"))
    }
}
