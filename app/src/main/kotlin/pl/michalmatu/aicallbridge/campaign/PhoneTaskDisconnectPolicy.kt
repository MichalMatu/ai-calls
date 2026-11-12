package pl.michalmatu.aicallbridge.campaign

import android.telecom.DisconnectCause

/**
 * Android REMOTE means either hangup OR unanswered outgoing, while MISSED is incoming-only.
 * https://developer.android.com/reference/android/telecom/DisconnectCause#REMOTE
 * Retry only with observed setup, no ACTIVE/HOLDING callback and a zero connection timestamp.
 * A timeout, missing Details, or even a brief connected call remains UNKNOWN.
 */
internal object PhoneTaskDisconnectPolicy {
    fun outcome(cause: Int?, observedSetup: Boolean, observedConnection: Boolean,
        connectTimeMillis: Long?): PhoneAttemptOutcome {
        if (!observedSetup || observedConnection || connectTimeMillis != 0L) return PhoneAttemptOutcome.UNKNOWN
        return when (cause) {
            DisconnectCause.BUSY -> PhoneAttemptOutcome.BUSY
            DisconnectCause.REMOTE -> PhoneAttemptOutcome.NO_ANSWER
            else -> PhoneAttemptOutcome.UNKNOWN
        }
    }
}

/** Network ringing is not a missing Call handle; keep the existing watchdog for missing ownership/audio. */
internal object PhoneTaskActivationPolicy {
    fun timedOut(now: Long, bindingDeadline: Long, audioDeadline: Long?, active: Boolean,
        connecting: Boolean): Boolean = when {
        active -> audioDeadline == null || now >= audioDeadline
        connecting -> false
        else -> now >= bindingDeadline
    }
}
