package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test

class PiperSystemServiceContractTest {
    @Test fun piperIsExplicitSeparateEngineNotAnAndroidVoiceAlias() {
        assertEquals("dev.ihorshevchuk.piper", PiperServiceContract.PACKAGE)
        assertNotEquals("com.google.android.tts", PiperServiceContract.PACKAGE)
        assertNull(AudioEngineRegistry.incompatibility(AudioEngineProfile(output = SpeechOutputId.PIPER_PL)))
    }
}
