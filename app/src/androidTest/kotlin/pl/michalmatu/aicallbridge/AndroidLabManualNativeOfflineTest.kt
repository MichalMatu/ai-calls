package pl.michalmatu.aicallbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.conversation.AndroidSkillLabSessionFactory
import pl.michalmatu.aicallbridge.conversation.SkillLabPhase
import pl.michalmatu.aicallbridge.conversation.SkillLabRunner
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness

/**
 * Opt-in, real phone-local text model. No STT/TTS, Telecom, dialing or tool execution.
 * No canned dialogue stories: short randomized technical prompts exercise one native context.
 */
@RunWith(AndroidJUnit4::class)
class AndroidLabManualNativeOfflineTest {
    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(120)
        return condition()
    }

    @Test fun realLocalMultiTurnContextAndStopStayOffCall() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val profile = requireNotNull(AudioEngineProfileStore(app).load()) {
            "lab_manual_profile_missing"
        }
        val ready = AndroidGemma4ModelReadiness.check(app, profile.dialogue)
        assertEquals("lab_manual_model_not_ready", "READY", ready.state.toString())
        val runner = SkillLabRunner(AndroidSkillLabSessionFactory(app),
            dispatchToUi = { it() }, onState = {})
        try {
            assertTrue(runner.startManual("natural"))
            assertTrue("lab_model_init_failed", await(140_000) {
                runner.state.phase == SkillLabPhase.INPUT_READY ||
                    runner.state.phase == SkillLabPhase.FAILED
            })
            assertEquals("lab_first_ready", SkillLabPhase.INPUT_READY, runner.state.phase)
            val label = UUID.randomUUID().toString().take(8)
            assertTrue(runner.submitManual("W rozmowie testowej użyj nazwy " + label +
                " jako neutralnej etykiety. Odpowiedz jednym zdaniem."))
            assertTrue("lab_first_turn_failed", await(140_000) {
                runner.state.completed.size == 1 ||
                    runner.state.phase == SkillLabPhase.FAILED
            })
            assertEquals(SkillLabPhase.INPUT_READY, runner.state.phase)
            assertTrue(runner.submitManual("Powróć do nazwy z poprzedniej wypowiedzi. " +
                "Co zostało ustalone?"))
            assertTrue("lab_second_turn_failed", await(140_000) {
                runner.state.completed.size == 2 ||
                    runner.state.phase == SkillLabPhase.FAILED
            })
            assertEquals(SkillLabPhase.INPUT_READY, runner.state.phase)
            assertEquals(2, runner.state.completed.size)
            assertTrue(runner.state.completed.all { it.observation.reply.isNotBlank() })
            assertTrue(runner.state.completed.all { it.observation.elapsedMs >= 0 })
            // Semantic memory grading requires a separately reviewed transcript.
            assertNull(runner.state.metrics)
            assertTrue(runner.submitManual("Przygotuj krótkie podsumowanie."))
            assertTrue(runner.stop())
            assertTrue("lab_stop_cleanup_failed", await(50_000) {
                runner.state.manualStartAllowed
            })
            assertEquals(SkillLabPhase.STOPPED, runner.state.phase)
            assertEquals(2, runner.state.completed.size)
            assertTrue(runner.startManual("natural"))
            assertTrue("lab_restart_failed", await(140_000) {
                runner.state.phase == SkillLabPhase.INPUT_READY ||
                    runner.state.phase == SkillLabPhase.FAILED
            })
            assertEquals(SkillLabPhase.INPUT_READY, runner.state.phase)
            println("S22_LAB_MANUAL_REAL_TWO_TURNS_STOP_RESET=PASS_OFFCALL")
        } finally {
            runner.close()
        }
    }
}
