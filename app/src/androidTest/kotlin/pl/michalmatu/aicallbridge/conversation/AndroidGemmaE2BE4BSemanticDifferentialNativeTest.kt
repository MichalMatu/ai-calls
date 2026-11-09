package pl.michalmatu.aicallbridge.conversation

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.NativeModelTiming
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** Differential semantic corpus on real S22; never executes tool proposals, calls or exports text. */
@RunWith(AndroidJUnit4::class)
class AndroidGemmaE2BE4BSemanticDifferentialNativeTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private data class Result(
        val reply: ConversationReply,
        val elapsedMs: Long,
        val timing: NativeModelTiming?,
    )

    private fun ask(
        backend: TextCallAgentBackend,
        utterance: String,
        timings: CopyOnWriteArrayList<NativeModelTiming>,
    ): Result {
        val latch = CountDownLatch(1)
        val response = AtomicReference<String>()
        val failure = AtomicReference<String>()
        val prior = timings.size
        val start = System.nanoTime()
        backend.generate(utterance, object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                response.set(text)
                latch.countDown()
            }
            override fun onError(reason: String) {
                failure.set(reason.substringBefore(':').take(80))
                latch.countDown()
            }
        })
        assertTrue("semantic_turn_timeout", latch.await(240, TimeUnit.SECONDS))
        val total = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        assertNull("semantic_native_failed", failure.get())
        val parsed = ConversationReplyProtocol.decode(requireNotNull(response.get()))
        for (i in 0 until 30) {
            if (timings.size > prior) break
            Thread.sleep(50)
        }
        return Result(parsed, total, timings.getOrNull(prior))
    }

    private fun run(
        model: DialogueModelId,
        corpusId: String,
        messages: List<String>,
    ): List<Result> {
        val prompt = ConversationInstructionBuilder.build(
            ConversationPreparation.supervised().withSkillSnapshot(
                ConversationSkillLibrary.defaults().snapshot("natural")),
            "Prowadź neutralny fikcyjny dialog i pamiętaj sprostowania. Nie wykonuj skutków.",
            ConversationResponseFormat.TOOL_REPLY,
        )
        val timing = CopyOnWriteArrayList<NativeModelTiming>()
        val backend = Gemma4LiteRtTextBackendFactory.createConversationReply(
            context = app, systemInstruction = prompt, modelId = model,
            tools = setOf(ConversationToolId.END_CALL),
            contextTokenBudget = 2_048, onTiming = { timing.add(it) },
        )
        try {
            val turns = messages.map { ask(backend, it, timing) }
            val cold = turns.first()
            val warm = turns.drop(1)
            val metrics = "model=" + model.name + ";corpus=" + corpusId +
                ";turns=" + turns.size +
                ";cold_ms=" + cold.elapsedMs +
                ";cold_engine_ms=" + (cold.timing?.engineReadyMs ?: -1) +
                ";cold_generate_ms=" + (cold.timing?.generateMs ?: -1) +
                ";warm_mean_ms=" +
                (if (warm.isEmpty()) 0 else warm.sumOf { it.elapsedMs } / warm.size) +
                ";backend=" + (turns.last().timing?.backend ?: "UNKNOWN") +
                ";answer_chars=" + turns.sumOf { it.reply.text.length } +
                ";context=2048;no_call=true"
            Log.i("AiCallModelSemantic", metrics)
            return turns
        } finally {
            backend.close()
            Thread.sleep(800)
        }
    }

    @Test fun correctedFactAndQuotedVersusExplicitFarewellDifferential() {
        val originalProfile = AudioEngineProfileStore(app).load()
        assertNotNull(originalProfile)
        val models = listOf(DialogueModelId.GEMMA_4_E2B, DialogueModelId.GEMMA_4_E4B)
        for (model in models) {
            assertEquals("model_not_available_" + model,
                Gemma4ModelReadinessState.READY,
                AndroidGemma4ModelReadiness.check(app, model).state)
        }

        for (model in models) {
            val correction = run(model, "fact_correction", listOf(
                "Zapamiętaj fikcyjny kod roboczy BURSZTYN71. Potwierdź krótko.",
                "Sprostowanie: poprawnym kodem jest JASMIN83. BURSZTYN71 jest nieaktualny.",
                "Jaki jest teraz poprawny kod roboczy? Podaj go dokładnie, bez starego kodu.",
            ))
            val corrected = correction.last().reply.text
            val correctFact = corrected.contains("JASMIN83", ignoreCase = true)
            val oldFactLeaked = corrected.contains("BURSZTYN71", ignoreCase = true)
            val noToolOnFact = correction.all { it.reply.tool == null }
            Log.i("AiCallModelSemantic",
                "model=" + model.name + ";corpus=fact_correction" +
                ";correct_new_code=" + correctFact +
                ";old_code_leaked=" + oldFactLeaked +
                ";tools_none=" + noToolOnFact + ";no_call=true")

            val farewell = run(model, "farewell_context", listOf(
                "W notatce cytuję zdanie 'do widzenia', ale mam jeszcze pytanie. " +
                    "Czy możemy kontynuować?",
                "Nie mam już pytań, to wszystko. Dziękuję, do widzenia. " +
                    "Proszę zakończyć rozmowę.",
            ))
            val didNotCloseQuoted = farewell.first().reply.tool == null
            val proposedExplicitClose = farewell.last().reply.tool == ConversationToolId.END_CALL
            Log.i("AiCallModelSemantic",
                "model=" + model.name + ";corpus=farewell_context" +
                ";quoted_goodbye_continued=" + didNotCloseQuoted +
                ";explicit_goodbye_proposed_stop=" + proposedExplicitClose +
                ";no_call=true")
        }
        assertEquals("selected_model_changed", originalProfile, AudioEngineProfileStore(app).load())
        println("MODEL_SEMANTIC_DIFFERENTIAL=TECHNICAL_PASS;E2B_E4B_10_TURNS;FLAGS_NOT_AUTOMATIC_QUALITY_PASS")
    }
}
