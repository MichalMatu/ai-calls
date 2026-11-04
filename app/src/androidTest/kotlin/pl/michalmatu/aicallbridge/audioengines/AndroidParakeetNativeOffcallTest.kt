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
 * Explicit S22-only native acceptance, not part of automatic app startup.
 * Input is generated in memory; never dials, records a person, uses a microphone,
 * calls a server, modifies a grant, or prints private text/audio.
 */
@RunWith(AndroidJUnit4::class)
class AndroidParakeetNativeOffcallTest {
    @Test fun statefulParakeetPrewarmsAndProcessesSyntheticPcmWithoutNativeFailure() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = ParakeetModelFile.resolve(context)
        assertTrue("Pinned Parakeet model missing", path.isFile)
        assertTrue("Pinned Parakeet identity mismatch", ParakeetModelFile.validPinnedModel(path))
        val ready = CountDownLatch(1)
        val terminal = CountDownLatch(1)
        val error = AtomicReference<String?>(null)
        val finalReturned = AtomicReference(false)
        val input = ParakeetSpeechInputProvider(context)
        try {
            val startNs = System.nanoTime()
            input.start(object : SpeechInputProvider.Listener {
                override fun onReady() { ready.countDown() }
                override fun onFinalTranscript(text: String) {
                    finalReturned.set(text.isNotBlank())
                    terminal.countDown()
                }
                override fun onError(reason: String) {
                    error.set(reason)
                    ready.countDown()
                    terminal.countDown()
                }
            })
            assertTrue("Parakeet native preload exceeded 180 s", ready.await(180, TimeUnit.SECONDS))
            println("PARAKEET_NATIVE_PREWARM_MS=" + ((System.nanoTime() - startNs) / 1_000_000L))
            assertNull("Parakeet failed prewarm: " + error.get(), error.get())
            // Exactly five seconds of silent synthetic PCM16 at 16 kHz, never real conversation data.
            val synthetic = ByteArray(16_000 * 2 * 5)
            assertTrue("Parakeet PCM RX write failed", input.writePcm(synthetic))
            input.finishInput()
            assertTrue("Parakeet terminal callback exceeded 180 s", terminal.await(180, TimeUnit.SECONDS))
            println("PARAKEET_NATIVE_TOTAL_MS=" + ((System.nanoTime() - startNs) / 1_000_000L))
            println("PARAKEET_NATIVE_FINAL_RECEIVED=" + finalReturned.get())
            println("PARAKEET_NATIVE_ERROR_CODE=" + (error.get() ?: "none"))
            assertTrue(
                "Parakeet synthetic silence must terminate without a native error",
                finalReturned.get() || error.get() == "parakeet_empty_transcript",
            )
        } finally {
            input.close()
        }
    }
}
