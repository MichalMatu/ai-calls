package pl.michalmatu.aicallbridge.session

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No network, native SDK, phone number, or real audio. */
class S8SecureVoipSpeechControllerTest {
    @Test fun nativeAuthenticationRequiredBeforeAnyAudioOrModelStarts() {
        val native = FakeEngine()
        val speech = FakeSpeech()
        val controller = newController(native, speech, Executor { it.run() })
        assertFalse(controller.onAuthenticatedSrtp(native.identity))
        assertFalse(controller.onVerifiedInvite(Any()))
        assertTrue(controller.onVerifiedInvite(native.identity))
        assertTrue(controller.accept(native.identity))
        assertEquals(0, native.opens)
        assertEquals(0, speech.starts)
        assertFalse(controller.onAuthenticatedSrtp(native.identity))
        assertEquals(0, native.opens)
        native.secure = S8SecureSipDialogGate.MediaSecurity.TLS_AND_SRTP_AUTHENTICATED
        assertTrue(controller.onAuthenticatedSrtp(native.identity))
        assertEquals(1, native.opens)
        assertEquals(1, speech.starts)
        assertEquals(S8VoipLocalSpeechSession.Phase.LISTENING,
            controller.snapshot().voice?.phase)
        controller.close()
        assertEquals(1, native.closes)
        assertEquals(1, native.mediaCloses)
        assertEquals(1, native.byes)
    }

    @Test fun delayedMediaOpenCannotStartAfterStopOrNewIdentity() {
        val native = FakeEngine()
        val speech = FakeSpeech()
        var pending: Runnable? = null
        val controller = newController(native, speech, Executor { pending = it })
        assertTrue(controller.onVerifiedInvite(native.identity))
        assertTrue(controller.accept(native.identity))
        native.secure = S8SecureSipDialogGate.MediaSecurity.TLS_AND_SRTP_AUTHENTICATED
        assertTrue(controller.onAuthenticatedSrtp(native.identity))
        assertEquals(0, native.opens)
        controller.close()
        pending?.run()
        assertEquals(0, native.opens)
        assertEquals(0, speech.starts)
        assertEquals(1, native.closes)
        assertFalse(controller.onAuthenticatedSrtp(native.identity))
    }

    @Test fun remoteByeAndLostSrtpKeysImmediatelyStopLocalVoice() {
        val native = FakeEngine()
        val speech = FakeSpeech()
        val controller = newController(native, speech, Executor { it.run() })
        assertTrue(controller.onVerifiedInvite(native.identity))
        assertTrue(controller.accept(native.identity))
        native.secure = S8SecureSipDialogGate.MediaSecurity.TLS_AND_SRTP_AUTHENTICATED
        assertTrue(controller.onAuthenticatedSrtp(native.identity))
        controller.onRemoteBye(Any())
        assertEquals(0, native.closes)
        controller.onMediaSecurityLost(native.identity)
        assertEquals(1, native.closes)
        assertEquals(1, native.mediaCloses)
        assertEquals(1, native.byes)
        assertFalse(controller.snapshot().dialog.mediaReady())
        assertEquals(null, controller.snapshot().voice)
    }

    private fun newController(
        engine: FakeEngine, speech: FakeSpeech, mediaExecutor: Executor,
    ) = S8SecureVoipSpeechController(
        engine.identity, engine, engine, engine, speech,
        Executor { it.run() }, mediaExecutor,
        CallMediaHeartbeatScheduler { _, _ -> AutoCloseable { } },
        CallMediaMonotonicClock { 1L }, maxTurns = 2,
    )

    private class FakeSpeech : S8VoipLocalSpeechSession.SpeechTurn {
        var starts = 0
        override fun start(listener: S8VoipLocalSpeechSession.SpeechTurn.Listener) {
            starts++ // no onReady: no RX worker needed for lifecycle-only test
        }
        override fun writeInputPcm(frame: ByteArray) = true
        override fun finishInput() {}
        override fun cancel() {}
        override fun close() {}
    }

    private class FakeEngine :
        S8SecureSipDialogGate.VerifiedMedia,
        S8SecureSipDialogGate.Signaling,
        AppOwnedVoipCallMediaSessionBackend.MediaPort {
        val identity = Any()
        var secure = S8SecureSipDialogGate.MediaSecurity.UNVERIFIED
        var opens = 0
        var mediaCloses = 0
        var closes = 0
        var byes = 0

        override fun mediaSecurity() = secure
        override fun exactCallIdentity(): Any = identity
        override fun activeIdentity(): Any? = if (closes > 0) null else identity
        override fun mediaHealthy(exactIdentity: Any?) =
            exactIdentity === identity && mediaCloses == 0 && closes == 0
        override fun open(
            exactIdentity: Any, generation: Long, sampleRateHz: Int,
        ): CallMediaEndpointLease {
            check(exactIdentity === identity && sampleRateHz == 16_000)
            opens++
            val rx = ByteArrayInputStream(ByteArray(640))
            val tx = ByteArrayOutputStream()
            return object : CallMediaEndpointLease {
                override fun downlink(): InputStream = rx
                override fun uplink(): OutputStream = tx
                override fun close() { mediaCloses++ }
            }
        }

        override fun tlsPeerVerified() = true
        override fun answerInvite(exactIdentity: Any) {}
        override fun rejectInvite(exactIdentity: Any) {}
        override fun endDialog(exactIdentity: Any) { byes++ }
        override fun acknowledgeRemoteBye(exactIdentity: Any) {}
        override fun acknowledgeRemoteCancel(exactIdentity: Any) {}
        override fun close() {
            closes++
            secure = S8SecureSipDialogGate.MediaSecurity.UNVERIFIED
        }
    }
}
