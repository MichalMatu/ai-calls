package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.session.GsmShellMediaProtocol

class LocalPhoneLlmBackendFactoryTest {
    @Test fun onlyVerifiedGsmPackageCanSelectAppPrivateGemma() {
        assertTrue(LocalPhoneLlmBackendFactory.supportsPackage(GsmShellMediaProtocol.S8_PACKAGE))
        assertTrue(LocalPhoneLlmBackendFactory.supportsPackage(GsmShellMediaProtocol.APP_PACKAGE))
        assertFalse(LocalPhoneLlmBackendFactory.supportsPackage("com.example.unknown"))
    }

    @Test fun phonePromptHandlesImperfectTranscriptsWithoutInventingFacts() {
        val prompt = LocalPhoneLlmBackendFactory.SYSTEM_PROMPT.lowercase()
        assertTrue(prompt.contains("transkrypt"))
        assertTrue(prompt.contains("nie wymyślaj"))
        assertTrue(prompt.contains("niejasny"))
        assertTrue(prompt.contains("ivr"))
    }
}
