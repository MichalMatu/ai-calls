package pl.michalmatu.aicallbridge.realtime

import kotlin.coroutines.Continuation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeTransportControlSurfaceTest {
    @Test
    fun onlyConnectionSetupUsesSuspendContinuation() {
        val methods = RealtimeTransport::class.java.declaredMethods.toList()

        assertTrue(methods.any { it.name.startsWith("connect") && it.hasContinuationParameter() })
        assertFalse(methods.any { it.name.startsWith("sendAudio") && it.hasContinuationParameter() })
        assertFalse(methods.any { it.name.startsWith("cancelResponse") && it.hasContinuationParameter() })
        assertFalse(methods.any { it.name == "close" && it.hasContinuationParameter() })
    }

    private fun java.lang.reflect.Method.hasContinuationParameter(): Boolean =
        parameterTypes.any { Continuation::class.java.isAssignableFrom(it) }
}
