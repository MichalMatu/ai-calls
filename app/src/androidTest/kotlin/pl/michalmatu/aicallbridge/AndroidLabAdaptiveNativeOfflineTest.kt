package pl.michalmatu.aicallbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.conversation.AndroidSkillLabSessionFactory
import pl.michalmatu.aicallbridge.conversation.LabCaseRevision
import pl.michalmatu.aicallbridge.conversation.LabRole
import pl.michalmatu.aicallbridge.conversation.SkillLabPhase
import pl.michalmatu.aicallbridge.conversation.SkillLabRunner
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness

/**
 * Isolated real-model adaptive verification: subsequent test input is decided from the
 * generated response, not from a baked-in dialogue story. No storage or Telecom actions.
 */
@RunWith(AndroidJUnit4::class)
class AndroidLabAdaptiveNativeOfflineTest {
    private fun eventually(timeoutMillis: Long, predicate: () -> Boolean): Boolean {
        val limit = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!predicate() && System.nanoTime() < limit) Thread.sleep(130)
        return predicate()
    }

    @Test fun physicalGemmaResponseChangesAdaptiveBranchAndCompletesOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val profile = requireNotNull(AudioEngineProfileStore(context).load()) {
            "adaptive_profile_missing"
        }
        assertEquals("READY",
            AndroidGemma4ModelReadiness.check(context, profile.dialogue).state.toString())

        // Technical prompts are not user-facing stories and do not reintroduce the rejected corpus.
        val testCase = LabCaseRevision(
            revision = 1,
            title = "isolated native adaptive fixture",
            goal = "Prowadź neutralną rozmowę testową, krótko odpowiadaj po polsku.",
            role = LabRole.GENERAL,
            opening = "Przywitaj się jednym krótkim zdaniem. Możesz dopytać o cel rozmowy.",
            callerState = "",
            facts = "",
            expectations = "",
            rules = "question => Chcę sprawdzić działanie lokalnego dialogu.\n" +
                "otherwise => Czy pamiętasz, co właśnie powiedziałem?",
            completion = "Stop after applicable single-use rules",
        )
        val runner = SkillLabRunner(AndroidSkillLabSessionFactory(context),
            dispatchToUi = { it() }, onState = {})
        try {
            assertTrue(runner.startAdaptive("natural", testCase))
            assertTrue("adaptive_timeout_or_init_failed", eventually(480_000) {
                runner.state.phase == SkillLabPhase.COMPLETED ||
                    runner.state.phase == SkillLabPhase.FAILED
            })
            assertEquals("adaptive_model_failed:" + runner.state.error,
                SkillLabPhase.COMPLETED, runner.state.phase)
            val state = runner.state
            assertTrue("adaptive_two_turns_missing", state.completed.size in 2..3)
            assertEquals(state.completed.size - 1, state.adaptiveTrace.size)
            val firstReply = state.completed.first().observation.reply
            val chosen = state.adaptiveTrace.first()
            assertEquals("adaptive_branch_not_driven_by_model_text",
                if ('?' in firstReply) "question" else "otherwise", chosen.condition)
            assertTrue(state.completed.all { it.observation.reply.isNotBlank() })
            assertTrue(state.completed.all { it.observation.elapsedMs >= 0 })
            assertTrue("native_lab_must_not_claim_semantic_grade", state.metrics == null)
            println("S22_LAB_ADAPTIVE_NATIVE_COMPLETE_OFFCALL=PASS")
        } finally {
            runner.close()
        }
    }
}
