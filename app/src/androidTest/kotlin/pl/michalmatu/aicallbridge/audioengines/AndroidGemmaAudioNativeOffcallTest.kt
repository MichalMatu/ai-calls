package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolated experimental LiteRT audio backend proof. No call and no real recorded audio.
 * Does not enable this configuration in the selectable production registry.
 */
@RunWith(AndroidJUnit4::class)
class AndroidGemmaAudioNativeOffcallTest {
    @Test fun audioBytesNativeEngineHandlesSyntheticPcmWithoutCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ready = CountDownLatch(1)
        val terminal = CountDownLatch(1)
        val error = AtomicReference<String?>(null)
        val gotText = AtomicReference(false)
        val input = SharedGemmaAudioProbeInput(context)
        try {
            val started = System.nanoTime()
            input.start(object : SpeechInputProvider.Listener {
                override fun onReady() { ready.countDown() }
                override fun onFinalTranscript(text: String) {
                    gotText.set(text.isNotBlank())
                    terminal.countDown()
                }
                override fun onError(reason: String) {
                    error.set(reason)
                    ready.countDown()
                    terminal.countDown()
                }
            })
            assertTrue("Gemma Audio initialization timed out", ready.await(200, TimeUnit.SECONDS))
            println("GEMMA_AUDIO_ENGINE_READY_MS=" + ((System.nanoTime() - started) / 1_000_000L))
            assertNull("Gemma Audio initialization error: " + error.get(), error.get())
            assertTrue(input.writePcm(ByteArray(16_000 * 2)))
            input.finishInput()
            assertTrue("Gemma Audio PCM inference timed out", terminal.await(200, TimeUnit.SECONDS))
            println("GEMMA_AUDIO_TOTAL_MS=" + ((System.nanoTime() - started) / 1_000_000L))
            println("GEMMA_AUDIO_RETURNED_TEXT=" + gotText.get())
            println("GEMMA_AUDIO_NATIVE_ERROR_CODE=" + (error.get() ?: "none"))
            // Silence can yield blank transcript, but failures of the native audio backend are real.
            assertTrue(
                "Gemma Audio synthetic inference did not return a completed response",
                gotText.get() || error.get() == "gemma_audio_empty_transcript",
            )
        } finally {
            input.close()
        }
    }
}
