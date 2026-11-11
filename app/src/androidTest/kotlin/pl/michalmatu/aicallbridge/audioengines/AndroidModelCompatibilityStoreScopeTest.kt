package pl.michalmatu.aicallbridge.audioengines

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Cache-only contract: synthetic file, no native inference, audio, TTS or Call. */
@RunWith(AndroidJUnit4::class)
class AndroidModelCompatibilityStoreScopeTest {
    @Test fun audioPassExpiresOnVoiceOrEngineChangeButTextProofRemainsValid() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "compat-test-${System.nanoTime()}.litertlm")
        val profile = AudioEngineProfile(input = SpeechInputId.GEMMA_AUDIO_DIRECT,
            voiceId = "pl-test-a", ttsEnginePackage = "com.google.android.tts")
        val store = ModelCompatibilityStore(context)
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val stamp = checkNotNull(ModelCompatibilityStamp.of(file, profile, true))
            assertTrue(store.confirm(file, true, stamp, profile))
            assertTrue(store.verified(file, true, profile))
            assertTrue(store.verified(file, false))
            assertFalse(store.verified(file, true, profile.copy(voiceId = "pl-test-b")))
            assertFalse(store.verified(file, true,
                profile.copy(ttsEnginePackage = "com.samsung.SMT")))
            assertFalse(store.verified(file, true, profile.copy(languageTag = "en-US")))
            assertTrue(store.verified(file, false))
            assertTrue(store.invalidate(file, true))
            assertFalse(store.verified(file, true, profile))
        } finally {
            store.invalidate(file, true)
            store.invalidate(file, false)
            file.delete()
        }
    }
}
