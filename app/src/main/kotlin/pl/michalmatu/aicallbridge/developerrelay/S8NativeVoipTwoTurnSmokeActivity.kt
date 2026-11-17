package pl.michalmatu.aicallbridge.developerrelay

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.TelecomManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import pl.michalmatu.aicallbridge.LocalSpeechTextPipelineProbe
import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.localspeech.LocalTtsSpeechOutput
import pl.michalmatu.aicallbridge.session.AppOwnedLoopbackRtpMediaPort
import pl.michalmatu.aicallbridge.session.AppOwnedVoipCallMediaSessionBackend
import pl.michalmatu.aicallbridge.session.CallMediaHeartbeatScheduler
import pl.michalmatu.aicallbridge.session.CallMediaMonotonicClock
import pl.michalmatu.aicallbridge.session.CallMediaSessionCoordinator
import pl.michalmatu.aicallbridge.session.CallMediaSessionState
import pl.michalmatu.aicallbridge.session.RtpPcm16kAdapterMediaPort
import pl.michalmatu.aicallbridge.session.RtpPcmuCodec
import pl.michalmatu.aicallbridge.session.S8LocalSpeechTurnAdapter
import pl.michalmatu.aicallbridge.session.S8VoipLocalSpeechSession
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness
import pl.michalmatu.aicallbridge.textagent.CallTextAgentOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState

/**
 * Owner-launched DUMP-gated integration: two REAL local Android STT -> Gemma
 * E2B -> approved TTS turns bridged through localhost RTP/PCMU in both directions.
 * Both inputs originate from on-device synthetic TTS, never from a person or
 * carrier call. No SIP account, PSTN, external host or network inference.
 *
 * Report contains counters only; never persist PCM, transcripts or model output.
 */
class S8NativeVoipTwoTurnSmokeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val stopped = AtomicBoolean()
    private val peerPackets = AtomicInteger()
    private val peerSpeechPackets = AtomicInteger()
    private val loop = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val sourcePcm = arrayOfNulls<ByteArray>(TURNS)
    private var source: LocalTtsSpeechOutput? = null
    private var pipeline: LocalSpeechTextPipeline? = null
    private var peer: DatagramSocket? = null
    private var port: AppOwnedLoopbackRtpMediaPort? = null
    private var adapter: RtpPcm16kAdapterMediaPort? = null
    private var backend: AppOwnedVoipCallMediaSessionBackend? = null
    private var owner: CallMediaSessionCoordinator? = null
    private var session: S8VoipLocalSpeechSession? = null
    private var feeder: Thread? = null
    private var receiver: Thread? = null
    private var sentPackets = 0
    private var deliveredTurn = 0
    private var activePort = -1
    private var finalSnapshot: S8VoipLocalSpeechSession.Snapshot? = null
    private val timeout = Runnable { complete("timeout") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        report("running")
        if (!phoneIdleAndAuthorized()) {
            complete("blocked_not_idle_or_permission")
            return
        }
        if (runCatching {
                AndroidGemma4ModelReadiness.check(this, DialogueModelId.GEMMA_4_E2B).state ==
                    Gemma4ModelReadinessState.READY
            }.getOrDefault(false).not()) {
            complete("model_unavailable")
            return
        }
        handler.postDelayed(timeout, TIMEOUT_MS)
        source = LocalTtsSpeechOutput(applicationContext)
        synthesizeInput(0)
    }

    private fun phoneIdleAndAuthorized(): Boolean = runCatching {
        val telecom = getSystemService(TelecomManager::class.java)
        val audio = getSystemService(AudioManager::class.java)
        checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED &&
            telecom != null && !telecom.isInCall && !telecom.isInManagedCall &&
            audio != null && audio.mode != AudioManager.MODE_IN_CALL &&
            audio.mode != AudioManager.MODE_IN_COMMUNICATION
    }.getOrDefault(false)

    // Pre-synthesize both short phrases before opening RTP, so Android TTS
    // initialization cannot trigger an RX-idle timeout on the first packet.
    private fun synthesizeInput(index: Int) {
        if (stopped.get()) return
        try {
            source?.synthesize(INPUTS[index], object : LocalTtsSpeechOutput.Listener {
                override fun onPcm16Mono16k(pcm: ByteArray) {
                    if (stopped.get()) { pcm.fill(0); return }
                    if (pcm.isEmpty() || pcm.size % 2 != 0 || pcm.size > MAX_SOURCE_BYTES) {
                        pcm.fill(0)
                        complete("source_pcm_invalid")
                        return
                    }
                    sourcePcm[index] = pcm
                    if (index + 1 < TURNS) synthesizeInput(index + 1)
                    else startLocalLoop()
                }
                override fun onError(reason: String) = complete("source_tts_failed")
            })
        } catch (_: Throwable) {
            complete("source_tts_failed")
        }
    }

    private fun startLocalLoop() {
        if (stopped.get() || !phoneIdleAndAuthorized()) {
            complete("blocked_not_idle_or_permission")
            return
        }
        try {
            val remote = DatagramSocket(InetSocketAddress(loop, 0)).apply { soTimeout = 200 }
            peer = remote
            val identity = Any()
            val raw = AppOwnedLoopbackRtpMediaPort(
                identity, 0, remote.localPort, PEER_SSRC, APP_SSRC,
            )
            port = raw
            val resampled = RtpPcm16kAdapterMediaPort(raw)
            adapter = resampled
            val mediaBackend = AppOwnedVoipCallMediaSessionBackend(resampled, identity)
            backend = mediaBackend
            val mediaOwner = CallMediaSessionCoordinator(
                mediaBackend,
                CallMediaHeartbeatScheduler { _, _ -> AutoCloseable { } },
                CallMediaMonotonicClock { SystemClock.elapsedRealtime() },
                { },
            )
            owner = mediaOwner
            val generation = mediaOwner.start(16_000)
            if (mediaOwner.snapshot().state() != CallMediaSessionState.ACTIVE) {
                complete("media_not_active")
                return
            }
            activePort = raw.boundLocalPort()
            if (activePort < 1024) {
                complete("media_no_local_port")
                return
            }

            val localBackend = Gemma4LiteRtTextBackendFactory.create(
                applicationContext,
                systemInstruction = "Jesteś lokalnym asystentem testowym. " +
                    "Odpowiadaj krótko po polsku, jednym zdaniem. " +
                    "Bez połączeń i danych osobowych.",
                modelId = DialogueModelId.GEMMA_4_E2B,
            )
            val localPipeline = LocalSpeechTextPipeline(
                applicationContext, localBackend,
                CallTextAgentOutputApprovalPolicy(
                    LocalSpeechTextPipelineProbe.activeDiagnosticWorkflow(),
                    CallCommitmentGate { "s8-synthetic-loopback-only" },
                ),
            )
            pipeline = localPipeline
            val running = S8VoipLocalSpeechSession(
                mediaOwner, generation, S8LocalSpeechTurnAdapter(localPipeline),
                applicationContext.mainExecutor,
                maxTurns = TURNS,
                observer = { snapshot ->
                    handler.post {
                        if (stopped.get()) return@post
                        finalSnapshot = snapshot
                        if (snapshot.phase == S8VoipLocalSpeechSession.Phase.FAILED) {
                            complete("speech_pipeline_failed")
                        } else if (snapshot.phase == S8VoipLocalSpeechSession.Phase.COMPLETE) {
                            // Give the localhost receive worker a short bounded drain.
                            handler.postDelayed({ complete("completed") }, 500)
                        }
                    }
                },
                onListeningReady = { turn ->
                    handler.post { startPeerInput(turn) }
                },
            )
            session = running
            receiver = Thread({ receiveLocalOutput(remote) }, "s8-native-voip-peer-rx").apply {
                isDaemon = true
                start()
            }
            running.start()
        } catch (_: Throwable) {
            complete("native_voip_setup_failed")
        }
    }

    private fun startPeerInput(turn: Int) {
        if (stopped.get() || turn !in 1..TURNS || turn != deliveredTurn + 1 ||
            !phoneIdleAndAuthorized()) {
            complete("peer_input_not_authorized")
            return
        }
        val phrase = sourcePcm[turn - 1] ?: run {
            complete("source_missing")
            return
        }
        deliveredTurn = turn
        feeder = Thread(
            { feedRtp(turn, phrase) }, "s8-native-voip-peer-tx",
        ).apply { isDaemon = true; start() }
    }

    private fun feedRtp(turn: Int, audio: ByteArray) {
        try {
            // This is a separate synthetic VoIP peer, not the product uplink.
            val silence = ByteArray(640)
            fun paced16k(bytes: ByteArray) {
                val wide = ByteArray(640)
                val narrow = ByteArray(320)
                var offset = 0
                while (offset < bytes.size && !stopped.get()) {
                    if (session?.snapshot()?.turn != turn) return
                    val size = minOf(640, bytes.size - offset)
                    wide.fill(0)
                    System.arraycopy(bytes, offset, wide, 0, size)
                    RtpPcm16kAdapterMediaPort.reduce16kTo8k(wide, narrow)
                    val seq = sentPackets++
                    val packet = RtpPcmuCodec.packetize(
                        narrow, 0, seq and 0xffff, seq.toLong() * 160L, PEER_SSRC,
                    )
                    peer?.send(DatagramPacket(packet, packet.size, loop, activePort))
                    offset += size
                    Thread.sleep(20L)
                }
                wide.fill(0)
                narrow.fill(0)
            }
            paced16k(ByteArray(640 * 25))
            paced16k(audio)
            paced16k(ByteArray(640 * 45))
        } catch (_: Throwable) {
            if (!stopped.get()) handler.post { complete("peer_input_failed") }
        } finally {
            audio.fill(0)
        }
    }

    private fun receiveLocalOutput(remote: DatagramSocket) {
        while (!stopped.get()) {
            try {
                val packet = DatagramPacket(ByteArray(RtpPcmuCodec.PACKET_BYTES + 1),
                    RtpPcmuCodec.PACKET_BYTES + 1)
                remote.receive(packet)
                val decoded = RtpPcmuCodec.parse(packet.data, packet.length, APP_SSRC)
                    ?: continue
                peerPackets.incrementAndGet()
                if (decoded.pcm16().any { it != 0.toByte() }) {
                    peerSpeechPackets.incrementAndGet()
                }
                decoded.pcm16().fill(0)
            } catch (_: SocketTimeoutException) {
                // Check STOP without polling the app-side lease.
            } catch (_: Throwable) {
                if (!stopped.get()) handler.post { complete("peer_output_failed") }
                return
            }
        }
    }

    private fun complete(outcome: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { complete(outcome) }
            return
        }
        if (!stopped.compareAndSet(false, true)) return
        handler.removeCallbacksAndMessages(null)
        val snapshot = finalSnapshot ?: session?.snapshot()
        val pass = outcome == "completed" &&
            snapshot?.phase == S8VoipLocalSpeechSession.Phase.COMPLETE &&
            snapshot.turn == TURNS && snapshot.transcripts == TURNS &&
            snapshot.approvals == TURNS && snapshot.incomingFrames > 0 &&
            snapshot.outgoingFrames > 0 && deliveredTurn == TURNS &&
            peerPackets.get() > 0 && peerSpeechPackets.get() > 0
        feeder?.interrupt()
        session?.close() // Revokes RTP lease before any native model cleanup.
        peer?.close()
        receiver?.interrupt()
        owner?.close()
        backend?.close()
        adapter?.close()
        port?.close()
        source?.close()
        if (session == null) runCatching { pipeline?.close() }
        sourcePcm.forEach { it?.fill(0) }
        report(if (pass) "pass" else if (outcome == "completed") "e2e_incomplete" else outcome,
            snapshot)
        finish()
    }

    private fun report(
        state: String, snapshot: S8VoipLocalSpeechSession.Snapshot? = null,
    ) {
        PrivateDiagnosticReportStore.write(filesDir, REPORT_FILE, buildString {
            appendLine("probe=s8_native_voip_two_turn")
            appendLine("transport=localhost_rtp_pcmu_8k")
            appendLine("backend=on_device_gemma_e2b")
            appendLine("stt_transcripts=${snapshot?.transcripts ?: 0}")
            appendLine("approved_turns=${snapshot?.approvals ?: 0}")
            appendLine("completed_turns=${snapshot?.turn ?: 0}")
            appendLine("rtp_rx_frames=${snapshot?.incomingFrames ?: 0}")
            appendLine("rtp_tx_frames=${snapshot?.outgoingFrames ?: 0}")
            appendLine("peer_tx_packets=${peerPackets.get()}")
            appendLine("peer_voice_packets=${peerSpeechPackets.get()}")
            appendLine("status=$state")
            appendLine("real_call=false")
            appendLine("remote_model=false")
            appendLine("report_complete=${state != "running"}")
        })
    }

    override fun onStop() {
        if (!stopped.get()) complete("stopped")
        super.onStop()
    }

    companion object {
        const val REPORT_FILE = "s8-native-voip-two-turn-report.txt"
        private const val TURNS = 2
        private const val PEER_SSRC = 0x14630AA1
        private const val APP_SSRC = 0x15430BB2
        private const val TIMEOUT_MS = 240_000L
        private const val MAX_SOURCE_BYTES = 16_000 * 2 * 8
        private val INPUTS = arrayOf(
            "To jest test lokalnego agenta tekstowego.",
            "To jest druga tura lokalnego agenta tekstowego.",
        )
    }
}
