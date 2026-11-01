package pl.michalmatu.aicallbridge.localspeech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Safe no-call physical contract for Android's Polish STT and offline TTS engines.
 * No dialer, microphone capture, phone media, relay, external effect or audio playback.
 */
@RunWith(AndroidJUnit4::class)
class AndroidLocalSpeechMvpContractTest {
    @Test
    fun polishLocalTtsProducesNonemptyMonoPcm16At16k() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = AtomicReference<ByteArray?>()
        val error = AtomicReference<String?>()
        val complete = CountDownLatch(1)
        val tts = LocalTtsSpeechOutput(context)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                tts.synthesize(
                    "Dzień dobry, to jest krótki lokalny test syntezy mowy.",
                    object : LocalTtsSpeechOutput.Listener {
                        override fun onPcm16Mono16k(pcm: ByteArray) {
                            result.set(pcm)
                            complete.countDown()
                        }

                        override fun onError(reason: String) {
                            error.set(reason)
                            complete.countDown()
                        }
                    },
                )
            }
            assertTrue("TTS timed out", complete.await(45, TimeUnit.SECONDS))
            assertEquals("Local Polish TTS failed", null, error.get())
            val bytes = checkNotNull(result.get()) { "local_tts_no_pcm" }
            assertTrue("Unexpectedly short synthesized PCM", bytes.size > 3_200)
            assertEquals("PCM16 must align to samples", 0, bytes.size % 2)
            println("LOCAL_TTS_PCM16_16K_BYTES=${bytes.size}")
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { tts.close() }
        }
    }

    @Test
    fun polishOnDeviceSttStartsWithoutTelephonyOrIncomingAudio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO),
        )
        val state = AtomicReference<String>("no_callback")
        val started = CountDownLatch(1)
        val input = OnDeviceSpeechInput(context)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                input.start(object : OnDeviceSpeechInput.Listener {
                    override fun onReady() {
                        state.set("ready")
                        started.countDown()
                    }

                    override fun onFinalTranscript(text: String) {
                        state.set("unexpected_transcript")
                        started.countDown()
                    }

                    override fun onError(reason: String) {
                        state.set("error:$reason")
                        started.countDown()
                    }
                })
            }
            assertTrue("STT readiness timed out", started.await(40, TimeUnit.SECONDS))
            assertEquals("On-device pl-PL STT unavailable", "ready", state.get())
            println("LOCAL_STT_PL_READY=PASS")
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { input.close() }
        }
    }
}
