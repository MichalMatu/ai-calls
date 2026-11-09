package pl.michalmatu.aicallbridge.localspeech

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AndroidSttInputProvider
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.SpeechInputProvider

/** Native Android STT and TTS STOP, off-call: unlike G5.2 these run active platform services. */
@RunWith(AndroidJUnit4::class)
class AndroidG53NativeSpeechStopTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun cancelNativeSttWhileRecognizerReadyThenRestart() {
        val profile = checkNotNull(AudioEngineProfileStore(context).load())
        val provider = AndroidSttInputProvider(context, "pl-PL")
        val ready = CountDownLatch(1)
        val restarted = CountDownLatch(1)
        val firstFinals = AtomicInteger()
        val firstErrors = AtomicReference<String?>()
        val secondErrors = AtomicReference<String?>()
        try {
            instrumentation.runOnMainSync {
                provider.start(object : SpeechInputProvider.Listener {
                    override fun onReady() { ready.countDown() }
                    override fun onFinalTranscript(text: String) { firstFinals.incrementAndGet() }
                    override fun onError(reason: String) {
                        firstErrors.set(reason)
                        ready.countDown()
                    }
                })
            }
            assertTrue("g53_native_stt_ready_timeout", ready.await(75, TimeUnit.SECONDS))
            assertNull("g53_native_stt_error", firstErrors.get())
            val silence = ByteArray(LocalSpeechFormat.bytesForDurationMs(20))
            // Native recognizer is actively reading app-owned PCM when STOP arrives.
            repeat(20) {
                assertTrue("g53_native_stt_rejected_pcm",
                    provider.writePcm(silence, 0, silence.size))
                Thread.sleep(20)
            }
            instrumentation.runOnMainSync { provider.cancel() }
            Thread.sleep(850L)
            assertEquals("g53_stale_stt_final", 0, firstFinals.get())
            assertNull("g53_stale_stt_error", firstErrors.get())

            instrumentation.runOnMainSync {
                provider.start(object : SpeechInputProvider.Listener {
                    override fun onReady() { restarted.countDown() }
                    override fun onFinalTranscript(text: String) = Unit
                    override fun onError(reason: String) {
                        secondErrors.set(reason)
                        restarted.countDown()
                    }
                })
            }
            assertTrue("g53_native_stt_restart_timeout", restarted.await(75, TimeUnit.SECONDS))
            assertNull("g53_native_stt_restart_error", secondErrors.get())
            Log.i("AiCallG53Speech", "native_stt_active=true;native_stt_cancel=true;" +
                "native_stt_restarted=true;stale_stt_callbacks=0;offcall=true")
        } finally {
            instrumentation.runOnMainSync { provider.close() }
        }
        assertEquals("g53_stt_profile_changed", profile, AudioEngineProfileStore(context).load())
    }

    @Test fun cancelNativeTtsAfterSynthesisStartsThenRestart() {
        val profile = checkNotNull(AudioEngineProfileStore(context).load())
        val tts = LocalTtsSpeechOutput(context, "pl-PL")
        val started = CountDownLatch(1)
        val stalePcm = AtomicInteger()
        val staleErrors = AtomicReference<String?>()
        val newPcm = AtomicReference<ByteArray?>()
        val newErrors = AtomicReference<String?>()
        val done = CountDownLatch(1)
        try {
            tts.onSynthesisStarted = { started.countDown() }
            val longSentence = "To jest dłuższy, wyłącznie syntetyczny test " +
                "anulowania lokalnej syntezy mowy w trakcie działania. "
            instrumentation.runOnMainSync {
                tts.synthesize(longSentence.repeat(38), object : LocalTtsSpeechOutput.Listener {
                    override fun onPcm16Mono16k(pcm: ByteArray) { stalePcm.incrementAndGet() }
                    override fun onError(reason: String) { staleErrors.set(reason) }
                })
            }
            assertTrue("g53_native_tts_started_timeout", started.await(90, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { tts.cancel() }
            Thread.sleep(850L)
            assertEquals("g53_stale_native_tts_pcm", 0, stalePcm.get())
            assertNull("g53_stale_native_tts_error", staleErrors.get())

            tts.onSynthesisStarted = {}
            instrumentation.runOnMainSync {
                tts.synthesize("To jest sprawdzony test ponownego uruchomienia.",
                    object : LocalTtsSpeechOutput.Listener {
                        override fun onPcm16Mono16k(pcm: ByteArray) {
                            newPcm.set(pcm)
                            done.countDown()
                        }
                        override fun onError(reason: String) {
                            newErrors.set(reason)
                            done.countDown()
                        }
                    })
            }
            assertTrue("g53_native_tts_restart_timeout", done.await(90, TimeUnit.SECONDS))
            assertNull("g53_native_tts_restart_error", newErrors.get())
            val pcm = checkNotNull(newPcm.get()) { "g53_native_tts_restart_missing_pcm" }
            assertTrue("g53_native_tts_restart_pcm_short", pcm.size > 4_000)
            assertEquals("g53_native_tts_restart_pcm_alignment", 0, pcm.size % 2)
            Log.i("AiCallG53Speech", "native_tts_active=true;native_tts_cancel=true;" +
                "native_tts_restarted=true;stale_tts_pcm=0;restart_pcm_bytes=" +
                pcm.size + ";offcall=true")
        } finally {
            instrumentation.runOnMainSync { tts.close() }
        }
        assertEquals("g53_tts_profile_changed", profile, AudioEngineProfileStore(context).load())
    }
}
