package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class PhoneTaskScheduleUiPolicyTest {
    private val now = LocalDateTime.parse("2026-11-20T04:00")
    private val past = now.minusMinutes(1)
    private val future = now.plusHours(2)

    @Test fun enablingScheduleImmediatelyRevealsExpiredStartEvenWithFutureEnd() {
        assertFalse(PhoneTaskScheduleUiPolicy.expired(false, past, future, now))
        assertTrue(PhoneTaskScheduleUiPolicy.expired(true, past, future, now))
        assertFalse(PhoneTaskScheduleUiPolicy.expired(true, now.plusMinutes(1), future, now))
    }

    @Test fun expiredDefaultsAutoRefreshWithoutExtraButton() {
        val draft = PhoneTaskDraft(scheduled = true,
            startLocal = past.minusHours(2).toString(), endLocal = past.toString())
        val updated = PhoneTaskScheduleUiPolicy.refreshDefaults(draft, now)
        assertEquals(now.plusHours(1).withSecond(0).withNano(0),
            LocalDateTime.parse(updated.startLocal))
        assertEquals(LocalDateTime.parse(updated.startLocal).plusHours(1),
            LocalDateTime.parse(updated.endLocal))
        assertEquals(updated, PhoneTaskScheduleUiPolicy.refreshDefaults(updated, now))
    }

    @Test fun immediateModeRefreshesExpiredDefaultEndOnly() {
        val draft = PhoneTaskDraft(scheduled = false,
            startLocal = past.toString(), endLocal = past.toString())
        val updated = PhoneTaskScheduleUiPolicy.refreshDefaults(draft, now)
        assertEquals(draft.startLocal, updated.startLocal)
        assertEquals(now.plusHours(2).withSecond(0).withNano(0),
            LocalDateTime.parse(updated.endLocal))
    }

    @Test fun manualAndValidDatesNeverChange() {
        val manual = PhoneTaskDraft(scheduled = true,
            startLocal = past.toString(), endLocal = past.toString(),
            startManuallySet = true, endManuallySet = true)
        assertEquals(manual, PhoneTaskScheduleUiPolicy.refreshDefaults(manual, now))
        val valid = PhoneTaskDraft(scheduled = true,
            startLocal = future.toString(), endLocal = future.plusHours(2).toString())
        assertEquals(valid, PhoneTaskScheduleUiPolicy.refreshDefaults(valid, now))
    }

    @Test fun enablingScheduleRepairsUneditedEndButNotManualEnd() {
        val draft = PhoneTaskDraft(scheduled = false,
            startLocal = past.toString(), endLocal = now.plusMinutes(30).toString())
        val enabled = PhoneTaskScheduleUiPolicy.refreshDefaults(draft.copy(scheduled = true), now)
        assertTrue(LocalDateTime.parse(enabled.endLocal).isAfter(LocalDateTime.parse(enabled.startLocal)))
        assertTrue(LocalDateTime.parse(enabled.startLocal).isAfter(now))
        val custom = draft.copy(scheduled = true, endManuallySet = true)
        val preserved = PhoneTaskScheduleUiPolicy.refreshDefaults(custom, now)
        assertEquals(custom.endLocal, preserved.endLocal)
        assertTrue(PhoneTaskScheduleUiPolicy.invalidOrder(true,
            LocalDateTime.parse(preserved.startLocal), LocalDateTime.parse(preserved.endLocal)))
    }

    @Test fun endTimeMustBeAfterScheduledStartWithoutShiftingIt() {
        assertTrue(PhoneTaskScheduleUiPolicy.invalidOrder(true, future, future.minusMinutes(1)))
        assertTrue(PhoneTaskScheduleUiPolicy.invalidOrder(true, future, future))
        assertFalse(PhoneTaskScheduleUiPolicy.invalidOrder(true, now.plusHours(1), future))
        assertFalse(PhoneTaskScheduleUiPolicy.invalidOrder(false, future, future.minusMinutes(1)))
    }

    @Test fun expiredWindowEndIsVisibleForImmediateAndScheduledCalls() {
        assertTrue(PhoneTaskScheduleUiPolicy.expired(false, future, past, now))
        assertTrue(PhoneTaskScheduleUiPolicy.expired(true, past, now, now))
    }
}
