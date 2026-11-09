package pl.michalmatu.aicallbridge.conversation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.telecom.TelecomManager
import java.util.ArrayDeque

/**
 * Plays only private, verified archive fragments when no phone call is in progress.
 * A sequence preserves clip order, but cannot reconstruct missing manual audio or silence.
 * No recording, call control or file export is performed here.
 */
internal class ArchivedConversationPlayer(
    private val context: Context,
    private val archive: RecordedConversationHistory,
) {
    enum class StartResult { NEEDS_PERMISSION, PHONE_BUSY, MISSING_FILE, PLAYING, FAILED }

    private var current: MediaPlayer? = null
    private var playingKey: Pair<String, String>? = null
    private var sequenceId: String? = null
    private val remaining = ArrayDeque<String>()
    private var completion: (() -> Unit)? = null

    fun isPlaying(id: String, fileName: String): Boolean =
        playingKey == (id to fileName) &&
            runCatching { current?.isPlaying == true }.getOrDefault(false)

    fun isPlayingSequence(id: String): Boolean = sequenceId == id && current != null

    fun play(id: String, fileName: String, onCompleted: () -> Unit): StartResult =
        begin(id, listOf(fileName), id to fileName, null, onCompleted)

    /** Play all actually recorded segments, not a claim of uninterrupted call audio. */
    fun playAvailable(id: String, files: List<String>, onCompleted: () -> Unit): StartResult =
        begin(id, files, null, id, onCompleted)

    private fun phoneReady(): StartResult? {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) return StartResult.NEEDS_PERMISSION
        val telecom = context.getSystemService(TelecomManager::class.java)
        if (runCatching { telecom == null || telecom.isInCall }.getOrDefault(true)) {
            return StartResult.PHONE_BUSY
        }
        return null
    }

    private fun begin(
        id: String, files: List<String>, key: Pair<String, String>?,
        session: String?, onCompleted: () -> Unit,
    ): StartResult {
        phoneReady()?.let { return it }
        if (files.isEmpty() || files.any { archive.resolveAudioFile(id, it) == null }) {
            return StartResult.MISSING_FILE
        }
        return runCatching {
            stop()
            playingKey = key
            sequenceId = session
            completion = onCompleted
            remaining.addAll(files)
            if (startNext(id)) StartResult.PLAYING else StartResult.FAILED
        }.getOrElse {
            stop()
            StartResult.FAILED
        }
    }

    private fun startNext(id: String): Boolean {
        if (phoneReady() != null) { stop(); return false }
        val name = remaining.pollFirst()
        if (name == null) {
            val done = completion
            stop()
            done?.invoke()
            return true
        }
        val file = archive.resolveAudioFile(id, name) ?: run { stop(); return false }
        return try {
            val player = MediaPlayer()
            current = player
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                if (current === it) {
                    current = null
                    it.release()
                    startNext(id)
                } else {
                    it.release()
                }
            }
            player.setOnErrorListener { source, _, _ ->
                if (current === source) stop() else source.release()
                true
            }
            player.prepare()
            player.start()
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        remaining.clear()
        completion = null
        playingKey = null
        sequenceId = null
        val previous = current
        current = null
        runCatching { previous?.release() }
    }
}
