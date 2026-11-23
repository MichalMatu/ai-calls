package pl.michalmatu.aicallbridge.campaign

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PhoneTaskApprovalFactoryTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val now = LocalDateTime.of(2026, 11, 23, 12, 0)
        .atZone(zone).toInstant().toEpochMilli()

    private fun draft() = PhoneTaskDraft(
        title = "Synthetic campaign", goal = "Ask about service price",
        suppliedBrief = "Synthetic scope; no real call",
        recipients = "Client; +12025550111",
        subscriptionId = 1, accountScope = "test-sim",
        scheduled = true,
        startLocal = "2026-11-23T13:00",
        endLocal = "2026-11-23T14:00",
        zoneId = zone.id,
        retry = true, retryMinutes = "5",
        recordAudio = false,
    )

    @Test fun approvalProducesImmutableScopedTaskWithoutSideEffects() {
        val input = draft()
        val result = PhoneTaskApprovalFactory.build(input, input.suppliedBrief, now, "en")
        assertEquals(input.id, result.id)
        assertEquals(input.goal, result.goal)
        assertEquals(input.suppliedBrief, result.brief)
        assertEquals("en", result.language)
        assertEquals(300_000L, result.retryDelayMs)
        assertEquals(PhoneTaskState.READY, result.state)
        assertEquals(PhoneRecipientState.QUEUED, result.recipients.single().state)
        assertTrue(result.recipients.single().attempts.isEmpty())
        assertFalse(result.recordAudio)
    }

    @Test fun immediateTaskUsesApprovalInstantAndDoesNotRequireFutureStart() {
        val input = draft().copy(scheduled = false)
        val result = PhoneTaskApprovalFactory.build(input, input.suppliedBrief, now)
        assertEquals(now, result.startAt)
        assertNull(result.language)
    }

    @Test fun rejectsExpiredStartAndMissingAccount() {
        val badStart = draft().copy(startLocal = "2026-11-23T11:00")
        assertTrue(runCatching {
            PhoneTaskApprovalFactory.build(badStart, badStart.suppliedBrief, now)
        }.isFailure)
        val noAccount = draft().copy(accountScope = null)
        assertTrue(runCatching {
            PhoneTaskApprovalFactory.build(noAccount, noAccount.suppliedBrief, now)
        }.isFailure)
    }

    @Test fun rejectsInvalidRetryAndDstAmbiguity() {
        val invalidRetry = draft().copy(retryMinutes = "0")
        assertTrue(runCatching {
            PhoneTaskApprovalFactory.build(invalidRetry, invalidRetry.suppliedBrief, now)
        }.isFailure)
        val dst = draft().copy(
            scheduled = true,
            startLocal = "2027-10-31T02:30",
            endLocal = "2027-10-31T04:30",
        )
        assertTrue(runCatching {
            PhoneTaskApprovalFactory.build(dst, dst.suppliedBrief, now)
        }.isFailure)
    }
}
