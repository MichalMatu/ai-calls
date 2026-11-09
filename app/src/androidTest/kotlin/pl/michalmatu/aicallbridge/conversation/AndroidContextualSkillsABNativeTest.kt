package pl.michalmatu.aicallbridge.conversation

import android.util.Log
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
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState
import pl.michalmatu.aicallbridge.textagent.NativeModelTiming
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * S22 offline, synthetic-only A/B. No saved preset, Android Call or tool is
 * modified/executed. Logs contain only numeric timings and boolean outcomes,
 * never the source utterance, response, contact data or transcript.
 */
@RunWith(AndroidJUnit4::class)
class AndroidContextualSkillsABNativeTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val tag = "AiCallSkillAB"
    private data class Scenario(val name: String, val goal: String, val first: String, val second: String)
    private data class Observation(
        val elapsedMs: Long,
        val timing: NativeModelTiming?,
        val marker: Boolean,
        val toolIsNone: Boolean,
        val answerChars: Int,
    )

    private fun send(
        backend: TextCallAgentBackend,
        text: String,
        timings: CopyOnWriteArrayList<NativeModelTiming>,
    ): Observation {
        val done = CountDownLatch(1)
        val response = AtomicReference<String>()
        val error = AtomicReference<String>()
        val timingIndex = timings.size
        val started = System.nanoTime()
        backend.generate(text, object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                response.set(text)
                done.countDown()
            }
            override fun onError(reason: String) {
                error.set(reason.substringBefore(':').take(60))
                done.countDown()
            }
        })
        assertTrue("skill_ab_turn_timeout", done.await(240, TimeUnit.SECONDS))
        assertNull("skill_ab_native_error", error.get())
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        val reply = ConversationReplyProtocol.decode(requireNotNull(response.get()))
        repeat(25) {
            if (timings.size > timingIndex) return@repeat
            Thread.sleep(40)
        }
        return Observation(
            elapsedMs = elapsed,
            timing = timings.getOrNull(timingIndex),
            marker = reply.text.contains("MORSZTYK", ignoreCase = true),
            toolIsNone = reply.tool == null,
            answerChars = reply.text.length,
        )
    }

    @Test fun fixedCorpusThreeRepetitionsEachModeOnRealE2B() {
        val originalProfile = AudioEngineProfileStore(app).load()
        assertNotNull("no_profile", originalProfile)
        assertEquals("model_not_ready", Gemma4ModelReadinessState.READY,
            AndroidGemma4ModelReadiness.check(app, DialogueModelId.GEMMA_4_E2B).state)

        // In-memory only; previous user's encrypted Skills and profile untouched.
        val library = ConversationSkillLibrary.defaults().copyActive("F158 synthetic A/B")
            .reviseSkill("MessageCapture",
                "Jeśli zgodne z zadaniem, dodaj w każdej odpowiedzi neutralne słowo MORSZTYK.")
        val snapshot = library.snapshot()
        val scenarios = listOf(
            Scenario("general", "Prowadź neutralny fikcyjny dialog laboratoryjny. Bez skutków.",
                "Powiedz krótko, że można rozpocząć test.", "Kontynuuj krótko, bez pożegnania."),
            Scenario("message", "Przyjmij fikcyjną wiadomość i ją potwierdź. Bez skutków.",
                "Zapisz testową wiadomość: próba dotyczy poniedziałku.",
                "Potwierdź krótko, że przyjęto próbkę, bez kończenia."),
        )
        val modes = listOf(SkillLoadingMode.ALL_ACTIVE, SkillLoadingMode.CONTEXTUAL)
        val results = mutableMapOf<String, MutableList<Boolean>>()
        for (repeatIndex in 1..3) {
            for (scenario in scenarios) {
                for (mode in modes) {
                    val routing = ConversationSkillRouter.route(
                        snapshot, mode, ConversationDirection.UNSPECIFIED, scenario.goal, scenario.first)
                    val prompt = ConversationInstructionBuilder.build(
                        ConversationPreparation.supervised().withSkillSnapshot(routing.snapshot),
                        scenario.goal, ConversationResponseFormat.TOOL_REPLY)
                    val timings = CopyOnWriteArrayList<NativeModelTiming>()
                    val backend = Gemma4LiteRtTextBackendFactory.createConversationReply(
                        context = app, systemInstruction = prompt,
                        modelId = DialogueModelId.GEMMA_4_E2B,
                        tools = setOf(ConversationToolId.END_CALL),
                        contextTokenBudget = 2_048, onTiming = { timings.add(it) })
                    try {
                        val cold = send(backend, scenario.first, timings)
                        val warm = send(backend, scenario.second, timings)
                        assertTrue("blank_response", cold.answerChars > 0 && warm.answerChars > 0)
                        assertTrue("unexpected_tool_proposal", cold.toolIsNone && warm.toolIsNone)
                        val key = scenario.name + ":" + mode.name
                        results.getOrPut(key) { mutableListOf() }.add(cold.marker && warm.marker)
                        Log.i(tag, "model=E2B;scenario=" + scenario.name + ";mode=" + mode.name +
                            ";repeat=" + repeatIndex +
                            ";selected=" + routing.selectedIds.size +
                            ";available=" + routing.availableCount +
                            ";selector_us=" + routing.selectorMicros +
                            ";prompt_chars=" + prompt.length +
                            ";cold_ms=" + cold.elapsedMs +
                            ";engine_ms=" + (cold.timing?.engineReadyMs ?: -1) +
                            ";cold_generate_ms=" + (cold.timing?.generateMs ?: -1) +
                            ";warm_ms=" + warm.elapsedMs +
                            ";backend=" + (warm.timing?.backend ?: "UNKNOWN") +
                            ";marker_cold=" + cold.marker +
                            ";marker_warm=" + warm.marker +
                            ";valid_json=true;tools_none=true;context=2048;no_call=true")
                    } finally {
                        backend.close()
                        Thread.sleep(800)
                    }
                }
            }
        }
        for ((key, hits) in results.toSortedMap()) {
            Log.i(tag, "summary=" + key + ";marker_both_turns=" +
                hits.count { it } + "/" + hits.size + ";semantic_review_required=true")
        }
        assertEquals("selected_profile_changed", originalProfile, AudioEngineProfileStore(app).load())
        assertEquals("incomplete_ab_matrix", 4, results.size)
        assertTrue("not_three_repetitions", results.values.all { it.size == 3 })
        println("F158_SKILLS_AB_S22=24_TECHNICAL_TURNS;MODEL=E2B;N=3;NO_CALL;QUALITY_MANUAL_REVIEW_REQUIRED")
    }
}
