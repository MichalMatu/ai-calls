package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
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

class AppOwnedCampaignService : Service() {
    private lateinit var authorizationStore: CampaignAuthorizationStore
    private lateinit var statusStore: CampaignRuntimeStatusStore
    private lateinit var notificationManager: NotificationManager
    private val supervisedAudioActive = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        authorizationStore = CampaignAuthorizationStore(this)
        statusStore = CampaignRuntimeStatusStore(this)
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
            ACTION_STOP_SUPERVISED_AUDIO -> {
                supervisedAudioActive.set(false)
                if (!AppOwnedClirCampaignExecutor.isRunning()) {
                    finish("Supervised audio test stopped; the cellular call remains under your control")
                }
                return START_NOT_STICKY
            }
        }
        if (intent?.action != ACTION_START_CLIR_ENABLE) {
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
        if (accountScope == null) {
            finish("Could not bind campaign to the current voice subscription")
            return START_NOT_STICKY
        }

        AppOwnedClirCampaignExecutor.start(
            context = this,
            authorizationStore = authorizationStore,
            accountScope = accountScope,
        ) { status ->
            statusStore.save(status)
            if (AppOwnedClirCampaignExecutor.isRunning()) {
                startForeground(
                    NOTIFICATION_ID,
                    notification(status),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Explicit, user-attached, read-only pilot. It never dials or disconnects a call.
     * The user must start and end the ordinary cellular call in the existing dialer.
     */
    private fun startSupervisedAudio() {
        if (AppOwnedClirCampaignExecutor.isRunning() ||
            !supervisedAudioActive.compareAndSet(false, true)
        ) {
            statusStore.save("Another call runtime is already active")
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
            ) { report ->
                supervisedAudioActive.set(false)
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
        }
    }

    override fun onDestroy() {
        supervisedAudioActive.set(false)
        super.onDestroy()
    }

    private fun finish(status: String) {
        statusStore.save(status)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(status: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AI Calls campaign")
            .setContentText(status.take(NOTIFICATION_TEXT_LIMIT))
            .setOngoing(AppOwnedClirCampaignExecutor.isRunning())
            .setOnlyAlertOnce(true)
            .build()

    companion object {
        const val ACTION_START_CLIR_ENABLE =
            "pl.michalmatu.aicallbridge.action.START_CLIR_ENABLE_CAMPAIGN"
        const val ACTION_START_SUPERVISED_AUDIO =
            "pl.michalmatu.aicallbridge.action.START_SUPERVISED_AUDIO"
        const val ACTION_STOP_SUPERVISED_AUDIO =
            "pl.michalmatu.aicallbridge.action.STOP_SUPERVISED_AUDIO"

        private const val CHANNEL_ID = "aicall_campaign"
        private const val NOTIFICATION_ID = 3101
        private const val NOTIFICATION_TEXT_LIMIT = 120
    }
}
