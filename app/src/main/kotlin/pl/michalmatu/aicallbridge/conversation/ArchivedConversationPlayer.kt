package pl.michalmatu.aicallbridge.conversation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.telecom.TelecomManager

/**
 * Audio playback boundary for private archived files, never a call controller.
 * The view only renders a typed status and receives completion notifications.
 */
internal class ArchivedConversationPlayer(
    private val context: Context,
    private val archive: RecordedConversationHistory,
) {
    enum class StartResult { NEEDS_PERMISSION, PHONE_BUSY, MISSING_FILE, PLAYING, FAILED }

    private var current: MediaPlayer? = null

    fun play(id: String, fileName: String, onCompleted: () -> Unit): StartResult {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) return StartResult.NEEDS_PERMISSION

        val telecom = context.getSystemService(TelecomManager::class.java)
        if (runCatching { telecom == null || telecom.isInCall }.getOrDefault(true)) {
            return StartResult.PHONE_BUSY
        }

        val file = archive.resolveAudioFile(id, fileName) ?: return StartResult.MISSING_FILE
        return runCatching {
            stop()
            val player = MediaPlayer()
            current = player
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                if (current === it) {
                    current = null
                    it.release()
                    onCompleted()
                } else {
                    it.release()
                }
            }
            player.prepare()
            player.start()
            StartResult.PLAYING
        }.getOrElse {
            stop()
            StartResult.FAILED
        }
    }

    fun stop() {
        runCatching { current?.release() }
        current = null
    }
}
