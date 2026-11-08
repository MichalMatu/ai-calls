package pl.michalmatu.aicallbridge.audioengines

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity

/** Foreground 615-MB model transfer. Independent of selected STT and Activity lifecycle. */
internal class ParakeetModelTransferService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var downloader: ParakeetModelDownloader? = null
    @Volatile private var cancelRequested = false

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.ux07_parakeet_title),
                NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            cancelRequested = true
            downloader?.cancel()
            return START_NOT_STICKY
        }
        val verifying = intent?.action == VERIFY
        if ((intent?.action != START && !verifying) || status.busy) return START_NOT_STICKY
        cancelRequested = false
        startForeground(NOTIFICATION, notification(getString(if (verifying)
            R.string.ux08_parakeet_verifying else R.string.ux07_parakeet_downloading)))
        status = ParakeetTransferStatus(busy = true, verifying = verifying)
        val transfer = if (verifying) null else ParakeetModelDownloader(this)
        downloader = transfer
        executor.execute {
            val result = try {
                if (cancelRequested) ParakeetModelDownloadResult.Failure("cancelled")
                else if (verifying) {
                    val candidate = ParakeetModelFile.resolve(this)
                    if (ParakeetModelFile.validPinnedModel(candidate))
                        ParakeetModelDownloadResult.Success
                    else ParakeetModelDownloadResult.Failure("sha256_or_size_mismatch")
                } else checkNotNull(transfer).download { read, _ ->
                    val previous = status
                    if (read - previous.bytes >= 4L * 1024 * 1024 ||
                        read == ParakeetModelFile.MIN_SIZE_BYTES) {
                        status = ParakeetTransferStatus(busy = true, bytes = read)
                        getSystemService(NotificationManager::class.java).notify(
                            NOTIFICATION, notification(getString(R.string.ux07_parakeet_progress,
                                (read * 100L / ParakeetModelFile.MIN_SIZE_BYTES).toInt().coerceIn(0,100))))
                    }
                }
            } catch (error: Exception) {
                ParakeetModelDownloadResult.Failure("transfer_" + error.javaClass.simpleName)
            }
            downloader = null
            status = when (result) {
                ParakeetModelDownloadResult.Success ->
                    ParakeetTransferStatus(verified = true, bytes = ParakeetModelFile.MIN_SIZE_BYTES)
                is ParakeetModelDownloadResult.Failure -> ParakeetTransferStatus(
                    verificationFailed = verifying && result.reason == "sha256_or_size_mismatch",
                    error = result.reason)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(message: String): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(getString(R.string.ux07_parakeet_title))
        .setContentText(message)
        .setContentIntent(PendingIntent.getActivity(this, 0,
            Intent(this, LanguageModelActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .setOngoing(true).build()

    override fun onDestroy() {
        downloader?.cancel()
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val START = "download_parakeet_model"
        const val VERIFY = "verify_parakeet_model"
        const val CANCEL = "cancel_parakeet_model"
        private const val CHANNEL = "parakeet_model_transfer"
        private const val NOTIFICATION = 1901
        @Volatile var status: ParakeetTransferStatus = ParakeetTransferStatus()
            private set
    }
}

internal data class ParakeetTransferStatus(
    val busy: Boolean = false,
    val bytes: Long = 0,
    val verified: Boolean = false,
    val verifying: Boolean = false,
    val verificationFailed: Boolean = false,
    val error: String = "",
)
