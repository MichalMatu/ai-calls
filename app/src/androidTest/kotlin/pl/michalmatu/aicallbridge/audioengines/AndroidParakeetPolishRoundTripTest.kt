package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.localspeech.LocalTtsSpeechOutput

/**
 * Off-call Polish ASR evidence: local system TTS synthesizes an invented test sentence and
 * Parakeet receives the resulting 16-kHz PCM. No microphone, no Call/Telecom, no user data.
 * The actual transcript stays in memory and is NOT written to logs/GitHub.
 */
@RunWith(AndroidJUnit4::class)
class AndroidParakeetPolishRoundTripTest {
    @Test fun recognizesSynthesizedPolishSpeechWithLexicalOverlap() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(ParakeetModelFile.validPinnedModel(ParakeetModelFile.resolve(context)))

        val synthDone = CountDownLatch(1)
        val synthPcm = AtomicReference<ByteArray?>(null)
        val synthError = AtomicReference<String?>(null)
        val tts = LocalTtsSpeechOutput(context, "pl-PL")
        val totalStarted = System.nanoTime()
        try {
            tts.synthesize(
                "Dzień dobry. Proszę o pomoc konsultanta.",
                object : LocalTtsSpeechOutput.Listener {
                    override fun onPcm16Mono16k(pcm: ByteArray) {
                        synthPcm.set(pcm)
                        synthDone.countDown()
                    }
                    override fun onError(reason: String) {
                        synthError.set(reason)
                        synthDone.countDown()
                    }
                },
            )
            assertTrue("Offline Polish TTS timed out", synthDone.await(140, TimeUnit.SECONDS))
            assertNull("Offline Polish TTS error: " + synthError.get(), synthError.get())
        } finally {
            tts.close()
        }
        val pcm = assertNotNull(synthPcm.get()).let { synthPcm.get()!! }
        assertTrue("Offline TTS produced no audible-length PCM", pcm.size >= 8_000)
        println("POLISH_ROUNDTRIP_TTS_MS=" + ((System.nanoTime() - totalStarted) / 1_000_000))
        println("POLISH_ROUNDTRIP_TTS_AUDIO_MS=" + (pcm.size.toLong() * 1000L / (16_000L * 2L)))

        val ready = CountDownLatch(1)
        val done = CountDownLatch(1)
        val transcript = AtomicReference<String?>(null)
        val error = AtomicReference<String?>(null)
        val input = ParakeetSpeechInputProvider(context)
        try {
            input.start(object : SpeechInputProvider.Listener {
                override fun onReady() { ready.countDown() }
                override fun onFinalTranscript(text: String) {
                    transcript.set(text)
                    done.countDown()
                }
                override fun onError(reason: String) {
                    error.set(reason)
                    ready.countDown()
                    done.countDown()
                }
            })
            assertTrue("Native Parakeet prewarm timed out", ready.await(180, TimeUnit.SECONDS))
            assertNull("Native Parakeet ready error: " + error.get(), error.get())
            for (offset in pcm.indices step 32_000) {
                val count = minOf(32_000, pcm.size - offset)
                assertTrue("Parakeet PCM RX queue rejected audio", input.writePcm(pcm, offset, count))
            }
            // Flush final segmented recognition using synthetic silence, not cellular VAD.
            assertTrue(input.writePcm(ByteArray(32_000 * 2)))
            input.finishInput()
            assertTrue("Native Polish ASR timed out", done.await(200, TimeUnit.SECONDS))
            val text = transcript.get().orEmpty().lowercase(Locale.forLanguageTag("pl-PL"))
            val matched = listOf("dzień", "dobry", "proszę", "pomoc", "konsultan")
                .count { text.contains(it) }
            println("POLISH_ROUNDTRIP_ASR_TOTAL_MS=" +
                ((System.nanoTime() - totalStarted) / 1_000_000))
            println("POLISH_ROUNDTRIP_RECOGNIZED_CHARS=" + text.length)
            println("POLISH_ROUNDTRIP_MATCHED_TEST_WORDS=" + matched)
            println("POLISH_ROUNDTRIP_NATIVE_ERROR_PRESENT=" + (error.get() != null))
            assertNull("Parakeet native ASR error: " + error.get(), error.get())
            assertTrue("Parakeet returned blank Polish transcript", text.isNotBlank())
            assertTrue("Parakeet Polish test phrase not recognized", matched >= 2)
        } finally {
            input.close()
        }
    }
}
