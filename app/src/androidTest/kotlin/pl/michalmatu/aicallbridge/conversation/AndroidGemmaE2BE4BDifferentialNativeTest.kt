package pl.michalmatu.aicallbridge.conversation

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
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
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState
import pl.michalmatu.aicallbridge.textagent.NativeModelTiming
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Physically compares the exact same synthetic, off-call context and messages
 * with E2B and E4B, without modifying the user's selected audio engine profile.
 * Native sessions are strictly sequential, never concurrently resident.
 * Text remains in process; only booleans, timing and memory metadata are logged.
 */
@RunWith(AndroidJUnit4::class)
class AndroidGemmaE2BE4BDifferentialNativeTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val marker = "KASZTAN"
    private val factCode = "QZ7M42"
    private val models = listOf(DialogueModelId.GEMMA_4_E2B, DialogueModelId.GEMMA_4_E4B)

    private data class Turn(
        val parsed: ConversationReply,
        val timing: NativeModelTiming?,
        val wallMs: Long,
    )
    private data class PairTrial(val model: DialogueModelId, val active: Boolean, val turns: List<Turn>)

    private fun memSnapshot(): String {
        val process = Debug.MemoryInfo()
        Debug.getMemoryInfo(process)
        val system = ActivityManager.MemoryInfo()
        app.getSystemService(ActivityManager::class.java).getMemoryInfo(system)
        return "pss_mib=" + (process.totalPss / 1024) +
            ";ram_avail_mib=" + (system.availMem / 1024L / 1024L) +
            ";low_memory=" + system.lowMemory
    }

    private fun generate(
        backend: TextCallAgentBackend,
        user: String,
        metrics: CopyOnWriteArrayList<NativeModelTiming>,
    ): Turn {
        val done = CountDownLatch(1)
        val result = AtomicReference<String>()
        val error = AtomicReference<String>()
        val started = System.nanoTime()
        val previous = metrics.size
        backend.generate(user, object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                result.set(text)
                done.countDown()
            }
            override fun onError(reason: String) {
                error.set(reason.substringBefore(':').take(80))
                done.countDown()
            }
        })
        assertTrue("native_turn_timeout", done.await(240, TimeUnit.SECONDS))
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        assertNull("native_turn_failed", error.get())
        val parsed = ConversationReplyProtocol.decode(requireNotNull(result.get()))
        assertTrue("empty_native_reply", parsed.text.isNotBlank())
        // The observer may publish on the generator thread after onComplete callback.
        for (i in 0 until 30) {
            if (metrics.size > previous) break
            Thread.sleep(50)
        }
        return Turn(parsed, metrics.getOrNull(previous), elapsed)
    }

    private fun trial(
        model: DialogueModelId,
        active: Boolean,
        activeLibrary: ConversationSkillLibrary,
        inactiveLibrary: ConversationSkillLibrary,
    ): PairTrial {
        val selected = if (active) activeLibrary else inactiveLibrary
        val prepared = ConversationPreparation.supervised().withSkillSnapshot(selected.snapshot())
        val prompt = ConversationInstructionBuilder.build(
            prepared, "Prowadz neutralny dialog testowy. Nie wykonuj narzedzi.",
            ConversationResponseFormat.TOOL_REPLY)
        assertEquals("compiled_skill_marker_in_wrong_state", active, prompt.contains(marker))
        val metrics = CopyOnWriteArrayList<NativeModelTiming>()
        val backend = Gemma4LiteRtTextBackendFactory.createConversationReply(
            context = app,
            systemInstruction = prompt,
            modelId = model,
            tools = setOf(ConversationToolId.END_CALL),
            contextTokenBudget = 2_048,
            onTiming = { metrics.add(it) },
        )
        try {
            val turns = listOf(
                generate(backend, "Zapamietaj fikcyjny kod testowy " + factCode +
                    ". Powiedz jedno krotkie zdanie o pogodzie.", metrics),
                generate(backend, "Przypomnij dokladnie fikcyjny kod z poprzedniej tury.", metrics),
            )
            return PairTrial(model, active, turns)
        } finally {
            backend.close()
            Thread.sleep(800)
        }
    }

    @Test fun e2bVersusE4BRealNativeAnswerQualityAndLatency() {
        val savedProfile = AudioEngineProfileStore(app).load()
        assertNotNull("audio_engine_profile_unreadable", savedProfile)
        for (m in models) {
            assertEquals("model_not_installed_or_size_invalid_" + m,
                Gemma4ModelReadinessState.READY, AndroidGemma4ModelReadiness.check(app, m).state)
        }
        val edited = ConversationSkillLibrary.defaults().copyActive("Isolated synthetic A/B")
            .reviseSkill("ConversationStyle",
                "Kazda wypowiedz musi zawierac dokladne slowo " + marker +
                    ". To neutralna instrukcja stylu i nie rozszerza zadania.")
        val off = edited.bindVersion("ConversationStyle", null)
        val results = mutableListOf<PairTrial>()
        for (model in models) {
            for (enabled in listOf(true, false)) {
                val t = trial(model, enabled, edited, off)
                results.add(t)
                val markerAll = t.turns.all { it.parsed.text.contains(marker, ignoreCase = true) }
                val markerAny = t.turns.any { it.parsed.text.contains(marker, ignoreCase = true) }
                val recall = t.turns.last().parsed.text.contains(factCode, ignoreCase = true)
                val validTools = t.turns.all { it.parsed.tool == null }
                val cold = t.turns.first().timing
                val warm = t.turns.last().timing
                val metrics = "model=" + model.name + ";skill_enabled=" + enabled +
                    ";turns=" + t.turns.size +
                    ";marker_any=" + markerAny + ";marker_all=" + markerAll +
                    ";fact_recall=" + recall + ";tools_none=" + validTools +
                    ";cold_wall_ms=" + t.turns.first().wallMs +
                    ";warm_wall_ms=" + t.turns.last().wallMs +
                    ";cold_engine_ms=" + (cold?.engineReadyMs ?: -1) +
                    ";cold_conv_ms=" + (cold?.conversationCreateMs ?: -1) +
                    ";cold_generate_ms=" + (cold?.generateMs ?: -1) +
                    ";warm_generate_ms=" + (warm?.generateMs ?: -1) +
                    ";backend=" + (warm?.backend ?: cold?.backend ?: "UNKNOWN") +
                    ";output_chars=" + t.turns.sumOf { it.parsed.text.length } +
                    ";context_tokens=2048;" + memSnapshot() + ";no_call=true"
                Log.i("AiCallModelCompare", metrics)
                println("MODEL_DIFF_SAFE=" + metrics)
            }
        }
        assertEquals("unexpected_trial_count", 4, results.size)
        assertEquals("user_engine_selection_mutated", savedProfile, AudioEngineProfileStore(app).load())
        println("MODEL_DIFF_TECHNICAL=PASS_4_SESSIONS_8_TURNS;SEMANTIC_HEURISTICS_SEPARATE")
    }
}
