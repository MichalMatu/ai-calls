package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class PhoneTaskQueueTest {
    private fun id() = UUID.randomUUID().toString()
    private fun task(count: Int = 3, start: Long = 1_000, end: Long = 1_000_000, retry: Long? = 60_000) = PhoneTask(
        id(), "Cena wymiany", "quote", "Sprawdź cenę wymiany oleju", "Zapytaj o cenę z częściami i terminy.",
        1, "sim-test", start, end, "Europe/Warsaw", retry, 100,
        (1..count).map { PhoneTaskRecipient(id(), "Workshop $it", "+1202555010$it") },
    ).validated()

    @Test fun `two identical wakeups cannot reserve two calls`() {
        val first = requireNotNull(PhoneTaskQueue.claim(listOf(task()), 1_000, id()))
        assertNull(PhoneTaskQueue.claim(listOf(first.task), 1_000, id()))
        assertNull(PhoneTaskQueue.claim(listOf(first.task, task()), 1_000, id()))
    }
    @Test fun `no early call and exact window end never starts next call`() {
        val task = task()
        assertNull(PhoneTaskQueue.claim(listOf(task), 999, id()))
        assertNotNull(PhoneTaskQueue.claim(listOf(task), 1_000, id()))
        assertNull(PhoneTaskQueue.claim(listOf(task), task.endAt, id()))
        val expired = PhoneTaskQueue.settle(task, task.endAt)
        assertTrue(expired.recipients.all { it.state == PhoneRecipientState.EXPIRED })
        assertEquals(PhoneTaskState.FINISHED, expired.state)
    }
    @Test fun `busy recipient moves behind remaining recipients until its retry time`() {
        val first = requireNotNull(PhoneTaskQueue.claim(listOf(task()), 1_000, id()))
        val complete = PhoneTaskQueue.complete(first.task, first.attemptId, PhoneAttemptOutcome.BUSY, 2_000)
        assertEquals(62_000, complete.recipients[0].nextAt)
        val next = requireNotNull(PhoneTaskQueue.claim(listOf(complete), 2_000, id()))
        assertEquals(complete.recipients[1].id, next.recipientId)
    }
    @Test fun `no artificial attempt quota while owner window is valid`() {
        var task = task(count = 1, end = 1_000_000_000, retry = 1)
        repeat(300) { index ->
            val claim = requireNotNull(PhoneTaskQueue.claim(listOf(task), 1_000L + index * 2, id()))
            task = PhoneTaskQueue.complete(claim.task, claim.attemptId, PhoneAttemptOutcome.BUSY, 1_001L + index * 2)
        }
        assertEquals(300, task.recipients.single().attempts.size)
        assertEquals(PhoneTaskState.READY, task.state)
    }
    @Test fun `retry cannot cross window and disabled retry is respected`() {
        listOf(task(count = 1, end = 30_000), task(count = 1, retry = null)).forEach { task ->
            val claim = requireNotNull(PhoneTaskQueue.claim(listOf(task), 1_000, id()))
            val next = PhoneTaskQueue.complete(claim.task, claim.attemptId, PhoneAttemptOutcome.BUSY, 2_000)
            assertNull(PhoneTaskQueue.nextAt(next))
            assertEquals(PhoneAttemptOutcome.BUSY, next.recipients.single().attempts.single().outcome)
        }
    }
    @Test fun `uncertain dial never automatically retries even with retry enabled`() {
        val claim = requireNotNull(PhoneTaskQueue.claim(listOf(task(1)), 1_000, id()))
        val next = PhoneTaskQueue.complete(claim.task, claim.attemptId, PhoneAttemptOutcome.UNKNOWN, 2_000)
        assertEquals(PhoneRecipientState.UNKNOWN, next.recipients.single().state)
        assertNull(PhoneTaskQueue.claim(listOf(next), 80_000, id()))
    }
    @Test fun `process death preserves completed replies and pauses unknown inflight recipient`() {
        val claim = requireNotNull(PhoneTaskQueue.claim(listOf(task()), 1_000, id()))
        val talking = PhoneTaskQueue.progress(claim.task, claim.attemptId, PhoneAttemptStage.TALKING,
            "synthetic-call", PhoneTaskAnswer(1, "Cena od 350 zł, bez części."))
        val recovered = PhoneTaskQueue.recover(listOf(talking), 2_000).single()
        assertEquals(PhoneTaskState.PAUSED, recovered.state)
        assertEquals(PhoneTaskIssue.INTERRUPTED, recovered.issue)
        assertEquals("Cena od 350 zł, bez części.", recovered.recipients[0].attempts[0].answers[0].text)
        assertEquals(PhoneAttemptOutcome.UNKNOWN, recovered.recipients[0].attempts[0].outcome)
        assertNull(PhoneTaskQueue.claim(listOf(recovered), 3_000, id()))
        val resumed = requireNotNull(PhoneTaskQueue.claim(listOf(recovered.copy(state = PhoneTaskState.READY)), 3_000, id()))
        assertEquals(recovered.recipients[1].id, resumed.recipientId)
    }
    @Test fun `cancel fences late result and keeps future recipients uncalled`() {
        val claim = requireNotNull(PhoneTaskQueue.claim(listOf(task()), 1_000, id()))
        val cancelled = PhoneTaskQueue.interrupt(claim.task, 2_000, true, null)
        assertEquals(cancelled, PhoneTaskQueue.complete(cancelled, claim.attemptId, PhoneAttemptOutcome.BUSY, 3_000))
        assertEquals(cancelled, PhoneTaskQueue.progress(cancelled, claim.attemptId, PhoneAttemptStage.TALKING,
            "late", PhoneTaskAnswer(2, "Late reply")))
        assertNull(PhoneTaskQueue.claim(listOf(cancelled), 4_000, id()))
    }
    @Test fun `takeover does not silently rearm current recipient`() {
        val claim = requireNotNull(PhoneTaskQueue.claim(listOf(task()), 1_000, id()))
        val dialing = PhoneTaskQueue.progress(claim.task, claim.attemptId, PhoneAttemptStage.DIALING)
        val paused = PhoneTaskQueue.interrupt(dialing, 2_000, false, PhoneTaskIssue.TAKEOVER)
        val next = requireNotNull(PhoneTaskQueue.claim(listOf(paused.copy(state = PhoneTaskState.READY)), 3_000, id()))
        assertNotEquals(claim.recipientId, next.recipientId)
    }
    @Test fun `interruption before dial preserves recipient for safe explicit resume`() {
        val claim = requireNotNull(PhoneTaskQueue.claim(listOf(task()), 1_000, id()))
        val paused = PhoneTaskQueue.interrupt(claim.task, 2_000, false, PhoneTaskIssue.INTERRUPTED)
        val next = requireNotNull(PhoneTaskQueue.claim(listOf(paused.copy(state = PhoneTaskState.READY)), 3_000, id()))
        assertEquals(claim.recipientId, next.recipientId)
    }
    @Test fun `answered recipient is not retried on a later transport failure`() {
        val claim = requireNotNull(PhoneTaskQueue.claim(listOf(task(1)), 1_000, id()))
        val withAnswer = PhoneTaskQueue.progress(claim.task, claim.attemptId, PhoneAttemptStage.TALKING,
            "test", PhoneTaskAnswer(1, "Nie wykonujemy tej usługi."))
        val next = PhoneTaskQueue.complete(withAnswer, claim.attemptId, PhoneAttemptOutcome.UNKNOWN, 2_000)
        assertEquals(PhoneRecipientState.ANSWERED, next.recipients.single().state)
        assertNull(PhoneTaskQueue.nextAt(next))
    }
    @Test fun `a pre-dial failure pauses without consuming the recipient`() {
        val claim = requireNotNull(PhoneTaskQueue.claim(listOf(task(1)), 1_000, id()))
        val next = PhoneTaskQueue.complete(claim.task, claim.attemptId, PhoneAttemptOutcome.BLOCKED, 2_000, PhoneTaskIssue.MODEL)
        assertEquals(PhoneTaskState.PAUSED, next.state)
        assertEquals(PhoneRecipientState.QUEUED, next.recipients.single().state)
    }
    @Test fun `phone becoming busy during model warmup waits without asking owner again`() {
        val claim = requireNotNull(PhoneTaskQueue.claim(listOf(task(1)), 1_000, id()))
        val next = PhoneTaskQueue.complete(claim.task, claim.attemptId, PhoneAttemptOutcome.BLOCKED,
            2_000, PhoneTaskIssue.PHONE_BUSY)
        assertEquals(PhoneTaskState.READY, next.state)
        assertEquals(32_000L, PhoneTaskQueue.nextAt(next))
        assertNull(PhoneTaskQueue.claim(listOf(next), 2_001, id()))
        assertNotNull(PhoneTaskQueue.claim(listOf(next), 32_000, id()))
    }
    @Test fun `each recipient gets exact fresh single-target authorization`() {
        val task = task()
        val outgoing = task.scenarioTask(task.recipients[0], id())
        val grant = outgoing.grant(1_000)
        assertTrue(CampaignAuthorizationPolicy.matchesExactTask(grant, outgoing.request()))
        assertFalse(CampaignAuthorizationPolicy.matchesExactTask(grant,
            task.scenarioTask(task.recipients[1], id()).request()))
        assertTrue(grant.allowedDisclosureFields.isEmpty())
        assertFalse(grant.recordCallEvidence)
    }
    @Test fun `duplicate callback does not add duplicate evidence or replace completed outcome`() {
        val claim = requireNotNull(PhoneTaskQueue.claim(listOf(task(1)), 1_000, id()))
        val answer = PhoneTaskAnswer(1, "Cena od 350 zł")
        val first = PhoneTaskQueue.progress(claim.task, claim.attemptId, PhoneAttemptStage.TALKING, "test", answer)
        assertEquals(first, PhoneTaskQueue.progress(first, claim.attemptId, PhoneAttemptStage.TALKING, "test", answer))
        val done = PhoneTaskQueue.complete(first, claim.attemptId, PhoneAttemptOutcome.ANSWERED, 2_000)
        assertEquals(done, PhoneTaskQueue.complete(done, claim.attemptId, PhoneAttemptOutcome.UNKNOWN, 3_000))
    }
    @Test fun `previous attempt callback cannot alter the next recipient`() {
        val first = requireNotNull(PhoneTaskQueue.claim(listOf(task()), 1_000, id()))
        val ended = PhoneTaskQueue.complete(first.task, first.attemptId, PhoneAttemptOutcome.UNKNOWN, 2_000)
        val second = requireNotNull(PhoneTaskQueue.claim(listOf(ended), 3_000, id()))
        assertEquals(second.task, PhoneTaskQueue.progress(second.task, first.attemptId,
            PhoneAttemptStage.TALKING, "late-call", PhoneTaskAnswer(1, "Late quote")))
        assertEquals(second.task, PhoneTaskQueue.complete(second.task, first.attemptId,
            PhoneAttemptOutcome.ANSWERED, 4_000))
    }
    @Test fun `phone normalization deduplicates formatting but never guesses country`() {
        val input = PhoneTaskInput.recipients("Garage; +1 (202) 555-0101\n0012025550101\n+12025550102")
        assertEquals(2, input.values.size)
        assertEquals(1, input.duplicates)
        assertEquals("Garage", input.values.first().name)
        listOf("2025550101", "tel:+12025550101", "+12025550101, +12025550102", "+12025550101;extra;bad").forEach {
            assertTrue(runCatching { PhoneTaskInput.recipients(it) }.isFailure)
        }
    }
    @Test fun `DST gap and overlap are not silently shifted and chosen zone is pinned`() {
        val zone = ZoneId.of("Europe/Warsaw")
        listOf("2027-03-28T02:30", "2027-10-31T02:30").forEach {
            assertTrue(runCatching { PhoneTaskInput.instant(LocalDateTime.parse(it), zone) }.isFailure)
        }
        val chosen = PhoneTaskInput.instant(LocalDateTime.parse("2027-01-12T08:00"), zone)
        assertEquals(java.time.Instant.parse("2027-01-12T07:00:00Z").toEpochMilli(), chosen)
    }
    @Test fun `comparison keeps lower bound range negation and missing data literal`() {
        val answers = listOf(PhoneTaskAnswer(1, "Cena od 350 do 500 zł, bez części."),
            PhoneTaskAnswer(2, "Nie mamy wolnego terminu w piątek."),
            PhoneTaskAnswer(3, "Wymagana zaliczka."))
        val result = PhoneTaskResults.comparison(answers)
        assertEquals(listOf(answers[0].text), result.price)
        assertEquals(listOf(answers[1].text), result.availability)
        assertEquals(listOf(answers[2].text), result.terms)
        assertTrue(PhoneTaskResults.comparison(emptyList()).price.isEmpty())
    }
    @Test fun `restart roundtrip durable claim blocks duplicate and failed persistence never yields claim`() {
        var raw: String? = null
        var repository = PhoneTaskRepository({ raw }, { raw = it })
        repository.update { listOf(task()) }
        val claim = requireNotNull(PhoneTaskQueue.claim(repository.load(), 1_000, id()))
        repository.update { listOf(claim.task) }
        repository = PhoneTaskRepository({ raw }, { raw = it })
        assertNull(PhoneTaskQueue.claim(repository.load(), 1_000, id()))
        val failing = PhoneTaskRepository({ raw }, { error("disk_full") })
        assertTrue(runCatching { failing.update { PhoneTaskQueue.recover(it, 2_000) } }.isFailure)
        assertEquals(PhoneRecipientState.IN_FLIGHT, repository.load().single().recipients[0].state)
    }
    @Test fun `unknown schema or corrupt storage never becomes empty queue`() {
        listOf("bad json", "{\"schema\":99,\"tasks\":[]}", "{\"schema\":1,\"tasks\":[{}]}").forEach { raw ->
            assertTrue(PhoneTaskRepository({ raw }, {}).run { runCatching { load() }.isFailure })
        }
    }
}
