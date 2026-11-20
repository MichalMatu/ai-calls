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
