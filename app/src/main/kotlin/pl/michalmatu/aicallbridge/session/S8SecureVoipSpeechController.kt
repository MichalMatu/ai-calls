package pl.michalmatu.aicallbridge.session

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-use owner joining verified incoming SIP with app-owned local AI speech.
 *
 * A native SIP implementation must provide the exact identity, authenticated
 * SRTP media and TLS-verified signaling. This class never opens unencrypted
 * remote RTP, places a call, or assumes access to cellular call audio.
 *
 * Media is established away from Android's main thread. STOP revokes local
 * PCM immediately even when the native SIP callback is late or blocks.
 */
internal class S8SecureVoipSpeechController(
    private val exactIdentity: Any,
    nativeMedia: S8SecureSipDialogGate.VerifiedMedia,
    nativePort: AppOwnedVoipCallMediaSessionBackend.MediaPort,
    signaling: S8SecureSipDialogGate.Signaling,
    private val speech: S8VoipLocalSpeechSession.SpeechTurn,
    private val speechExecutor: Executor,
    private val mediaExecutor: Executor,
    heartbeatScheduler: CallMediaHeartbeatScheduler,
    clock: CallMediaMonotonicClock,
    private val maxTurns: Int = 6,
    private val observer: (Snapshot) -> Unit = {},
) : AutoCloseable {
    data class Snapshot(
        val dialog: S8SecureSipDialogGate.Snapshot,
        val voice: S8VoipLocalSpeechSession.Snapshot?,
    )

    private val lock = Any()
    private val stopped = AtomicBoolean(false)
    private val dialog = S8SecureSipDialogGate(signaling, nativeMedia) { publish() }
    private val securePort = S8AuthenticatedVoipMediaPort(
        exactIdentity, dialog, nativeMedia, nativePort,
    )
    private val backend = AppOwnedVoipCallMediaSessionBackend(securePort, exactIdentity)
    private val coordinator = CallMediaSessionCoordinator(
        backend, heartbeatScheduler, clock,
    ) { snapshot ->
        if (snapshot.state() == CallMediaSessionState.FAILED) close()
    }
    private var voice: S8VoipLocalSpeechSession? = null
    private var opening = false

    init {
        require(maxTurns in 1..6) { "s8_secure_voip_turn_count" }
        require(nativeMedia.exactCallIdentity() === exactIdentity) {
            "s8_exact_native_identity_required"
        }
    }

    fun snapshot(): Snapshot = Snapshot(dialog.snapshot(), synchronized(lock) {
        voice?.snapshot()
    })

    /** Called only after the native stack has authenticated the TLS INVITE. */
    fun onVerifiedInvite(identity: Any): Boolean =
        !stopped.get() && dialog.onVerifiedIncomingInvite(identity)

    /** Explicit accept tap in the UI. No media opens during SIP negotiation. */
    fun accept(identity: Any): Boolean =
        !stopped.get() && dialog.accept(identity)

    /** Only an independently verified native SRTP callback may call this. */
    fun onAuthenticatedSrtp(identity: Any): Boolean {
        if (stopped.get() || !dialog.onAuthenticatedSrtp(identity)) return false
        synchronized(lock) {
            if (stopped.get() || opening || voice != null) return false
            opening = true
        }
        try {
            mediaExecutor.execute { openVoiceAfterAuthentication() }
        } catch (_: RuntimeException) {
            close()
            return false
        }
        return true
    }

    private fun openVoiceAfterAuthentication() {
        if (stopped.get() || !dialog.snapshot().mediaReady()) return
        val generation = try {
            coordinator.start(16_000)
        } catch (_: Throwable) {
            close()
            return
        }
        if (stopped.get() || !dialog.snapshot().mediaReady() ||
            coordinator.snapshot().state() != CallMediaSessionState.ACTIVE) {
            close()
            return
        }
        val current = try {
            S8VoipLocalSpeechSession(
                coordinator, generation, speech, speechExecutor, maxTurns,
                observer = {
                    publish()
                    if (it.phase == S8VoipLocalSpeechSession.Phase.FAILED ||
                        it.phase == S8VoipLocalSpeechSession.Phase.COMPLETE) close()
                },
            )
        } catch (_: Throwable) {
            close()
            return
        }
        val claimed = synchronized(lock) {
            if (stopped.get() || voice != null || !dialog.snapshot().mediaReady()) false
            else {
                voice = current
                true
            }
        }
        if (!claimed) {
            current.close()
            return
        }
        try {
            current.start()
        } catch (_: Throwable) {
            close()
        }
    }

    /** Remote BYE/CANCEL and security loss revoke local PCM before signaling. */
    fun onRemoteBye(identity: Any) {
        if (identity !== exactIdentity) return
        coordinator.takeOverNow()
        dialog.onRemoteBye(identity)
        close()
    }

    fun onRemoteCancel(identity: Any) {
        if (identity !== exactIdentity) return
        if (dialog.snapshot().state() != S8SecureSipDialogGate.State.RINGING) return
        coordinator.takeOverNow()
        dialog.onRemoteCancel(identity)
        close()
    }

    fun onMediaSecurityLost(identity: Any) {
        if (identity !== exactIdentity) return
        coordinator.takeOverNow()
        dialog.onMediaSecurityLost(identity)
        close()
    }

    override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        coordinator.takeOverNow()
        val running = synchronized(lock) { voice.also { voice = null } }
        if (running != null) {
            running.close()
        } else {
            try {
                speechExecutor.execute {
                    try { speech.cancel() } catch (_: Throwable) {}
                    try { speech.close() } catch (_: Throwable) {}
                }
            } catch (_: RuntimeException) {}
        }
        dialog.takeOverNow()
        backend.close()
        publish()
    }

    private fun publish() {
        try { observer(snapshot()) } catch (_: Throwable) {}
    }
}
