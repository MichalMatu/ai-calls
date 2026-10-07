package pl.michalmatu.aicallbridge.campaign

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

class AppOwnedCampaignService : Service() {
    private lateinit var authorizationStore: CampaignAuthorizationStore
    private lateinit var statusStore: CampaignRuntimeStatusStore
    private lateinit var notificationManager: NotificationManager

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
        if (intent?.action != ACTION_START_CLIR_ENABLE) {
            stopSelf(startId)
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

        private const val CHANNEL_ID = "aicall_campaign"
        private const val NOTIFICATION_ID = 3101
        private const val NOTIFICATION_TEXT_LIMIT = 120
    }
}
