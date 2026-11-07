package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import pl.michalmatu.aicallbridge.R
import android.os.Bundle
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
    private lateinit var audioPlayer: ArchivedConversationPlayer
    private var playbackStatus: TextView? = null
    private var displayedSessionId: String? = null
    private val detailBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { showList() }
    }
    internal var pendingDeleteConfirmation: AlertDialog? = null
        private set

    /** The details view is a screen within Calls, not a separate Home destination. */
    override fun consumeChromeBack(): Boolean {
        if (displayedSessionId == null) return false
        showList()
        return true
    }

    private fun setDisplayedSession(id: String?) {
        displayedSessionId = id
        detailBack.isEnabled = id != null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // One lifecycle-aware handler for both gesture and hardware Back, Android 10+.
        onBackPressedDispatcher.addCallback(this, detailBack)
        history = RecordedConversationHistory(File(filesDir, CallSessionEvidenceRecorder.ROOT))
        audioPlayer = ArchivedConversationPlayer(this, history)
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
                    if (session.invalidRecords > 0) content.addView(TextView(this).apply { text = getString(R.string.history_incomplete, session.invalidRecords) })
                    playbackStatus = TextView(this).apply {
                        text = getString(R.string.history_audio_count, session.audioFiles.size)
                    }
                    content.addView(playbackStatus)
                    session.lines.forEach { line ->
                        content.addView(conversationMessageView(this, line, ongoing = !session.complete))
                        session.audioFor(line)?.let { name ->
                            content.addView(Button(this).apply {
                                text = getString(R.string.history_play_fragment)
                                setOnClickListener { playFragment(session.id, name) }
                            })
                        }
                    }
                    val linked = session.lines.mapNotNull(session::audioFor).toSet()
                    val unlinked = session.audioFiles.values.filterNot { it in linked }
                    if (unlinked.isNotEmpty()) {
                        content.addView(TextView(this).apply {
                            text = getString(R.string.history_more_segments)
                        })
                        unlinked.forEach { name ->
                            content.addView(Button(this).apply {
                                text = getString(R.string.history_play_extra)
                                setOnClickListener { playFragment(session.id, name) }
                            })
                        }
                    }
                }.onFailure { failure -> content.addView(TextView(this).apply { text = getString(R.string.history_detail_error, failure.javaClass.simpleName) }) }
            }
        }
    }
    private fun playFragment(id: String, fileName: String) {
        val state = audioPlayer.play(id, fileName) {
            if (!isDestroyed) playbackStatus?.text = getString(R.string.history_playback_finished)
        }
        val textId = when (state) {
            ArchivedConversationPlayer.StartResult.NEEDS_PERMISSION -> R.string.history_permission_required
            ArchivedConversationPlayer.StartResult.PHONE_BUSY -> R.string.history_off_call_only
            ArchivedConversationPlayer.StartResult.MISSING_FILE -> R.string.history_clip_not_found
            ArchivedConversationPlayer.StartResult.PLAYING -> R.string.history_playing
            ArchivedConversationPlayer.StartResult.FAILED -> R.string.history_playback_error
        }
        playbackStatus?.text = getString(textId)
    }
    private fun stopPlayback() {
        if (::audioPlayer.isInitialized) audioPlayer.stop()
    }
    override fun onPause() { stopPlayback(); super.onPause() }
    override fun onDestroy() {
        pendingDeleteConfirmation?.dismiss()
        pendingDeleteConfirmation = null
        stopPlayback()
        io.shutdown()
        super.onDestroy()
    }
}
