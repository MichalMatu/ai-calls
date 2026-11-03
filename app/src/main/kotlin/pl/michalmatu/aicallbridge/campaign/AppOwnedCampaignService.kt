package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.telecom.TelecomManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import pl.michalmatu.aicallbridge.developerrelay.ChatRelayLiveCallProbe
import rikka.shizuku.Shizuku
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Handler
import android.os.Looper

class AppOwnedCampaignService : Service() {
    private lateinit var authorizationStore: CampaignAuthorizationStore
    private lateinit var statusStore: CampaignRuntimeStatusStore
    private lateinit var continuationStore: CampaignContinuationStore
    private val continuationHandler = Handler(Looper.getMainLooper())
    private var scheduledAttempt: Runnable? = null
    private var campaignRequested = false
    private var attemptOutcome: Pair<Boolean, String?>? = null
    private var awaitingPhoneIdle = false
    private var continuationGeneration = 0L
    private lateinit var notificationManager: NotificationManager
    private val supervisedAudioActive = AtomicBoolean(false)
    private var supervisedAudioSession: ChatRelayLiveCallProbe.SessionControl? = null

    override fun onCreate() {
        super.onCreate()
        authorizationStore = CampaignAuthorizationStore(this)
        statusStore = CampaignRuntimeStatusStore(this)
        continuationStore = CampaignContinuationStore(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "AI Calls campaign",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SUPERVISED_AUDIO -> {
                startSupervisedAudio()
                return START_NOT_STICKY
            }
            ACTION_STOP_SUPERVISED_AUDIO, ACTION_STOP_ALL_AI -> {
                campaignRequested = false
                continuationGeneration += 1
                continuationStore.clear()
                scheduledAttempt?.let { continuationHandler.removeCallbacks(it) }
                scheduledAttempt = null
                supervisedAudioActive.set(false)
                supervisedAudioSession?.stopAudio()
                supervisedAudioSession = null
                var ownedStatus: String? = null
                AppOwnedClirCampaignExecutor.stop { ownedStatus = it }
                finish(ownedStatus ?: "STOP AI: app audio stopped; external calls remain untouched")
                return START_NOT_STICKY
            }
        }
        // A service restarted by Android may resume only a persisted, previously started
        // exact-grant campaign. An ordinary null/unknown command never grants new authority.
        val explicitStart = intent?.action == ACTION_START_CLIR_ENABLE
        val restoring = intent == null && continuationStore.load() != null
        if (!explicitStart && !restoring) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (supervisedAudioActive.get()) {
            statusStore.save("Cannot start a campaign during the supervised audio pilot")
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            notification("Preparing authorized campaign"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        val accountScope = CampaignAccountScope.currentVoiceSubscription(this)
        val grant = authorizationStore.load()
        val now = System.currentTimeMillis()
        val authorized = grant != null && accountScope != null &&
            CampaignAuthorizationPolicy.authorize(
                grant, ClirCampaignSpec.enableRequest(accountScope), now,
            ) is CampaignAuthorizationDecision.Authorized
        if (!authorized || grant == null || accountScope == null) {
            finish("Campaign authorization or voice subscription unavailable")
            return START_NOT_STICKY
        }

        val saved = continuationStore.load()
        if (!explicitStart && (saved == null || saved.grantId != grant.grantId ||
                saved.accountScope != accountScope)
        ) {
            finish("Saved campaign scope does not match the current approved grant")
            return START_NOT_STICKY
        }
        val disposition = CampaignStartReconciliationPolicy.decide(
            saved = saved,
            requestedGrantId = grant.grantId,
            requestedAccountScope = accountScope,
            ownedCallRunning = AppOwnedClirCampaignExecutor.isRunning(),
        )
        when (disposition) {
            CampaignStartDisposition.BEGIN_NEW,
            CampaignStartDisposition.QUEUE_REPLACEMENT -> {
                // A changed, separately authorized grant supersedes the previous
                // pending campaign. The previous exact owned call must finish
                // before the replacement is allowed to start.
                continuationGeneration += 1
                continuationStore.begin(grant.grantId, accountScope, now)
            }
            CampaignStartDisposition.REUSE_SAVED -> Unit // Keep existing due time.
            CampaignStartDisposition.ALREADY_RUNNING -> {
                if (campaignRequested) {
                    statusStore.save("Autoryzowana rozmowa już trwa; bez ponownego wybierania numeru")
                    return START_STICKY
                }
                // A recreated Service may not own the old executor's callbacks.
                // Reconcile from phone IDLE instead of stranding the saved task.
            }
        }
        campaignRequested = true
        scheduleAuthorizedAttempt()
        return START_STICKY
    }

    private fun scheduleAuthorizedAttempt() {
        scheduledAttempt?.let { continuationHandler.removeCallbacks(it) }
        val state = continuationStore.load()
        if (!campaignRequested || state == null || !isContinuationAuthorized(state)) {
            finish("Campaign no longer authorized; no further calls")
            return
        }
        val delay = (state.nextAttemptAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        statusStore.save("Autoryzowana kontynuacja: następna próba za ${delay / 1_000L} s")
        startForeground(
            NOTIFICATION_ID,
            notification(statusStore.load().orEmpty()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        val runnable = Runnable { startAuthorizedAttempt() }
        scheduledAttempt = runnable
        continuationHandler.postDelayed(runnable, delay)
    }

    private fun isContinuationAuthorized(state: CampaignContinuationState): Boolean {
        val grant = authorizationStore.load() ?: return false
        if (grant.grantId != state.grantId ||
            CampaignAccountScope.currentVoiceSubscription(this) != state.accountScope
        ) return false
        return CampaignAuthorizationPolicy.authorize(
            grant,
            ClirCampaignSpec.enableRequest(state.accountScope),
            System.currentTimeMillis(),
        ) is CampaignAuthorizationDecision.Authorized
    }

    private fun startAuthorizedAttempt() {
        scheduledAttempt = null
        val state = continuationStore.load()
        if (!campaignRequested || state == null || !isContinuationAuthorized(state)) {
            finish("Campaign stopped or authorization expired")
            return
        }
        // Never consume or abandon the next attempt while an earlier owned call
        // or any cellular call remains active. This also handles replacement of
        // a grant during an old call without hanging the new task indefinitely.
        val ownedCallRunning = AppOwnedClirCampaignExecutor.isRunning()
        val phoneBusy = runCatching {
            getSystemService(TelecomManager::class.java)?.isInCall == true
        }.getOrDefault(false)
        if (ownedCallRunning || phoneBusy) {
            awaitPhoneIdle()
            return
        }
        awaitingPhoneIdle = false
        continuationStore.prepareAttempt(state.grantId, System.currentTimeMillis())
            ?: run { finish("Continuation state unavailable"); return }
        attemptOutcome = null
        val generation = continuationGeneration
        AppOwnedClirCampaignExecutor.start(
            context = this,
            authorizationStore = authorizationStore,
            accountScope = state.accountScope,
            onAttemptFinished = { success, reason ->
                continuationHandler.post {
                    if (campaignRequested && continuationGeneration == generation) {
                        attemptOutcome = success to reason
                    }
                }
            },
        ) { status ->
            continuationHandler.post { handleAttemptStatus(status, state.grantId, generation) }
        }
    }

    private fun awaitPhoneIdle() {
        if (!awaitingPhoneIdle) {
            awaitingPhoneIdle = true
            statusStore.save("Autoryzowane zadanie czeka na zakończenie trwającej rozmowy")
        }
        val generation = continuationGeneration
        val retry = Runnable {
            if (campaignRequested && continuationGeneration == generation) {
                startAuthorizedAttempt()
            }
        }
        scheduledAttempt = retry
        continuationHandler.postDelayed(retry, PHONE_IDLE_RECHECK_MS)
    }

    private fun handleAttemptStatus(status: String, grantId: String, generation: Long) {
        // A late result from STOP or a previous run must never overwrite the
        // current diagnostic state or schedule an unexpected follow-up call.
        if (!campaignRequested || continuationGeneration != generation ||
            continuationStore.load()?.grantId != grantId
        ) return
        statusStore.save(status)
        if (AppOwnedClirCampaignExecutor.isRunning()) {
            startForeground(
                NOTIFICATION_ID,
                notification(status),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            return
        }
        val outcome = attemptOutcome
        attemptOutcome = null
        // A setup/readiness failure is not a reason to blindly redial; it needs a
        // working device. An ended, incomplete authorized conversation can resume.
        if (outcome != null && !outcome.first) {
            val state = continuationStore.planRetry(grantId, System.currentTimeMillis())
            if (state != null && isContinuationAuthorized(state)) {
                scheduleAuthorizedAttempt()
                return
            }
        }
        finish(status)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Explicit, user-attached, read-only pilot. It never dials or disconnects a call.
     * The user must start and end the ordinary cellular call in the existing dialer.
     */
    private fun startSupervisedAudio() {
        // A queued retry is still an active owner-authorized campaign even between
        // calls. An unrelated pilot must not tear it down through finish()/STOP.
        if (campaignRequested || continuationStore.load() != null ||
            AppOwnedClirCampaignExecutor.isRunning() ||
            !supervisedAudioActive.compareAndSet(false, true)
        ) {
            statusStore.save("Campaign or another audio runtime is active; current task continues")
            return
        }
        val telecom = getSystemService(TelecomManager::class.java)
        val audio = getSystemService(AudioManager::class.java)
        val permissionsReady = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
        ).all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!permissionsReady) {
            supervisedAudioActive.set(false)
            finish("Read-only audio pilot blocked: microphone and phone-state permission required")
            return
        }
        startForeground(
            NOTIFICATION_ID,
            notification("Read-only audio pilot on user-controlled call"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        val shizukuReady = runCatching {
            Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val alreadyInCall = permissionsReady && runCatching {
            telecom?.isInCall == true && audio?.mode == AudioManager.MODE_IN_CALL
        }.getOrDefault(false)
        if (!permissionsReady || !shizukuReady || !alreadyInCall) {
            supervisedAudioActive.set(false)
            finish("Read-only pilot blocked: attach to an existing call with phone/mic/Shizuku ready")
            return
        }
        val sessionId = "supervised-" + UUID.randomUUID().toString().take(18)
        statusStore.save("Read-only audio pilot attached; no dial, hangup, account change or PHONE disclosure")
        val result = runCatching {
            ChatRelayLiveCallProbe.run(
                context = this,
                sessionId = sessionId,
                maxTurns = 2,
                phoneDisclosureAuthorized = false,
                supervisorRelayEnabled = false,
                liveAuthorization = {
                    supervisedAudioActive.get() &&
                        runCatching {
                            telecom?.isInCall == true &&
                                audio?.mode == AudioManager.MODE_IN_CALL
                        }.getOrDefault(false)
                },
                readOnlySupervised = true,
                supervisedGemmaEnabled = true,
            ) { report ->
                supervisedAudioActive.set(false)
                supervisedAudioSession = null
                val reason = report.lineSequence()
                    .firstOrNull { it.startsWith("failure_reason=") }
                    ?.substringAfter('=')?.take(80)
                val status = if (reason.isNullOrBlank()) {
                    "Read-only audio pilot finished; cellular call not modified or disconnected"
                } else {
                    "Read-only audio pilot ended ($reason); cellular call not modified or disconnected"
                }
                finish(status)
            }
        }
        if (result.isFailure) {
            supervisedAudioActive.set(false)
            finish("Read-only audio pilot failed to start; cellular call remains untouched")
        } else if (supervisedAudioActive.get()) {
            supervisedAudioSession = result.getOrNull()
        }
    }

    override fun onDestroy() {
        supervisedAudioActive.set(false)
        supervisedAudioSession?.stopAudio()
        supervisedAudioSession = null
        scheduledAttempt?.let { continuationHandler.removeCallbacks(it) }
        scheduledAttempt = null
        continuationGeneration += 1
        AppOwnedClirCampaignExecutor.stop { statusStore.save(it) }
        super.onDestroy()
    }

    private fun finish(status: String) {
        campaignRequested = false
        awaitingPhoneIdle = false
        continuationGeneration += 1
        scheduledAttempt?.let { continuationHandler.removeCallbacks(it) }
        scheduledAttempt = null
        continuationStore.clear()
        statusStore.save(status)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(status: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AI Calls campaign")
            .setContentText(status.take(NOTIFICATION_TEXT_LIMIT))
            .setOngoing(AppOwnedClirCampaignExecutor.isRunning() || campaignRequested)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "STOP AI",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, AppOwnedCampaignService::class.java)
                        .setAction(ACTION_STOP_ALL_AI),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    companion object {
        const val ACTION_START_CLIR_ENABLE =
            "pl.michalmatu.aicallbridge.action.START_CLIR_ENABLE_CAMPAIGN"
        const val ACTION_START_SUPERVISED_AUDIO =
            "pl.michalmatu.aicallbridge.action.START_SUPERVISED_AUDIO"
        const val ACTION_STOP_SUPERVISED_AUDIO =
            "pl.michalmatu.aicallbridge.action.STOP_SUPERVISED_AUDIO"
        const val ACTION_STOP_ALL_AI =
            "pl.michalmatu.aicallbridge.action.STOP_ALL_AI"

        private const val CHANNEL_ID = "aicall_campaign"
        private const val NOTIFICATION_ID = 3101
        private const val NOTIFICATION_TEXT_LIMIT = 120
        private const val PHONE_IDLE_RECHECK_MS = 2_000L
    }
}
