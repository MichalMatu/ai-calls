package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.session.GsmShellMediaProtocol

class LocalPhoneLlmBackendFactoryTest {
    @Test
    fun `S8 GSM defaults to app private Gemma and rejects Shizuku Qwen`() {
        val lab = GsmShellMediaProtocol.S8_PACKAGE
        val stable = "pl.michalmatu.aicallbridge"
        assertEquals(LocalPhoneModelExecution.APP_PRIVATE_LITERT,
            LocalPhoneLlmBackendFactory.defaultExecution(lab))
        assertEquals(LocalPhoneModelExecution.SHIZUKU_QWEN,
            LocalPhoneLlmBackendFactory.defaultExecution(stable))
        assertFalse(LocalPhoneLlmBackendFactory.permits(
            lab, LocalPhoneModelExecution.SHIZUKU_QWEN))
        assertTrue(LocalPhoneLlmBackendFactory.permits(
            lab, LocalPhoneModelExecution.APP_PRIVATE_LITERT))
        assertTrue(LocalPhoneLlmBackendFactory.permits(
            stable, LocalPhoneModelExecution.SHIZUKU_QWEN))
    }

    @Test
    fun `production phone provider selects qwen 1 point 5b`() {
        assertEquals("qwen-phone-1.5b", LocalPhoneLlmBackendFactory.MODEL)
        assertEquals("http://127.0.0.1:18115/v1/", LocalPhoneLlmBackendFactory.BASE_URL)
    }

    @Test
    fun `phone prompt treats speech input as imperfect transcript and forbids guessing`() {
        val prompt = LocalPhoneLlmBackendFactory.SYSTEM_PROMPT.lowercase()
        assertTrue(prompt.contains("transkrypt"))
        assertTrue(prompt.contains("nie wymyślaj"))
        assertTrue(prompt.contains("niejasny"))
        assertTrue(prompt.contains("ivR".lowercase()))
    }
}
