package pl.michalmatu.aicallbridge.developerrelay

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.TelecomManager
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiActionTone
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
    private val sourcePcm = arrayOfNulls<ByteArray>(MAX_TURNS)
    private var turnLimit = 2
    private var lossEveryNPackets = 0
    private val lostPeerPackets = AtomicInteger()
    private val startAtMs = SystemClock.elapsedRealtime()
    private val readyAtMs = LongArray(MAX_TURNS)
    private val replyLatencyMs = LongArray(MAX_TURNS)
    private val processingAtMs = LongArray(MAX_TURNS)
    private val approvedAtMs = LongArray(MAX_TURNS)
    private val speakingAtMs = LongArray(MAX_TURNS)
    private var maxJavaHeapBytes = 0L
    private var maxNativeHeapBytes = 0L
    private var source: LocalTtsSpeechOutput? = null
    private var progressView: TextView? = null
    private var pipeline: LocalSpeechTextPipeline? = null
    private var peer: DatagramSocket? = null
    private var port: AppOwnedLoopbackRtpMediaPort? = null
    private var adapter: RtpPcm16kAdapterMediaPort? = null
    private var backend: AppOwnedVoipCallMediaSessionBackend? = null
    private var owner: CallMediaSessionCoordinator? = null
    private var session: S8VoipLocalSpeechSession? = null
    private var feeder: Thread? = null
    private var receiver: Thread? = null
    private val sentPackets = AtomicInteger()
    private var deliveredTurn = 0
    private var activePort = -1
    private var finalSnapshot: S8VoipLocalSpeechSession.Snapshot? = null
    private val mediaResourcesLock = Any()
    private var mediaSetupThread: Thread? = null
    @Volatile private var mediaStartState = "NOT_STARTED"
    @Volatile private var mediaStartFailure = "NONE"
    @Volatile private var mediaStartDetail = "none"
    private val timeout = Runnable { complete("timeout") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildLocalVoipUi()
        turnLimit = intent?.getIntExtra("turn_limit", 2) ?: 2
        lossEveryNPackets = intent?.getIntExtra("loss_every_n_packets", 0) ?: 0
        if (turnLimit !in 2..MAX_TURNS ||
            (lossEveryNPackets != 0 && lossEveryNPackets !in 20..100)) {
            complete("invalid_test_parameters")
            return
        }
        sampleMemory()
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

    private fun buildLocalVoipUi() {
        val body = UiDesign.content(this)
        body.addView(
            UiDesign.heading(this, getString(R.string.s8_voip_native_title)),
            UiDesign.spaceAfter(this),
        )
        body.addView(
            UiDesign.caption(this, getString(R.string.s8_voip_native_caption)),
            UiDesign.spaceAfter(this, UiDesign.GAP_CARD),
        )
        val card = UiDesign.card(this)
        progressView = UiDesign.status(
            this, getString(R.string.s8_voip_native_running),
        ).apply { tag = "s8-native-voip-status" }
        card.addView(progressView, UiDesign.spaceAfter(this))
        card.addView(
            UiDesign.action(
                this, getString(R.string.s8_voip_lab_stop),
                UiActionTone.REVOCATION,
            ) { complete("stopped") }.apply { tag = "s8-native-voip-stop" },
        )
        body.addView(card)
        setContentView(ScrollView(this).apply { addView(body) })
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
                    if (index + 1 < turnLimit) synthesizeInput(index + 1)
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
        // Android forbids socket connect on the main thread. Only socket and
        // coordinator setup use this worker; STT, TTS and Activity stay on main.
        mediaSetupThread = Thread({
            var openedPeer: DatagramSocket? = null
            var openedPort: AppOwnedLoopbackRtpMediaPort? = null
            var openedAdapter: RtpPcm16kAdapterMediaPort? = null
            var openedBackend: AppOwnedVoipCallMediaSessionBackend? = null
            var openedOwner: CallMediaSessionCoordinator? = null
            var handedOff = false
            try {
                val remote = DatagramSocket(InetSocketAddress(loop, 0)).apply {
                    soTimeout = 200
                }
                openedPeer = remote
                val identity = Any()
                val raw = AppOwnedLoopbackRtpMediaPort(
                    identity, 0, remote.localPort, PEER_SSRC, APP_SSRC,
                )
                openedPort = raw
                val resampled = RtpPcm16kAdapterMediaPort(raw)
                openedAdapter = resampled
                val mediaBackend = AppOwnedVoipCallMediaSessionBackend(resampled, identity)
                openedBackend = mediaBackend
                val mediaOwner = CallMediaSessionCoordinator(
                    mediaBackend,
                    CallMediaHeartbeatScheduler { _, _ -> AutoCloseable { } },
                    CallMediaMonotonicClock { SystemClock.elapsedRealtime() },
                    { },
                )
                openedOwner = mediaOwner
                val generation = mediaOwner.start(16_000)
                val mediaSnapshot = mediaOwner.snapshot()
                mediaStartState = mediaSnapshot.state().name
                mediaStartFailure = mediaSnapshot.failure().name
                mediaStartDetail = CallDiagnosticEvidence.safeCode(
                    mediaSnapshot.failureDetail()?.substringAfter(':')?.trim() ?: "none"
                )
                val boundPort = raw.boundLocalPort()
                synchronized(mediaResourcesLock) {
                    if (!stopped.get()) {
                        peer = remote
                        port = raw
                        adapter = resampled
                        backend = mediaBackend
                        owner = mediaOwner
                        activePort = boundPort
                        handedOff = true
                    }
                }
                if (!handedOff) return@Thread
                handler.post {
                    if (stopped.get()) return@post
                    if (mediaSnapshot.state() != CallMediaSessionState.ACTIVE) {
                        complete("media_not_active")
                    } else if (boundPort < 1024) {
                        complete("media_no_local_port")
                    } else if (!phoneIdleAndAuthorized()) {
                        complete("blocked_not_idle_or_permission")
                    } else {
                        startLocalPipeline(mediaOwner, generation, remote)
                    }
                }
            } catch (_: Throwable) {
                if (!stopped.get()) complete("native_voip_setup_failed")
            } finally {
                if (!handedOff) {
                    try { openedOwner?.close() } catch (_: Throwable) {}
                    try { openedBackend?.close() } catch (_: Throwable) {}
                    try { openedAdapter?.close() } catch (_: Throwable) {}
                    try { openedPort?.close() } catch (_: Throwable) {}
                    openedPeer?.close()
                }
            }
        }, "s8-voip-local-media-setup").apply {
            isDaemon = true
            start()
        }
    }

    private fun startLocalPipeline(
        mediaOwner: CallMediaSessionCoordinator,
        generation: Long,
        remote: DatagramSocket,
    ) {
        if (stopped.get()) return
        try {
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
                maxTurns = turnLimit,
                observer = { snapshot ->
                    handler.post {
                        if (stopped.get()) return@post
                        finalSnapshot = snapshot
                        progressView?.text = getString(
                            R.string.s8_voip_native_progress,
                            snapshot.turn, turnLimit,
                            snapshot.incomingFrames, snapshot.outgoingFrames,
                        )
                        sampleMemory()
                        if (snapshot.turn in 1..turnLimit) {
                            val index = snapshot.turn - 1
                            val now = SystemClock.elapsedRealtime()
                            if (snapshot.phase == S8VoipLocalSpeechSession.Phase.PROCESSING &&
                                processingAtMs[index] == 0L) {
                                processingAtMs[index] = now
                            }
                            if (snapshot.approvals >= snapshot.turn &&
                                approvedAtMs[index] == 0L) {
                                approvedAtMs[index] = now
                            }
                            if (snapshot.phase == S8VoipLocalSpeechSession.Phase.SPEAKING &&
                                speakingAtMs[index] == 0L) {
                                speakingAtMs[index] = now
                                val started = readyAtMs[index]
                                if (started != 0L) {
                                    replyLatencyMs[index] = now - started
                                }
                            }
                        }
                        if (snapshot.phase == S8VoipLocalSpeechSession.Phase.FAILED) {
                            complete("speech_pipeline_failed")
                        } else if (snapshot.phase == S8VoipLocalSpeechSession.Phase.COMPLETE) {
                            // Give the localhost receive worker a short bounded drain.
                            handler.postDelayed({ complete("completed") }, 500)
                        }
                    }
                },
                onListeningReady = { turn ->
                    handler.post {
                        if (turn in 1..turnLimit) {
                            readyAtMs[turn - 1] = SystemClock.elapsedRealtime()
                            sampleMemory()
                        }
                        startPeerInput(turn)
                    }
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
        if (stopped.get() || turn !in 1..turnLimit || turn != deliveredTurn + 1 ||
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
                    val live = session?.snapshot() ?: return
                    if (live.turn != turn ||
                        live.phase != S8VoipLocalSpeechSession.Phase.LISTENING) return
                    val size = minOf(640, bytes.size - offset)
                    wide.fill(0)
                    System.arraycopy(bytes, offset, wide, 0, size)
                    RtpPcm16kAdapterMediaPort.reduce16kTo8k(wide, narrow)
                    val seq = sentPackets.getAndIncrement()
                    if (lossEveryNPackets > 0 &&
                        (seq + 1) % lossEveryNPackets == 0) {
                        lostPeerPackets.incrementAndGet()
                    } else {
                        val packet = RtpPcmuCodec.packetize(
                            narrow, 0, seq and 0xffff, seq.toLong() * 160L, PEER_SSRC,
                        )
                        peer?.send(DatagramPacket(packet, packet.size, loop, activePort))
                    }
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
            snapshot.turn == turnLimit && snapshot.transcripts == turnLimit &&
            snapshot.approvals == turnLimit && snapshot.incomingFrames > 0 &&
            snapshot.outgoingFrames > 0 && deliveredTurn == turnLimit &&
            peerPackets.get() > 0 && peerSpeechPackets.get() > 0
        feeder?.interrupt()
        mediaSetupThread?.interrupt()
        // Fence late off-main socket setup against STOP, then close the lease.
        synchronized(mediaResourcesLock) {
            session?.close() // Revokes RTP lease before native model cleanup.
            peer?.close()
            receiver?.interrupt()
            owner?.close()
            backend?.close()
            adapter?.close()
            port?.close()
        }
        source?.close()
        if (session == null) runCatching { pipeline?.close() }
        sourcePcm.forEach { it?.fill(0) }
        sampleMemory()
        report(if (pass) "pass" else if (outcome == "completed") "e2e_incomplete" else outcome,
            snapshot)
        finish()
    }

    private fun report(
        state: String, snapshot: S8VoipLocalSpeechSession.Snapshot? = null,
    ) {
        PrivateDiagnosticReportStore.write(filesDir, REPORT_FILE, buildString {
            appendLine("probe=s8_native_voip_multi_turn")
            appendLine("requested_turns=$turnLimit")
            appendLine("observed_duration_ms=${SystemClock.elapsedRealtime() - startAtMs}")
            appendLine("max_java_heap_used_bytes=$maxJavaHeapBytes")
            appendLine("max_native_heap_bytes=$maxNativeHeapBytes")
            appendLine("worst_turn_response_ms=${replyLatencyMs.maxOrNull() ?: 0L}")
            appendLine("measured_response_turns=${replyLatencyMs.count { it > 0L }}")
            appendLine("max_capture_ms=${longestStage(processingAtMs, readyAtMs)}")
            appendLine("max_stt_model_ms=${longestStage(approvedAtMs, processingAtMs)}")
            appendLine("max_tts_ms=${longestStage(speakingAtMs, approvedAtMs)}")
            appendLine("synthetic_peer_packets_sent=${sentPackets.get() - lostPeerPackets.get()}")
            appendLine("synthetic_peer_packets_lost=${lostPeerPackets.get()}")
            appendLine("media_start_state=$mediaStartState")
            appendLine("media_start_failure=$mediaStartFailure")
            appendLine("media_start_detail_code=$mediaStartDetail")
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

    private fun longestStage(ends: LongArray, starts: LongArray): Long =
        (0 until turnLimit).maxOfOrNull { index ->
            if (starts[index] > 0L && ends[index] >= starts[index]) {
                ends[index] - starts[index]
            } else 0L
        } ?: 0L

    private fun sampleMemory() {
        val runtime = Runtime.getRuntime()
        maxJavaHeapBytes = maxOf(maxJavaHeapBytes,
            runtime.totalMemory() - runtime.freeMemory())
        maxNativeHeapBytes = maxOf(maxNativeHeapBytes,
            Debug.getNativeHeapAllocatedSize())
    }

    override fun onStop() {
        if (!stopped.get()) complete("stopped")
        super.onStop()
    }

    companion object {
        const val REPORT_FILE = "s8-native-voip-two-turn-report.txt"
        private const val MAX_TURNS = 6
        private const val PEER_SSRC = 0x14630AA1
        private const val APP_SSRC = 0x15430BB2
        private const val TIMEOUT_MS = 480_000L
        private const val MAX_SOURCE_BYTES = 16_000 * 2 * 8
        private val INPUTS = arrayOf(
            "To jest pierwsza tura lokalnego agenta tekstowego.",
            "To jest druga tura lokalnego agenta tekstowego.",
            "To jest trzecia tura lokalnego agenta tekstowego.",
            "To jest czwarta tura lokalnego agenta tekstowego.",
            "To jest piąta tura lokalnego agenta tekstowego.",
            "To jest szósta tura lokalnego agenta tekstowego.",
        )
    }
}
