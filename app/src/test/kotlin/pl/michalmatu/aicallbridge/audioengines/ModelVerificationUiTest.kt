package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test

class ModelVerificationUiTest {
    @Test fun allDownloadedModelsHaveManualCheckExceptDuringTransfer() {
        assertTrue(ModelVerificationUi.checkVisible(true, false))
        assertFalse(ModelVerificationUi.checkVisible(false, false))
        assertFalse(ModelVerificationUi.checkVisible(true, true))
    }
    @Test fun onlyActualVerifiedStateIsPresentedAsVerified() {
        assertEquals(ModelVerificationStatus.DOWNLOADED_UNVERIFIED,
            ModelVerificationUi.status(false, false, false))
        assertEquals(ModelVerificationStatus.REQUIRED_TEXT,
            ModelVerificationUi.status(false, true, false))
        assertEquals(ModelVerificationStatus.REQUIRED_AUDIO,
            ModelVerificationUi.status(false, true, true))
        assertEquals(ModelVerificationStatus.VERIFIED_TEXT,
            ModelVerificationUi.status(true, true, false))
        assertEquals(ModelVerificationStatus.VERIFIED_AUDIO,
            ModelVerificationUi.status(true, true, true))
    }
}
