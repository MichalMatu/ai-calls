package pl.michalmatu.aicallbridge.localspeech

import android.content.Context
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * English synthetic speech: offline TTS PCM -> on-device EN recognition.
 * No microphone recording, phone calls, playback, profile mutation or exports.
 * The exact identifier is intentionally a QA metric, not a condition for accepting
 * the speech plumbing; dialogue recall is evaluated in the separate six-turn Lab.
 */
@RunWith(AndroidJUnit4::class)
class AndroidEnglishSyntheticAudioTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun englishSyntheticTtsPcmCanBeRecognizedWithoutPhoneCall() {
        val telecom = context.getSystemService(TelecomManager::class.java)
        assertFalse("english_audio_requires_idle_phone", telecom.isInCall)
        val pcmReady = CountDownLatch(1)
        val pcmValue = AtomicReference<ByteArray?>()
        val ttsError = AtomicReference<String?>()
        val tts = LocalTtsSpeechOutput(context, "en-US")
        try {
            instrumentation.runOnMainSync {
                tts.synthesize(
                    "The fictional sample code is KOD WRZOS forty seven. Its corrected color is purple.",
                    object : LocalTtsSpeechOutput.Listener {
                        override fun onPcm16Mono16k(pcm: ByteArray) {
                            pcmValue.set(pcm)
                            pcmReady.countDown()
                        }
                        override fun onError(reason: String) {
                            ttsError.set(reason)
                            pcmReady.countDown()
                        }
                    },
                )
            }
            assertTrue("english_local_tts_timeout", pcmReady.await(90, TimeUnit.SECONDS))
            assertNull("english_local_tts_failed", ttsError.get())
            val pcm = checkNotNull(pcmValue.get()) { "english_local_tts_empty" }
            assertTrue("english_pcm_unexpectedly_short", pcm.size > 8_000)
            assertEquals("english_pcm_alignment", 0, pcm.size % 2)
            instrumentation.sendStatus(2, android.os.Bundle().apply {
                putString("stream", "\nEN_SYNTHETIC_TTS_PCM16_BYTES=" + pcm.size + "\n")
            })
            println("EN_SYNTHETIC_TTS_PCM16_BYTES=" + pcm.size)

            val done = CountDownLatch(1)
            val sttResult = AtomicReference<String?>()
            val sttError = AtomicReference<String?>()
            val writerStarted = AtomicBoolean(false)
            val input = OnDeviceSpeechInput(context, "en-US")
            try {
                instrumentation.runOnMainSync {
                    input.start(object : OnDeviceSpeechInput.Listener {
                        override fun onReady() {
                            if (!writerStarted.compareAndSet(false, true)) return
                            Thread({
                                try {
                                    val pad = ByteArray(LocalSpeechFormat.bytesForDurationMs(420))
                                    val chunk = LocalSpeechFormat.bytesForDurationMs(20)
                                    fun stream(data: ByteArray) {
                                        var offset = 0
                                        while (offset < data.size) {
                                            val count = minOf(chunk, data.size - offset)
                                            check(input.writePcm(data, offset, count)) {
                                                "english_stt_pcm_stream_rejected"
                                            }
                                            offset += count
                                            Thread.sleep(20)
                                        }
                                    }
                                    stream(pad)
                                    stream(pcm)
                                    stream(pad)
                                    input.finishInput()
                                } catch (error: Throwable) {
                                    sttError.set("english_stream_" + error.javaClass.simpleName)
                                    done.countDown()
                                }
                            }, "EnglishSyntheticAudioWriter").start()
                        }
                        override fun onFinalTranscript(text: String) {
                            sttResult.set(text)
                            done.countDown()
                        }
                        override fun onError(reason: String) {
                            sttError.set(reason)
                            done.countDown()
                        }
                    })
                }
                assertTrue("english_stt_timeout", done.await(130, TimeUnit.SECONDS))
                assertNull("english_stt_failed", sttError.get())
                val recognized = checkNotNull(sttResult.get()) { "english_stt_no_transcript" }
                assertTrue("english_stt_empty_transcript", recognized.isNotBlank())
                val normalized = recognized.uppercase(Locale.US)
                val metrics = listOf(
                    "EN_SYNTHETIC_STT_WORD_COUNT" to recognized.split(Regex("\\s+")).size.toString(),
                    "EN_SYNTHETIC_STT_PURPLE" to normalized.contains("PURPLE").toString(),
                    "EN_SYNTHETIC_STT_CODE_EXACT" to normalized.contains("KOD-WRZOS-47").toString(),
                    "EN_SYNTHETIC_AUDIO_TECHNICAL_PASS" to "true",
                )
                metrics.forEach { (key, value) ->
                    instrumentation.sendStatus(2, android.os.Bundle().apply {
                        putString("stream", "\n" + key + "=" + value + "\n")
                    })
                }
            } finally {
                instrumentation.runOnMainSync { input.close() }
            }
        } finally {
            instrumentation.runOnMainSync { tts.close() }
        }
        assertFalse("english_audio_changed_call_state", telecom.isInCall)
    }
}
