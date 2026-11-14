package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test

class PiperSystemServiceContractTest {
    @Test fun deferNativeVoiceLoadOnlyWhenOneExactExplicitPiperVoiceExists() {
        val gosia = "pl_PL-gosia-medium"
        assertTrue(PiperServiceContract.canDeferVoiceLoad(
            PiperServiceContract.PACKAGE, gosia, listOf(gosia)))
        assertFalse(PiperServiceContract.canDeferVoiceLoad(
            PiperServiceContract.PACKAGE, gosia, listOf(gosia, "pl_PL-ania-medium")))
        assertFalse(PiperServiceContract.canDeferVoiceLoad(
            PiperServiceContract.PACKAGE, gosia, listOf("pl_PL-ania-medium")))
        assertFalse(PiperServiceContract.canDeferVoiceLoad(
            PiperServiceContract.PACKAGE, null, listOf(gosia)))
        assertFalse(PiperServiceContract.canDeferVoiceLoad(
            "com.google.android.tts", gosia, listOf(gosia)))
        assertFalse(PiperServiceContract.canDeferVoiceLoad(
            PiperServiceContract.PACKAGE, gosia, emptyList()))
    }

    @Test fun piperIsExplicitSeparateEngineNotAnAndroidVoiceAlias() {
        assertEquals("dev.ihorshevchuk.piper", PiperServiceContract.PACKAGE)
        assertNotEquals("com.google.android.tts", PiperServiceContract.PACKAGE)
        assertNull(AudioEngineRegistry.incompatibility(AudioEngineProfile(output = SpeechOutputId.PIPER_PL)))
    }
}
