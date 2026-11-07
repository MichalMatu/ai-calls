package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import pl.michalmatu.aicallbridge.R
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.telecom.TelecomManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder

class ConversationHistoryActivity : UiChromeActivity(AppTab.HISTORY) {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    private lateinit var history: RecordedConversationHistory
    private var playback: MediaPlayer? = null
    private var playbackStatus: TextView? = null
    private var displayedSessionId: String? = null
    private var gestureBack: OnBackInvokedCallback? = null
    internal var pendingDeleteConfirmation: AlertDialog? = null
        private set

    /** The details view is a screen within Calls, not a separate Home destination. */
    override fun consumeChromeBack(): Boolean {
        if (displayedSessionId == null) return false
        showList()
        return true
    }

    @Deprecated("Pre-33 fallback; modern back uses OnBackInvokedDispatcher.")
    override fun onBackPressed() {
        if (!consumeChromeBack()) super.onBackPressed()
    }

    private fun setDisplayedSession(id: String?) {
        displayedSessionId = id
        if (Build.VERSION.SDK_INT >= 33) {
            gestureBack?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            gestureBack = null
            if (id != null) {
                val callback = OnBackInvokedCallback { showList() }
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
                gestureBack = callback
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        history = RecordedConversationHistory(File(filesDir, CallSessionEvidenceRecorder.ROOT))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(UiDesign.dp(this@ConversationHistoryActivity, 24), UiDesign.dp(this@ConversationHistoryActivity, 32), UiDesign.dp(this@ConversationHistoryActivity, 24), UiDesign.dp(this@ConversationHistoryActivity, 32)) }
        setContentView(ScrollView(this).apply { addView(content) })
        showList()
    }
    private fun showList(message: String? = null) {
        setDisplayedSession(null)
        stopPlayback()
        playbackStatus = null
        content.removeAllViews()
        content.addView(TextView(this).apply { text = getString(R.string.history_title); textSize = 22f })
        content.addView(TextView(this).apply { text = getString(R.string.history_subtitle) })
        if (message != null) content.addView(TextView(this).apply { text = message })
        io.execute {
            val result = runCatching { history.list() }
            runOnUiThread {
                if (isDestroyed || displayedSessionId != null) return@runOnUiThread
                result.onSuccess { sessions ->
                    if (sessions.isEmpty()) content.addView(TextView(this).apply {
                        text = getString(R.string.history_empty)
                    })
                    sessions.forEach { session ->
                        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                        row.addView(Button(this).apply {
                            tag = "history-open:" + session.id
                            val direction = when (session.direction) {
                                ConversationDirection.OUTGOING -> getString(R.string.history_outgoing)
                                ConversationDirection.INCOMING -> getString(R.string.history_incoming)
                                ConversationDirection.UNSPECIFIED -> getString(R.string.history_unknown)
                            }
                            text = direction + " • " +
                                DateFormat.getDateTimeInstance().format(Date(session.startedAtEpochMs)) +
                                " • " + session.lines.size + " " + getString(R.string.history_lines)
                            setOnClickListener { showSession(session.id) }
                        })
                        if (history.canDelete(session.id)) {
                            row.addView(Button(this).apply {
                                tag = "history-delete:" + session.id
                                text = getString(R.string.history_delete)
                                setOnClickListener { confirmDelete(session.id) }
                            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                    topMargin = UiDesign.dp(this@ConversationHistoryActivity, 8)
                                })
                        }
                        content.addView(row, LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = UiDesign.dp(this@ConversationHistoryActivity, 14) })
                    }
                }.onFailure { failure -> content.addView(TextView(this).apply {
                    text = getString(R.string.history_read_error) + ": " + failure.javaClass.simpleName
                }) }
            }
        }
    }

