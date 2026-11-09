package pl.michalmatu.aicallbridge.localspeech

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.LiteRtGemma4Runtime
import pl.michalmatu.aicallbridge.textagent.NativeModelTiming
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** G5.3: real E2B sendMessage cancellation and subsequent recovery, no Telecom. */
@RunWith(AndroidJUnit4::class)
class AndroidG53NativeInferenceStopTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun listener(
        done: CountDownLatch,
        errors: AtomicReference<String?>,
        completes: AtomicInteger,
    ): TextCallAgentBackend.Listener = object : TextCallAgentBackend.Listener {
        override fun onComplete(text: String) {
            completes.incrementAndGet()
            done.countDown()
        }
        override fun onError(reason: String) {
            errors.set(reason)
            done.countDown()
        }
    }

    @Test fun cancelDuringNativeSendDiscardsResultAndAllowsCleanRecovery() {
        val profile = checkNotNull(AudioEngineProfileStore(context).load())
        assertEquals("g53_expected_e2b_profile", DialogueModelId.GEMMA_4_E2B, profile.dialogue)
        val model = Gemma4LiteRtTextBackendFactory.modelFile(context, DialogueModelId.GEMMA_4_E2B)
        assertTrue("g53_model_missing", model.isFile && model.length() > 0L)

        val entered = CountDownLatch(1)
        val warmTiming = CountDownLatch(1)
        val stoppedTiming = CountDownLatch(1)
        val recoveryTiming = CountDownLatch(1)
        val sendOrdinal = AtomicInteger()
        val timings = CopyOnWriteArrayList<NativeModelTiming>()
        val runtime = LiteRtGemma4Runtime(
            modelPath = model.absolutePath,
            systemInstruction = "Laboratorium offline. Odpowiadaj po polsku. Nie wykonuj narzędzi ani połączeń.",
            contextTokenBudget = 4_096,
            onNativeSendEntered = {
                if (sendOrdinal.incrementAndGet() == 2) entered.countDown()
            },
            onTiming = {
                timings.add(it)
                when (timings.size) {
                    1 -> warmTiming.countDown()
                    2 -> stoppedTiming.countDown()
                    3 -> recoveryTiming.countDown()
                }
            },
        )
        val warmDone = CountDownLatch(1)
        val warmErrors = AtomicReference<String?>()
        val warmCompletions = AtomicInteger()
        val stoppedDone = CountDownLatch(1)
        val stoppedErrors = AtomicReference<String?>()
        val stoppedCompletions = AtomicInteger()
        val recoveredDone = CountDownLatch(1)
        val recoveredErrors = AtomicReference<String?>()
        val recoveredCompletions = AtomicInteger()
        try {
            // Initial generation creates Engine and Conversation; the second is a warm native send.
            runtime.generate(
                "Odpowiedz jednym słowem: gotowe.",
                listener(warmDone, warmErrors, warmCompletions),
            )
            assertTrue("g53_warm_timeout", warmDone.await(180, TimeUnit.SECONDS))
            assertNull("g53_warm_error", warmErrors.get())
            assertEquals("g53_warm_completion", 1, warmCompletions.get())
            assertTrue("g53_warm_timing_missing", warmTiming.await(20, TimeUnit.SECONDS))

            runtime.generate(
                "Wyjaśnij szczegółowo w kilku zdaniach, dlaczego korekta daty " +
                    "spotkania wymaga zapamiętania nowej daty, a nie starej.",
                listener(stoppedDone, stoppedErrors, stoppedCompletions),
            )
            assertTrue("g53_native_send_not_entered", entered.await(45, TimeUnit.SECONDS))
            // Signal fires immediately before sendMessage. Allow the native call to enter,
            // while the multi-sentence warm generation remains in progress.
            Thread.sleep(200L)
            assertEquals("g53_generation_ended_before_stop", 0, stoppedCompletions.get())
            val stopStarted = System.nanoTime()
            runtime.cancel()
            val stopMs = (System.nanoTime() - stopStarted) / 1_000_000L
            assertTrue("g53_cancelled_native_timing_missing",
                stoppedTiming.await(120, TimeUnit.SECONDS))
            assertEquals("g53_stale_model_output", 0, stoppedCompletions.get())
            assertNull("g53_stale_model_error", stoppedErrors.get())
            val interrupted = timings[1]
            assertTrue("g53_no_native_send_turn", interrupted.turn >= 2)
            assertTrue("g53_cancel_did_not_target_native_send", interrupted.nativeCancelRequested)
            assertNotEquals("g53_interrupted_native_completed", "complete", interrupted.status)
            assertEquals("g53_e2b_gpu_expected", "GPU", interrupted.backend)

            // Recovery must start a new usable Conversation after interrupted native context.
            runtime.generate(
                "Odpowiedz jednym słowem: wznowiono.",
                listener(recoveredDone, recoveredErrors, recoveredCompletions),
            )
            assertTrue("g53_recovery_timeout", recoveredDone.await(180, TimeUnit.SECONDS))
            assertNull("g53_recovery_error", recoveredErrors.get())
            assertEquals("g53_recovery_completion", 1, recoveredCompletions.get())
            assertTrue("g53_recovery_timing_missing", recoveryTiming.await(20, TimeUnit.SECONDS))
            assertEquals("g53_recovery_status", "complete", timings[2].status)
            Log.i("AiCallG53Stop", "e2b_gpu=true;native_send_entered=true;" +
                "native_cancel_requested=true;cancel_return_ms=" + stopMs +
                ";cancelled_status=" + interrupted.status +
                ";cancelled_turn=" + interrupted.turn +
                ";fresh_conversation_recovered=true;late_callbacks=0;offcall=true")
        } finally {
            runtime.close()
        }
        assertEquals("g53_profile_changed", profile, AudioEngineProfileStore(context).load())
    }
}
