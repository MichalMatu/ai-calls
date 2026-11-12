package pl.michalmatu.aicallbridge.campaign

import android.telecom.DisconnectCause
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneTaskDisconnectPolicyTest {
    @Test fun `unanswered outgoing requires remote close and positively observed setup`() {
        assertEquals(PhoneAttemptOutcome.NO_ANSWER,
            PhoneTaskDisconnectPolicy.outcome(DisconnectCause.REMOTE, true, false, 0))
        assertEquals(PhoneAttemptOutcome.BUSY,
            PhoneTaskDisconnectPolicy.outcome(DisconnectCause.BUSY, true, false, 0))
    }
    @Test fun `brief active callback or connection timestamp prevents automatic redial`() {
        assertEquals(PhoneAttemptOutcome.UNKNOWN,
            PhoneTaskDisconnectPolicy.outcome(DisconnectCause.REMOTE, true, true, 0))
        assertEquals(PhoneAttemptOutcome.UNKNOWN,
            PhoneTaskDisconnectPolicy.outcome(DisconnectCause.REMOTE, true, false, 1_000))
    }
    @Test fun `missing details unknown local rejection and incoming missed do not imply outgoing no answer`() {
        listOf(null, DisconnectCause.UNKNOWN, DisconnectCause.LOCAL, DisconnectCause.ERROR,
            DisconnectCause.REJECTED, DisconnectCause.MISSED).forEach {
            assertEquals(PhoneAttemptOutcome.UNKNOWN, PhoneTaskDisconnectPolicy.outcome(it, true, false, 0))
        }
        assertEquals(PhoneAttemptOutcome.UNKNOWN,
            PhoneTaskDisconnectPolicy.outcome(DisconnectCause.REMOTE, false, false, 0))
        assertEquals(PhoneAttemptOutcome.UNKNOWN,
            PhoneTaskDisconnectPolicy.outcome(DisconnectCause.REMOTE, true, false, null))
    }
    @Test fun `observed network ringing is not cut off by the call-binding watchdog`() {
        assertEquals(false, PhoneTaskActivationPolicy.timedOut(120_000, 30_000, null, false, true))
        assertEquals(true, PhoneTaskActivationPolicy.timedOut(30_000, 30_000, null, false, false))
        assertEquals(false, PhoneTaskActivationPolicy.timedOut(120_001, 30_000, 150_000, true, false))
        assertEquals(true, PhoneTaskActivationPolicy.timedOut(150_000, 30_000, 150_000, true, false))
    }
}
