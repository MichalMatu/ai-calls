package pl.michalmatu.aicallbridge.conversation

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class SkillLabManualDialogueTest {
    private fun eventually(check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
        while (!check() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue("condition_not_reached", check())
    }

    private class FakeBackend(
        private val reply: (String) -> String = { "OK" },
        private val auto: Boolean = true,
    ) : TextCallAgentBackend {
        val inputs = Collections.synchronizedList(mutableListOf<String>())
        val closeCount = AtomicInteger()
        val cancelCount = AtomicInteger()
        @Volatile var callback: TextCallAgentBackend.Listener? = null
        val entered = CountDownLatch(1)
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            inputs.add(userText)
            callback = listener
            entered.countDown()
            if (auto) listener.onComplete(
                "{\"reply\":\"" + reply(userText) + "\",\"tool\":\"none\"}")
        }
        override fun cancel() { cancelCount.incrementAndGet() }
        override fun close() { closeCount.incrementAndGet() }
    }

    private val runners = mutableListOf<SkillLabRunner>()

    @org.junit.After fun closeRunners() = runners.forEach(SkillLabRunner::close)

    private fun runner(models: () -> FakeBackend): SkillLabRunner = SkillLabRunner(
        SkillLabModelSessionFactory { SkillLabModelSession { models() } },
        { it() }, {}).also(runners::add)

    @Test fun continuousConversationReusesOneBackendAcrossUnboundedTurns() {
        val opened = AtomicInteger()
        val model = FakeBackend()
        val subject = runner { opened.incrementAndGet(); model }
        assertTrue(subject.startManual("preset"))
        eventually { subject.state.phase == SkillLabPhase.INPUT_READY }
        for (n in 1..16) {
            assertTrue(subject.submitManual("utterance " + n))
            eventually {
                subject.state.phase == SkillLabPhase.INPUT_READY &&
                    subject.state.completed.size == n
            }
        }
        assertEquals(1, opened.get())
        assertEquals(16, model.inputs.size)
        assertEquals("utterance 16", model.inputs.last())
        assertEquals(16, subject.state.completed.size)
        assertFalse(subject.submitManual(" "))
        assertTrue(subject.stop())
        eventually { subject.state.manualStartAllowed }
        assertEquals(1, model.closeCount.get())
        subject.close()
    }

    @Test fun stopFencesInFlightCallbackAndAllowsFreshSession() {
        val first = FakeBackend(auto = false)
        val second = FakeBackend()
        val count = AtomicInteger()
        val subject = runner { if (count.incrementAndGet() == 1) first else second }
        assertTrue(subject.startManual("preset"))
        eventually { subject.state.phase == SkillLabPhase.INPUT_READY }
        assertTrue(subject.submitManual("first"))
        assertTrue(first.entered.await(3, TimeUnit.SECONDS))
        assertTrue(subject.stop())
        eventually { subject.state.manualStartAllowed }
        first.callback?.onComplete("""{"reply":"stale","tool":"none"}""")
        assertEquals(0, subject.state.completed.size)
        assertTrue(subject.startManual("preset"))
        eventually { subject.state.phase == SkillLabPhase.INPUT_READY }
        assertTrue(subject.submitManual("new"))
        eventually { subject.state.completed.size == 1 &&
            subject.state.phase == SkillLabPhase.INPUT_READY }
        assertEquals("new", subject.state.completed.single().pending.utterance)
        subject.close()
    }

    @Test fun adaptiveCallerRejectsManualInputEvenBetweenTurns() {
        val model = FakeBackend(reply = { "Which one?" })
        lateinit var subject: SkillLabRunner
        val attempted = AtomicInteger()
        val accepted = AtomicInteger()
        subject = SkillLabRunner(
            SkillLabModelSessionFactory { SkillLabModelSession { model } },
            { it() },
            { state ->
                if (state.phase == SkillLabPhase.INPUT_READY) {
                    attempted.incrementAndGet()
                    if (subject.submitManual("unexpected input")) accepted.incrementAndGet()
                }
            },
        ).also(runners::add)
        val test = LabCaseRevision(1, "Fixture", "Goal", LabRole.GENERAL,
            "Initial", "", "", "", "question => Updated input", "")
        assertTrue(subject.startAdaptive("preset", test))
        eventually { subject.state.phase == SkillLabPhase.COMPLETED && subject.state.manualStartAllowed }
        assertEquals(2, attempted.get())
        assertEquals(0, accepted.get())
        assertEquals(listOf("Initial", "Updated input"), model.inputs.toList())
    }

    @Test fun adaptiveCallerSelectsBranchFromActualResponseAndStopsAtExhaustion() {
        val model = FakeBackend(reply = { input ->
            if (input == "Initial") "Which one?" else "Noted."
        })
        val subject = runner { model }
        val test = LabCaseRevision(1, "Fixture", "Goal", LabRole.GENERAL,
            "Initial", "", "", "", "question => Updated input", "")
        assertTrue(subject.startAdaptive("preset", test))
        eventually { subject.state.phase == SkillLabPhase.COMPLETED &&
            subject.state.manualStartAllowed }
        assertEquals(listOf("Initial", "Updated input"), model.inputs.toList())
        assertEquals(2, subject.state.completed.size)
        assertEquals("question", subject.state.adaptiveTrace.single().condition)
        assertEquals(1, model.closeCount.get())
        subject.close()
    }
}
