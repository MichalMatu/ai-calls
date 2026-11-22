package pl.michalmatu.aicallbridge.localspeech

import org.junit.Assert.*
import org.junit.Test

class OfflineSpeechLanguagePolicyTest {
    @Test fun englishUsMustNotAcceptInstalledEnglishFromAnotherRegion() {
        assertTrue(OfflineSpeechLanguagePolicy.matchesInstalled("en-US", "en-US"))
        assertFalse(OfflineSpeechLanguagePolicy.matchesInstalled("en-US", "en-GB"))
        assertFalse(OfflineSpeechLanguagePolicy.matchesInstalled("en-US", "en"))
        assertFalse(OfflineSpeechLanguagePolicy.matchesInstalled("en-US", "pl-PL"))
    }

    @Test fun polishExistingMatchBehaviourIsPreserved() {
        assertTrue(OfflineSpeechLanguagePolicy.matchesInstalled("pl-PL", "pl"))
        assertTrue(OfflineSpeechLanguagePolicy.matchesInstalled("pl-PL", "pl-PL"))
        assertFalse(OfflineSpeechLanguagePolicy.matchesInstalled("pl-PL", "en-US"))
    }
}
