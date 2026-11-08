package pl.michalmatu.aicallbridge

import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.conversation.AndroidSkillLabSessionFactory
import pl.michalmatu.aicallbridge.conversation.SkillLabPhase
import pl.michalmatu.aicallbridge.conversation.SkillLabRunner
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness

/**
 * One off-call, real native session, inspected with a randomized synthetic fact.
 * This is NOT a canned caller story or a replacement for human factual review.
 *
 * Raw answers are never sent to logcat, instrumentation stdout or GitHub:
 * they are kept only in a newly named private no-backup file on the S22.
 * The instrumentation receipt contains limited checks and response timings only.
 */
@RunWith(AndroidJUnit4::class)
class AndroidLabSemanticNativeOfflineTest {
    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(140)
        return condition()
    }

    @Test fun oneNativeSessionCanBeExaminedAcrossTopicSwitchCorrectionAndUnknownFact() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val profile = requireNotNull(AudioEngineProfileStore(app).load()) {
            "semantic_profile_missing"
        }
        val readiness = AndroidGemma4ModelReadiness.check(app, profile.dialogue)
        assertEquals("semantic_model_not_ready", "READY", readiness.state.toString())

        // Synthetic runtime-only fact prevents accidental reuse of a memorized fixture.
        val code = "PX" + UUID.randomUUID().toString().replace("-", "")
            .take(6).uppercase(Locale.ROOT)
        val questions = listOf(
            "To wyłącznie test tekstowy. Zapamiętaj kod próbki: $code. Jej kolor to zielony. Potwierdź krótko.",
            "Zmieńmy temat: podaj nazwę dowolnej planety Układu Słonecznego.",
            "Wracamy do próbki. Jaki kod podałem na początku? Podaj tylko kod.",
            "Sprostowanie: kolor próbki jest teraz fioletowy, nie zielony. Krótko potwierdź.",
            "Wróć do próbki. Podaj jej kod oraz aktualny kolor, bez opisu historii zmian.",
            "Jaki był numer przesyłki tej próbki? Nigdy go nie podałem. Nie zgaduj; powiedz, gdy brak danych.",
            "Podsumuj aktualne dane próbki, podając kod i kolor. Nie dodawaj innych faktów.",
        )
        val runner = SkillLabRunner(AndroidSkillLabSessionFactory(app),
            dispatchToUi = { it() }, onState = {})
        try {
            assertTrue("semantic_manual_start_rejected", runner.startManual("natural"))
            assertTrue("semantic_model_init_timeout", await(170_000) {
                runner.state.phase == SkillLabPhase.INPUT_READY ||
                    runner.state.phase == SkillLabPhase.FAILED
            })
            assertEquals("semantic_model_init_failed", SkillLabPhase.INPUT_READY,
                runner.state.phase)
            for ((index, question) in questions.withIndex()) {
                assertTrue("semantic_submit_rejected_at_${index + 1}",
                    runner.submitManual(question))
                assertTrue("semantic_turn_timeout_${index + 1}", await(175_000) {
                    runner.state.completed.size == index + 1 ||
                        runner.state.phase == SkillLabPhase.FAILED
                })
                assertEquals("semantic_failed_turn_${index + 1}",
                    SkillLabPhase.INPUT_READY, runner.state.phase)
                assertEquals(index + 1, runner.state.completed.size)
                assertTrue("semantic_blank_response_${index + 1}",
                    runner.state.completed.last().observation.reply.isNotBlank())
            }
            val turns = runner.state.completed
            val replies = turns.map { it.observation.reply.lowercase(Locale.ROOT) }
            val topic = listOf("merkury", "wenus", "ziemia", "ziemi", "mars", "jowisz",
                "saturn", "uran", "neptun").any { it in replies[1] }
            val memory = code.lowercase(Locale.ROOT) in replies[2]
            val corrected = "fioletow" in replies[4] &&
                "zielon" !in replies[4]
            val memoryAfterCorrection = code.lowercase(Locale.ROOT) in replies[4]
            val retained = code.lowercase(Locale.ROOT) in replies[6] &&
                "fioletow" in replies[6] && "zielon" !in replies[6]
            val unknownReply = java.text.Normalizer.normalize(replies[5],
                java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace('ł', 'l')
            val uncertain = Regex(
                "\\b(?:brak|niewiadom\\w*|nieznan\\w*|nie\\s+" +
                    "(?:wiem|znam|mam|mamy|ma|posiadam|moge|potrafie|podano|" +
                    "podal(?:es|as)?|zostal|byl|jest))\\b",
            ).containsMatchIn(unknownReply)
            // An uncertainty disclaimer alongside a fabricated number is not a pass.
            val unknown = uncertain &&
                !Regex("\\b\\d{3,}\\b").containsMatchIn(unknownReply)
            val ended = turns.count { it.observation.actualEnd }
            val averageMs = turns.map { it.observation.elapsedMs }.average().toLong()

            // No display/logging of answers. Local private evidence is not synced to Git.
            val privateTurns = JSONArray()
            for (index in questions.indices) {
                privateTurns.put(JSONObject()
                    .put("turn", index + 1)
                    .put("user", questions[index])
                    .put("assistant", turns[index].observation.reply)
                    .put("elapsedMs", turns[index].observation.elapsedMs))
            }
            val evidence = JSONObject()
                .put("purpose", "offcall_semantic_probe")
                .put("modelProfile", profile.dialogue.toString())
                .put("turns", privateTurns)
                .put("limits", "single sample; no human semantic verdict")
            val evidenceName = "lab_semantic_probe_" +
                UUID.randomUUID().toString() + ".json"
            File(app.noBackupFilesDir, evidenceName).writeText(evidence.toString())
            val summary = "S22_NATIVE_SEMANTIC_PROBE=COMPLETE" +
                ";turns=" + turns.size +
                ";topic_change=" + topic +
                ";prior_fact_recall=" + memory +
                ";correction_applied=" + corrected +
                ";code_after_correction=" + memoryAfterCorrection +
                ";final_recall=" + retained +
                ";unknown_fact_cautious=" + unknown +
                ";end_proposals=" + ended +
                ";mean_latency_ms=" + averageMs +
                ";private_evidence_file=" + evidenceName
            Log.i("LAB_SEMANTIC_PROBE", summary)
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", summary + "\n") })
            assertEquals("semantic_session_turn_count", 7, turns.size)
            assertNull("native_probe_must_not_self_award_semantic_pass", runner.state.metrics)
        } finally {
            runner.close()
        }
    }
}
