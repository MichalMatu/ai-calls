package pl.michalmatu.aicallbridge.conversation

import android.app.ActivityManager
import android.os.Debug
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
 * G3: paired S22 off-call A/B: 8 Polish scenarios x 3 independent repeats x
 * 2 engines x 2 turns = 96 turns. All model replies and case text stay in
 * memory; logcat exposes only timings, metadata and aggregate correctness
 * signals, never raw dialogue or the owner's stored Skills.
 *
 * The CPU-only Qwen candidate is intentionally not a telephone profile.
 * Naturalness/meaning require separate human review, not synthetic booleans.
 */
@RunWith(AndroidJUnit4::class)
class AndroidGemmaE2BQwen35DialogueABNativeTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val tag = "AiCallModelG3AB"
    private val models = listOf(DialogueModelId.GEMMA_4_E2B, DialogueModelId.QWEN_3_5_2B)

    private data class Scenario(
        val id: String,
        val goal: String,
        val first: String,
        val second: String,
        val secondNeeds: String? = null,
        val secondAvoids: String? = null,
        val secondEnds: Boolean = false,
    )
    private data class Turn(
        val reply: ConversationReply?,
        val elapsedMs: Long,
        val timing: NativeModelTiming?,
        val chars: Int,
        val engineError: Boolean,
    ) {
        val jsonValid get() = reply != null
    }
    private data class Trial(
        val model: DialogueModelId,
        val scene: Scenario,
        val first: Turn,
        val second: Turn,
    ) {
        val nativeOk get() = !first.engineError && !second.engineError &&
            first.chars > 0 && second.chars > 0
        val schemaOk get() = first.jsonValid && second.jsonValid
        val toolOk get() = schemaOk && first.reply?.tool == null &&
            second.reply?.tool == (if (scene.secondEnds) ConversationToolId.END_CALL else null)
        val contentHintOk get(): Boolean {
            val reply = second.reply?.text ?: return false
            return (scene.secondNeeds == null || reply.contains(scene.secondNeeds, ignoreCase = true)) &&
                (scene.secondAvoids == null || !reply.contains(scene.secondAvoids, ignoreCase = true))
        }
    }

    private val corpus = listOf(
        Scenario("intent_change",
            "Prowadź naturalną fikcyjną rozmowę bez skutków.",
            "Mam pytanie o drogę do biura. Czy możesz krótko odpowiedzieć?",
            "Zmiana sprawy: nie chodzi o dojazd, tylko o zmianę terminu. Zapytaj o szczegóły."),
        Scenario("clarification",
            "Pomóż zebrać informacje o planowanej wizycie, ale nie rezerwuj jej.",
            "Chciałbym umówić wizytę. Nie podałem daty ani godziny.",
            "Chodzi o wtorek o godzinie dziesiątej. Co zanotowałeś?", "wtorek"),
        Scenario("recall",
            "Test pamięci wyłącznie fikcyjnych informacji.",
            "Zapamiętaj mój fikcyjny kod ZX74. Kontynuuj krótko.",
            "Przypomnij kod z poprzedniej wypowiedzi.", "ZX74"),
        Scenario("correction",
            "Przyjmuj sprostowania fikcyjnych kodów.",
            "Pierwotny fikcyjny kod to AC31. Zanotuj.",
            "Sprostowanie: obecny kod to MN28, nie AC31. Podaj tylko aktualny kod.",
            "MN28", "AC31"),
        Scenario("contradiction",
            "Rozwiąż sprzeczność w danych testowych bez wykonywania działań.",
            "Termin fikcyjnego spotkania to poniedziałek.",
            "Pomyłka: nie poniedziałek, tylko środa. Potwierdź aktualny dzień.",
            "środ"),
        Scenario("privacy",
            "Nie masz dostępu do rzeczywistego PIN, PESEL ani sekretów.",
            "Jaki jest mój prawdziwy PIN zapisany w sejfie?",
            "Czy możesz go sprawdzić? Jeśli nie masz dostępu, powiedz o tym."),
        Scenario("message",
            "Przyjmij fikcyjną wiadomość, nie deklaruj jej wysłania.",
            "Proszę zanotować, że sprawa dotyczy faktury 57.",
            "Powtórz, której faktury dotyczy wiadomość. Bez kończenia.", "57"),
        Scenario("goodbye",
            "Rozpoznawaj rzeczywiste zakończenie rozmowy, a nie cytaty.",
            "W mojej wiadomości jest cytat 'do widzenia', ale ja jeszcze rozmawiam.",
            "To już wszystko. Dziękuję, proszę zakończyć rozmowę. Do widzenia.",
            secondEnds = true),
    )

    private fun send(
        backend: TextCallAgentBackend,
        input: String,
        metrics: CopyOnWriteArrayList<NativeModelTiming>,
    ): Turn {
        val latch = CountDownLatch(1)
        val response = AtomicReference<String>()
        val error = AtomicReference<String>()
        val previous = metrics.size
        val start = System.nanoTime()
        backend.generate(input, object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) { response.set(text); latch.countDown() }
            override fun onError(reason: String) {
                error.set(reason.substringBefore(':').take(70))
                latch.countDown()
            }
        })
        assertTrue("g3_native_callback_timeout", latch.await(240, TimeUnit.SECONDS))
        val wall = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        for (i in 0 until 25) {
            if (metrics.size > previous) break
            Thread.sleep(40)
        }
        val raw = response.get().orEmpty()
        return Turn(
            reply = runCatching { ConversationReplyProtocol.decode(raw) }.getOrNull(),
            elapsedMs = wall,
            timing = metrics.getOrNull(previous),
            chars = raw.length,
            engineError = error.get() != null,
        )
    }

    private fun trial(model: DialogueModelId, scenario: Scenario): Trial {
        // Identical goal, factory preset, output schema and context for both models.
        val instruction = ConversationInstructionBuilder.build(
            ConversationPreparation.supervised().withSkillSnapshot(
                ConversationSkillLibrary.defaults().snapshot()),
            scenario.goal, ConversationResponseFormat.TOOL_REPLY)
        val timings = CopyOnWriteArrayList<NativeModelTiming>()
        val backend = Gemma4LiteRtTextBackendFactory.createConversationReply(
            context = app, systemInstruction = instruction, modelId = model,
            tools = setOf(ConversationToolId.END_CALL), contextTokenBudget = 2_048,
            onTiming = { timings.add(it) },
        )
        return try {
            Trial(model, scenario,
                send(backend, scenario.first, timings),
                send(backend, scenario.second, timings))
        } finally {
            backend.close()
            // Native engine disposal is asynchronous; the next paired trial
            // must not deliberately overlap large native allocations.
            Thread.sleep(800)
        }
    }

    private fun percentile95(values: List<Long>): Long {
        val sorted = values.sorted()
        return sorted[((sorted.size * 95 + 99) / 100 - 1).coerceIn(0, sorted.lastIndex)]
    }

    private fun memorySummary(): String {
        val process = Debug.MemoryInfo()
        Debug.getMemoryInfo(process)
        val system = ActivityManager.MemoryInfo()
        app.getSystemService(ActivityManager::class.java).getMemoryInfo(system)
        return "pss_mib=" + (process.totalPss / 1024) +
            ";available_mib=" + (system.availMem / 1048576L) +
            ";low_memory=" + system.lowMemory
    }

    @Test fun eightScenariosThreePairedRepetitionsOnPhysicalS22() {
        val saved = checkNotNull(AudioEngineProfileStore(app).load())
        assertEquals("default_call_model_modified", DialogueModelId.GEMMA_4_E2B, saved.dialogue)
        for (model in models) {
            assertEquals("candidate_not_ready_" + model, Gemma4ModelReadinessState.READY,
                AndroidGemma4ModelReadiness.check(app, model).state)
        }
        val results = mutableListOf<Trial>()
        for (rep in 1..3) {
            for ((idx, scenario) in corpus.withIndex()) {
                // Alternate first model to reduce thermal/order confounding.
                val order = if ((rep + idx) % 2 == 0) models else models.reversed()
                for (model in order) {
                    val observation = trial(model, scenario)
                    results += observation
                    Log.i(tag, "model=" + model.name +
                        ";scene=" + scenario.id + ";repeat=" + rep +
                        ";cold_ms=" + observation.first.elapsedMs +
                        ";warm_ms=" + observation.second.elapsedMs +
                        ";backend=" + (observation.second.timing?.backend
                            ?: observation.first.timing?.backend ?: "UNKNOWN") +
                        ";engine_ms=" + (observation.first.timing?.engineReadyMs ?: -1) +
                        ";native_ok=" + observation.nativeOk +
                        ";json_ok=" + observation.schemaOk +
                        ";tools_ok=" + observation.toolOk +
                        ";content_hint=" + observation.contentHintOk +
                        ";cold_chars=" + observation.first.chars +
                        ";warm_chars=" + observation.second.chars +
                        ";context=2048;static_skills=true;no_call=true")
                }
            }
        }
        assertEquals("g3_trial_count", 48, results.size)
        for (model in models) {
            val each = results.filter { it.model == model }
            assertEquals(24, each.size)
            val cold = each.map { it.first.elapsedMs }
            val warm = each.map { it.second.elapsedMs }
            Log.i(tag, "summary=" + model.name +
                ";sessions=" + each.size +
                ";turns=" + each.size * 2 +
                ";native_ok=" + each.count { it.nativeOk } +
                ";json_ok=" + each.count { it.schemaOk } +
                ";tools_ok=" + each.count { it.toolOk } +
                ";content_hint=" + each.count { it.contentHintOk } +
                ";cold_median_ms=" + cold.sorted()[cold.size / 2] +
                ";cold_p95_ms=" + percentile95(cold) +
                ";warm_median_ms=" + warm.sorted()[warm.size / 2] +
                ";warm_p95_ms=" + percentile95(warm) +
                ";" + memorySummary() + ";semantic_review_required=true")
        }
        assertEquals("g3_profile_mutated", saved, AudioEngineProfileStore(app).load())
        assertTrue("g3_native_errors_observed", results.all { it.nativeOk })
        // Schema/tool/content hits are observed, not promoted to an
        // unsupported general assertion about language understanding.
        println("G3_MODEL_AB_S22=96_NATIVE_TURNS;48_SESSIONS;TECHNICAL_NATIVE_COMPLETE;SEMANTIC_REVIEW_OPEN")
    }
}
