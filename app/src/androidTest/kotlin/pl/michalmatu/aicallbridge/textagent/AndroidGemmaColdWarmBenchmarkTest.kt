package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.conversation.ConversationReplyProtocol
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId

/** The same short prompt with a fresh Engine vs prewarm-ready Engine (off-call).
 * Fresh Engine may benefit from OS file cache; warm turns grow conversation context.
 * Never changes phone profile, dials, records audio or uses microphone.
 */
@RunWith(AndroidJUnit4::class)
class AndroidGemmaColdWarmBenchmarkTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val phrase = "Proszę krótko potwierdzić przyjęcie wiadomości. Nie wywołuj narzędzi."
    private val instruction = "Jesteś lokalną sekretarką. Odpowiadaj po polsku " +
        "jednym krótkim zdaniem; stosuj wyłącznie wymagany format JSON."

    private fun emit(id: String, value: Long) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2,
            Bundle().apply { putString("stream", "\nAUDIO_QA_" + id + "=" + value + "\n") })
    }

    private data class Sample(val wallMs: Long, val timing: NativeModelTiming)

    private fun measure(
        backend: TextCallAgentBackend,
        timings: CopyOnWriteArrayList<NativeModelTiming>,
        phase: String,
        sample: Int,
    ): Sample {
        val previous = timings.size
        val done = CountDownLatch(1)
        val reply = AtomicReference<String?>()
        val error = AtomicReference<String?>()
        val started = System.nanoTime()
        backend.generate(phrase, object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) { reply.set(text); done.countDown() }
            override fun onError(reason: String) { error.set(reason); done.countDown() }
        })
        assertTrue("native_gemma_turn_timeout", done.await(210, TimeUnit.SECONDS))
        val wall = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        assertNull("native_gemma_turn_error", error.get())
        val answer = ConversationReplyProtocol.decode(checkNotNull(reply.get()))
        assertTrue("native_gemma_blank_answer", answer.text.isNotBlank())
        assertNull("native_gemma_unexpected_tool", answer.tool)
        var observed: NativeModelTiming? = null
        repeat(60) {
            if (observed == null) {
                observed = timings.getOrNull(previous)
                if (observed == null) Thread.sleep(50)
            }
        }
        val t = checkNotNull(observed) { "native_gemma_timing_missing" }
        assertEquals("native_gemma_status", "complete", t.status)
        val base = "GEMMA_" + phase + "_CASE_" + sample + "_"
        emit(base + "WALL_MS", wall)
        emit(base + "ENGINE_MS", t.engineReadyMs)
        emit(base + "CONV_MS", t.conversationCreateMs)
        emit(base + "GEN_MS", t.generateMs)
        emit(base + "REUSED", if (t.conversationReused) 1L else 0L)
        emit(base + "CHARS", t.outputChars.toLong())
        emit(base + "REPAIRS", t.replyRecoveryAttempts.toLong())
        emit(base + "PASSED", 1L)
        return Sample(wall, t)
    }

    @Test fun freshVsPrewarmedGemmaE2BIdenticalPromptAndHotTurns() {
        val saved = checkNotNull(AudioEngineProfileStore(context).load())
        for (phase in listOf("COLD", "PREWARM")) {
            val nativeTimings = CopyOnWriteArrayList<NativeModelTiming>()
            val prewarmReady = CountDownLatch(1)
            val prewarm = AtomicReference<NativePrewarmTiming?>()
            val backend = Gemma4LiteRtTextBackendFactory.createConversationReply(
                context, instruction, DialogueModelId.GEMMA_4_E2B,
                emptySet<ConversationToolId>(), contextTokenBudget = 4096,
                onTiming = { nativeTimings.add(it) },
                onPrewarm = { prewarm.set(it); prewarmReady.countDown() },
            )
            try {
                if (phase == "PREWARM") {
                    val started = System.nanoTime()
                    backend.prewarm()
                    assertTrue("native_gemma_prewarm_timeout",
                        prewarmReady.await(210, TimeUnit.SECONDS))
                    val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                    val p = checkNotNull(prewarm.get())
                    assertTrue("native_gemma_prewarm_failed", p.ready)
                    emit("GEMMA_PREWARM_WALL_MS", elapsed)
                    emit("GEMMA_PREWARM_ENGINE_MS", p.engineReadyMs)
                    emit("GEMMA_PREWARM_CONV_MS", p.conversationReadyMs)
                }
                val samples = (1..3).map { measure(backend, nativeTimings, phase, it) }
                // Prewarm proves the native Engine is ready, but structured
                // reply recovery may replace the first Conversation and report
                // conversationReused=false. Track retries instead of mistaking
                // this for another cold Engine initialization.
                if (phase == "COLD") {
                    assertFalse("fresh_engine_first_conversation_not_fresh",
                        samples.first().timing.conversationReused)
                } else {
                    assertTrue("prewarmed_engine_reinitialized",
                        samples.first().timing.engineReadyMs < 500L)
                }
                assertTrue("native_gemma_hot_conversation_not_reused",
                    samples.drop(1).all { it.timing.conversationReused })
            } finally {
                backend.close()
            }
            Thread.sleep(800)
        }
        assertEquals("audio_profile_mutated", saved, AudioEngineProfileStore(context).load())
    }
}
