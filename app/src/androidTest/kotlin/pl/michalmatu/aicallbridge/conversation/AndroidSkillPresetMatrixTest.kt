package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.incoming.IncomingSecretaryScenario
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** Strictly off-call; no Telecom, audio, user data or executed model tools. */
@RunWith(AndroidJUnit4::class)
class AndroidSkillPresetMatrixTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun aEncryptedEditVersionSwitchAndRollbackInIsolatedStore() {
        val unique = UUID.randomUUID().toString()
        val dir = "skill-qa-" + unique
        val key = "skill-qa-" + unique
        val legacy = "skill-qa-legacy-" + unique
        try {
            fun store() = AndroidConversationPreparationStore(context, dir, key, legacy)
            val factory = store().loadSkillLibrary()
            assertEquals(3, factory.presets.size)
            val custom = factory.copyActive("Synthetic QA")
                .reviseSkill("Clarification", "Synthetic-only QA instruction.")
            store().saveSkillLibrary(custom)
            val read = store().loadSkillLibrary()
            assertEquals(2, read.snapshot().entries.single { it.id == "Clarification" }.version)
            assertEquals(1, read.snapshot("natural").entries.single { it.id == "Clarification" }.version)
            store().saveSkillLibrary(read.bindVersion("NaturalClosure", null))
            assertFalse(store().loadSkillLibrary().snapshot().entries.any { it.id == "NaturalClosure" })
            store().saveSkillLibrary(store().loadSkillLibrary()
                .bindVersion("Clarification", 1).bindVersion("NaturalClosure", 1))
            val restored = store().loadSkillLibrary()
            assertEquals(4, restored.snapshot().entries.size)
            assertEquals(1, restored.snapshot().entries.single { it.id == "Clarification" }.version)
            assertEquals(2, restored.skills.single { it.id == "Clarification" }.revisions.size)
            val encrypted = File(context.noBackupFilesDir, dir).listFiles().orEmpty()
            assertTrue(encrypted.isNotEmpty())
            assertFalse(encrypted.any { it.readText().contains("Synthetic-only QA instruction.") })
            println("SKILL_QA_ENCRYPTION_VERSION_ROLLBACK=PASS")
        } finally {
            File(context.noBackupFilesDir, dir).deleteRecursively()
            context.getSharedPreferences(legacy, 0).edit().clear().commit()
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias(key)) deleteEntry(key)
            }
        }
    }

    @Test fun bSkillsAndLaboratoryScreensLaunchWithoutMutatingActivePreset() {
        val original = AndroidConversationPreparationStore(context).loadSkillLibrary().snapshot()
        ActivityScenario.launch(ConversationSkillsActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                val text = labels(activity.window.decorView).joinToString("|")
                assertTrue(text.contains("Skills i presety Gemmy"))
                assertTrue(text.contains("Utwórz własną kopię"))
                assertTrue(text.contains("Laboratorium skillsów"))
            }
        }
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                val text = labels(activity.window.decorView).joinToString("|")
                assertTrue(text.contains("Laboratorium skillsów"))
                assertTrue(text.contains("Uruchom testy wieloturowe"))
                assertTrue(text.contains("Ocena naturalności"))
            }
        }
        assertEquals(original, AndroidConversationPreparationStore(context).loadSkillLibrary().snapshot())
        println("SKILL_QA_UI=PASS_READONLY")
    }

    @Test fun cCompareThreePresetsOnSameOnDeviceModelAndCorpus() {
        val profile = checkNotNull(AudioEngineProfileStore(context).load()) { "no_model_profile" }
        val provider = DialogueEngineProviders.resolve(profile)
        val library = ConversationSkillLibrary.defaults()
        val corpus = SkillLabCorpus.stories
        val count = corpus.sumOf { it.turns.size }
        val report = JSONObject()
            .put("model", profile.dialogue.name)
            .put("synthetic_only", true)
            .put("telecom_calls", 0)
            .put("corpus_stories", corpus.size)
            .put("turns_per_preset", count)
        val runs = JSONArray()
        for (preset in listOf("natural", "formal", "message")) {
            val preparation = ConversationPreparation.incoming()
                .withSkillSnapshot(library.snapshot(preset))
            val instruction = ConversationInstructionBuilder.build(
                preparation, IncomingSecretaryScenario.GOAL, ConversationResponseFormat.TOOL_REPLY)
            val observations = mutableListOf<SkillLabObservation>()
            val turns = JSONArray()
            var initMs = 0L
            for (story in corpus) {
                val initStart = System.nanoTime()
                val backend = provider.createConversationReply(
                    context, instruction, setOf(ConversationToolId.END_CALL))
                initMs += (System.nanoTime() - initStart) / 1_000_000L
                try {
                    for ((idx, item) in story.turns.withIndex()) {
                        val start = System.nanoTime()
                        val response = AtomicReference<String?>()
                        val failure = AtomicReference<String?>()
                        val latch = CountDownLatch(1)
                        backend.generate(item.utterance, object : TextCallAgentBackend.Listener {
                            override fun onComplete(text: String) {
                                response.set(text); latch.countDown()
                            }
                            override fun onError(reason: String) {
                                failure.set(reason); latch.countDown()
                            }
                        })
                        assertTrue("offcall_timeout_" + preset, latch.await(120, TimeUnit.SECONDS))
                        assertNull("offcall_error_" + preset, failure.get())
                        val reply = ConversationReplyProtocol.decode(checkNotNull(response.get()))
                        val time = (System.nanoTime() - start) / 1_000_000L
                        val end = reply.tool == ConversationToolId.END_CALL
                        observations += SkillLabObservation(
                            story.title, idx + 1, item.shouldEnd, end,
                            reply.text, time, item.expectQuestion)
                        turns.put(JSONObject().put("story", story.title)
                            .put("turn", idx + 1)
                            .put("expected_end", item.shouldEnd)
                            .put("actual_end", end)
                            .put("latency_ms", time)
                            .put("reply_length", reply.text.length)
                            .put("question_mark", reply.text.contains("?")))
                    }
                } finally { backend.close() }
            }
            val metrics = SkillLabScoring.score(observations)
            assertEquals(count, metrics.turns)
            println("SKILL_MATRIX_PRESET=" + preset + " MODEL=" + profile.dialogue.name +
                " TURNS=" + metrics.turns + " ACCURATE=" + metrics.accurateEndDecisions +
                " PREMATURE=" + metrics.prematureEnds + " MISSING=" + metrics.missingEnds +
                " HEURISTIC_QUESTIONS=" + metrics.unexpectedQuestions +
                " MEAN_LATENCY_MS=" + metrics.meanLatencyMs + " INIT_MS=" + initMs)
            runs.put(JSONObject().put("preset", preset).put("turns", metrics.turns)
                .put("accurate_end", metrics.accurateEndDecisions)
                .put("premature_end", metrics.prematureEnds)
                .put("missing_end", metrics.missingEnds)
                .put("unexpected_questions_heuristic", metrics.unexpectedQuestions)
                .put("mean_generation_latency_ms", metrics.meanLatencyMs)
                .put("init_ms", initMs)
                .put("human_naturalness", JSONObject.NULL)
                .put("turn_metadata", turns))
        }
        report.put("presets", runs)
        val file = File(context.cacheDir, "skill-matrix-" + System.currentTimeMillis() + ".json")
        file.writeText(report.toString())
        println("SKILL_MATRIX_DONE MODEL=" + profile.dialogue.name +
            " PRESETS=" + runs.length() +
            " TURNS_EACH=" + count +
            " FILE=" + file.name +
            " HUMAN_NATURALNESS=NOT_RATED")
        assertEquals(3, runs.length())
    }

    private fun labels(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) repeat(view.childCount) { addAll(labels(view.getChildAt(it))) }
    }
}