    private fun confirmDelete(id: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.history_delete_title)
            .setMessage(R.string.history_delete_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.history_delete) { _, _ ->
                stopPlayback()
                io.execute {
                    val deleted = runCatching { history.deleteCompleted(id) }.getOrDefault(false)
                    runOnUiThread {
                        if (!isDestroyed) showList(getString(
                            if (deleted) R.string.history_deleted else R.string.history_delete_failed))
                    }
                }
            }
            .create()
        pendingDeleteConfirmation = dialog
        dialog.setOnDismissListener { if (pendingDeleteConfirmation === dialog) pendingDeleteConfirmation = null }
        dialog.show()
    }
    private fun showSession(id: String) {
        setDisplayedSession(id)
        io.execute {
            val result = runCatching { history.load(id) }
            runOnUiThread {
                if (isDestroyed || displayedSessionId != id) return@runOnUiThread
                content.removeAllViews()
                content.addView(Button(this).apply {
                    tag = "history-back-to-list"
                    text = getString(R.string.history_back_to_list)
                    setOnClickListener { showList() }
                })
                result.onSuccess { session ->
                    if (session.invalidRecords > 0) content.addView(TextView(this).apply { text = "Niepełne dane archiwum: ${session.invalidRecords} uszkodzonych wpisów" })
                    playbackStatus = TextView(this).apply {
                        text = "Dostępne fragmenty audio: " + session.audioFiles.size +
                            ". Nie jest to nagranie całej sesji."
                    }
                    content.addView(playbackStatus)
                    session.lines.forEach { line ->
                        content.addView(conversationMessageView(this, line, ongoing = !session.complete))
                        session.audioFor(line)?.let { name ->
                            content.addView(Button(this).apply {
                                text = "▶ Odtwórz fragment"
                                setOnClickListener { playFragment(session.id, name) }
                            })
                        }
                    }
                    val linked = session.lines.mapNotNull(session::audioFor).toSet()
                    val unlinked = session.audioFiles.values.filterNot { it in linked }
                    if (unlinked.isNotEmpty()) {
                        content.addView(TextView(this).apply {
                            text = "Inne dostępne segmenty, w tym krótkie potwierdzenia AI:"
                        })
                        unlinked.forEach { name ->
                            content.addView(Button(this).apply {
                                text = "▶ Odtwórz dodatkowy segment"
                                setOnClickListener { playFragment(session.id, name) }
                            })
                        }
                    }
                }.onFailure { failure -> content.addView(TextView(this).apply { text = "Błąd odczytu: ${failure.javaClass.simpleName}" }) }
            }
        }
    }
    private fun playFragment(id: String, fileName: String) {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            playbackStatus?.text = "Do odsłuchu potrzebna jest zgoda Androida na odczyt stanu telefonu."
            return
        }
        val telecom = getSystemService(TelecomManager::class.java)
        if (runCatching { telecom == null || telecom.isInCall }.getOrDefault(true)) {
            playbackStatus?.text = "Odsłuch możliwy tylko poza połączeniem telefonicznym."
            return
        }
        val file = history.resolveAudioFile(id, fileName)
        if (file == null) {
            playbackStatus?.text = "Nie znaleziono bezpiecznego lokalnego fragmentu WAV."
            return
        }
        runCatching {
            stopPlayback()
            val player = MediaPlayer()
            playback = player
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                it.release()
                if (playback === it) {
                    playback = null
                    playbackStatus?.text = "Odsłuch fragmentu zakończony."
                }
            }
            player.prepare()
            player.start()
            playbackStatus?.text = "Odtwarzanie zapisanego fragmentu audio."
        }.onFailure {
            stopPlayback()
            playbackStatus?.text = "Nie można odtworzyć fragmentu audio."
        }
    }
    private fun stopPlayback() {
        runCatching { playback?.release() }
        playback = null
    }
    override fun onPause() { stopPlayback(); super.onPause() }
    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            gestureBack?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            gestureBack = null
        }
        pendingDeleteConfirmation?.dismiss()
        pendingDeleteConfirmation = null
        stopPlayback()
        io.shutdown()
        super.onDestroy()
    }
}
