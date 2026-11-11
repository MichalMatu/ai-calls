package pl.michalmatu.aicallbridge.textagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FilterInputStream
import java.io.InterruptedIOException
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.R

/** One app-owned transfer survives activity recreation. Never selects a model or starts a call. */
internal class LanguageModelTransferService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var cancelled = false
    @Volatile private var downloader: AndroidGemma4ModelDownloader? = null

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.models_ux05_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            cancelled = true
            downloader?.cancel()
            return START_NOT_STICKY
        }
        val importingUserModel = intent?.getBooleanExtra(USER_IMPORT, false) == true
        val uri = intent?.getStringExtra(URI)?.let(Uri::parse)
        val id = if (importingUserModel) DialogueModelId.USER_LITERT else
            DialogueModelId.entries.firstOrNull { it.name == intent?.getStringExtra(MODEL) }
        if (id == null || (importingUserModel && uri == null) ||
            (!importingUserModel && id == DialogueModelId.USER_LITERT)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val model = if (importingUserModel) null else LanguageModelCatalog[id]
        val importing = uri != null
        val granted = if (importing) operationGate.tryBeginImport()
            else operationGate.tryBeginDownload()
        if (!granted) return START_NOT_STICKY
        cancelled = false
        status = TransferStatus(id, true, 0, getString(R.string.models_ux05_prepare))
        startForeground(NOTIFICATION, notification(status.message))
        executor.execute {
            val message = try {
                if (importingUserModel) {
                    val name = contentResolver.query(checkNotNull(uri),
                        arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: uri.lastPathSegment.orEmpty()
                    val source = contentResolver.openInputStream(uri)
                    if (source == null) "model_source_unreadable" else source.use {
                        when (val result = UserLiteRtModelStore(this).import(name, it,
                            cancelled = { cancelled }, onProgress = ::publishUser)) {
                            is UserModelImportResult.Installed ->
                                getString(R.string.models_ui_custom_import_success, result.model.title)
                            is UserModelImportResult.Failure -> result.reason
                        }
                    }
                } else {
                    val entry = checkNotNull(model)
                    val result = if (uri == null) {
                        val transfer = AndroidGemma4ModelDownloader(this, entry)
                        downloader = transfer
                        if (cancelled) Gemma4ModelInstallResult.Failure("model_download_cancelled")
                        else transfer.download { publish(entry, it.bytesRead) }
                    } else {
                        val source = contentResolver.openInputStream(uri)
                        if (source == null) Gemma4ModelInstallResult.Failure("model_source_unreadable")
                        else source.use { original ->
                            var bytes = 0L
                            val progress = object : FilterInputStream(original) {
                                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                                    if (cancelled) throw InterruptedIOException("cancelled")
                                    return super.read(buffer, offset, length).also {
                                        if (it > 0) { bytes += it; publish(entry, bytes) }
                                    }
                                }
                            }
                            Gemma4ModelInstaller(
                                checkNotNull(Gemma4LiteRtTextBackendFactory.modelFile(this, id).parentFile),
                                entry.spec,
                            ).install(progress)
                        }
                    }
                    when {
                        result is Gemma4ModelInstallResult.Success ->
                            getString(R.string.models_ux05_installed, entry.title)
                        cancelled -> getString(R.string.models_ux05_cancelled)
                        result is Gemma4ModelInstallResult.Failure -> "${entry.title}: ${result.reason}"
                        else -> "model_transfer_unknown"
                    }
                }
            } catch (error: Exception) {
                "model_transfer_${error.javaClass.simpleName}"
            } finally {
                downloader = null
                if (importing) operationGate.finishImport() else operationGate.finishDownload()
            }
            status = TransferStatus(id, false, status.bytes,
                if (cancelled) getString(R.string.models_ux05_cancelled) else message)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun publishUser(bytes: Long) {
        val previous = status
        if (bytes - previous.bytes < 4 * 1024 * 1024) return
        val message = getString(R.string.models_ui_custom_progress, bytes / 1_000_000L)
        status = TransferStatus(DialogueModelId.USER_LITERT, true, bytes, message)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(message))
    }

    private fun publish(model: LanguageModelEntry, bytes: Long) {
        val previous = status
        if (bytes - previous.bytes < 4 * 1024 * 1024 && bytes != model.spec.expectedBytes) return
        val message = "${model.title}: ${bytes / 1_000_000} / ${checkNotNull(model.spec.expectedBytes) / 1_000_000} MB"
        status = TransferStatus(model.id, true, bytes, message)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(message))
    }

    private fun notification(message: String): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(getString(R.string.models_ux05_notification)).setContentText(message)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, LanguageModelActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .setOngoing(true).build()

    override fun onDestroy() {
        cancelled = true
        downloader?.cancel()
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val MODEL = "model_id"
        const val URI = "source_uri"
        const val USER_IMPORT = "import_user_litert"
        const val CANCEL = "cancel_model_transfer"
        private const val CHANNEL = "language_models"
        private const val NOTIFICATION = 1900
        private val operationGate = Gemma4ModelOperationGate()
        @Volatile var status = TransferStatus()
            private set
    }
}

internal data class TransferStatus(
    val id: DialogueModelId? = null, val busy: Boolean = false,
    val bytes: Long = 0, val message: String = "",
)
