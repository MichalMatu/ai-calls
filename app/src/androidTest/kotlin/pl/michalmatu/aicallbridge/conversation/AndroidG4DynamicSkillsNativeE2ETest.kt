package pl.michalmatu.aicallbridge.conversation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId

/**
 * G4.2 real E2B through production SkillLabRunner → session factory →
 * dynamic native instructions, using only synthetic off-call text.
 * Exercises repeated context changes and the actual Lab STOP lifecycle.
 */
@RunWith(AndroidJUnit4::class)
class AndroidG4DynamicSkillsNativeE2ETest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun ready(runner: SkillLabRunner, completed: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180)
        while (System.nanoTime() < deadline) {
            val state = runner.state
            if (state.phase == SkillLabPhase.FAILED) {
                fail("g4_lab_runner_failed:" + state.error)
            }
            if (state.phase == SkillLabPhase.INPUT_READY && state.completed.size == completed) return
            Thread.sleep(40)
        }
        fail("g4_lab_runner_timeout_completed_" + completed)
    }

    @Test fun runnerGemmaDynamicSkillsPreservesMemoryAndStops() {
        val profile = checkNotNull(AudioEngineProfileStore(app).load())
        assertEquals("g4_e2b_required", DialogueModelId.GEMMA_4_E2B, profile.dialogue)
        val factory = AndroidSkillLabSessionFactory(app, contextTokenBudget = 4_096)
        factory.skillLoadingMode = SkillLoadingMode.CONTEXTUAL
        val initialSelection = mutableListOf<String>()
        val actualBackends = mutableListOf<String>()
        factory.onSkillSelection = { initialSelection.addAll(it.selectedIds) }
        factory.onTiming = { actualBackends.add(it.backend) }
        val runner = SkillLabRunner(factory, { it() }, {},
            timeoutMillis = TimeUnit.SECONDS.toMillis(170))
        try {
            assertTrue("g4_lab_start", runner.startManual("natural"))
            ready(runner, 0)
            assertEquals(listOf("ConversationStyle", "NaturalClosure"), initialSelection)

            val requests = listOf(
                "Zapamiętaj mój fikcyjny kod ZX74. Nie jest to prawdziwy sekret.",
                "Zanotuj wiadomość testową o fakturze 57.",
                "Sprostowanie: faktura 58, nie 57. Jaki był mój fikcyjny kod?",
                "Zmienmy temat. Przypomnij mój fikcyjny kod.",
            )
            requests.forEachIndexed { idx, input ->
                assertTrue("g4_submit_" + idx, runner.submitManual(input))
                ready(runner, idx + 1)
            }

            val done = runner.state.completed
            assertEquals(4, done.size)
            assertTrue("g4_first_reply_missing", done[0].observation.reply.isNotBlank())
            assertTrue("g4_message_reply_missing", done[1].observation.reply.isNotBlank())
            val beforePivot = done[2].observation.reply.contains("ZX74", ignoreCase = true)
            val afterPivot = done[3].observation.reply.contains("ZX74", ignoreCase = true)
            Log.i("AiCallG4E2E", "f157_memory_before_pivot=" + beforePivot +
                ";f157_memory_after_pivot=" + afterPivot +
                ";turns_completed=" + done.size +
                ";native_timing_count=" + actualBackends.size + ";no_call=true")
            assertTrue("g4_memory_before_topic_change", beforePivot)
            assertTrue("g4_memory_after_topic_change", afterPivot)
            assertTrue("g4_runner_stop", runner.stop())
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (!runner.state.manualStartAllowed && System.nanoTime() < deadline) {
                Thread.sleep(20)
            }
            assertTrue("g4_stop_cleanup", runner.state.manualStartAllowed)
            assertEquals(SkillLabPhase.STOPPED, runner.state.phase)
            assertFalse("g4_stopped_runner_rejects_send", runner.submitManual("Nie wysyłaj"))
            Log.i("AiCallG4E2E", "model=E2B;native_runner=true;turns=4;" +
                "skills_refresh=3;memory_before_pivot=" + beforePivot +
                ";memory_after_pivot=" + afterPivot +
                ";backend_count=" + actualBackends.size +
                ";stop=true;no_call=true;profile_preserved=true")
        } finally {
            runner.close()
        }
        assertEquals(profile, AudioEngineProfileStore(app).load())
    }
}
