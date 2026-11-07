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
        content.tag = "history-list"
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
                    var lastDay: String? = null
                    sessions.forEach { session ->
                        val day = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(session.startedAtEpochMs))
                        if (day != lastDay) {
                            content.addView(UiDesign.heading(this, day).apply { textSize = 16f }, UiDesign.spaceAfter(this, 8))
                            lastDay = day
                        }
                        val direction = when (session.direction) {
                            ConversationDirection.OUTGOING -> getString(R.string.history_outgoing)
                            ConversationDirection.INCOMING -> getString(R.string.history_incoming)
                            ConversationDirection.UNSPECIFIED -> getString(R.string.history_unknown)
                        }
                        val icon = when (session.direction) {
                            ConversationDirection.OUTGOING -> R.drawable.ic_aicalls_phone_outgoing
                            ConversationDirection.INCOMING -> R.drawable.ic_aicalls_phone_incoming
                            ConversationDirection.UNSPECIFIED -> R.drawable.ic_aicalls_phone
                        }
                        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(session.startedAtEpochMs))
                        val time = DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(Date(session.startedAtEpochMs))
                        val count = session.lines.size.toString() + " " + getString(R.string.history_lines)
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            tag = "history-row:" + session.id
                        }
                        val open = LinearLayout(this).apply {
                            tag = "history-open:" + session.id
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            minimumHeight = UiDesign.dp(this@ConversationHistoryActivity, 56)
                            isClickable = true
                            isFocusable = true
                            contentDescription = direction + " • " + date + " • " + count
                            background = UiDesign.touchSurface(this@ConversationHistoryActivity)
                            setPadding(UiDesign.dp(this@ConversationHistoryActivity, 12),
                                UiDesign.dp(this@ConversationHistoryActivity, 8),
                                UiDesign.dp(this@ConversationHistoryActivity, 10),
                                UiDesign.dp(this@ConversationHistoryActivity, 8))
                            setOnClickListener { showSession(session.id) }
                        }
                        open.addView(ImageView(this).apply {
                            setImageResource(icon)
                            setColorFilter(UiDesign.secondary)
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        }, LinearLayout.LayoutParams(UiDesign.dp(this, 24), UiDesign.dp(this, 24)).apply {
                            marginEnd = UiDesign.dp(this@ConversationHistoryActivity, 12)
                        })
                        open.addView(LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(TextView(this@ConversationHistoryActivity).apply {
                                text = direction + " · " + time
                                textSize = 15f
                                setTextColor(UiDesign.ink)
                                isSingleLine = true
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            })
                            addView(TextView(this@ConversationHistoryActivity).apply {
                                text = count
                                textSize = 12f
                                setTextColor(UiDesign.secondary)
                            })
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        open.addView(ImageView(this).apply {
                            setImageResource(R.drawable.ic_aicalls_chevron_right)
                            setColorFilter(UiDesign.secondary)
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        }, LinearLayout.LayoutParams(UiDesign.dp(this, 20), UiDesign.dp(this, 20)))
                        row.addView(open, LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        if (history.canDelete(session.id)) {
                            row.addView(UiDesign.iconButton(
                                this, R.drawable.ic_aicalls_trash, getString(R.string.history_delete),
                                UiDesign.secondary
                            ).apply {
                                tag = "history-delete:" + session.id
                                setOnClickListener { confirmDelete(session.id) }
                            }, LinearLayout.LayoutParams(UiDesign.dp(this, 48),
                                UiDesign.dp(this, 48)).apply {
                                    marginStart = UiDesign.dp(this@ConversationHistoryActivity, 4)
                                })
                        }
                        content.addView(row, LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = UiDesign.dp(this@ConversationHistoryActivity, 6) })
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
        content.tag = "history-detail"
        io.execute {
            val result = runCatching { history.load(id) }
            runOnUiThread {
                if (isDestroyed || displayedSessionId != id) return@runOnUiThread
                content.removeAllViews()
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
                        val extraClips = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            visibility = View.GONE
                            tag = "history-extra-clips"
                        }
                        val toggle = UiDesign.button(this,
                            getString(R.string.history_more_segments_collapsed, unlinked.size)) {
                            val expanded = extraClips.visibility != View.VISIBLE
                            extraClips.visibility = if (expanded) View.VISIBLE else View.GONE
                        }.apply { tag = "history-extra-toggle" }
                        // The header changes with visibility; no duplicate rows in the collapsed state.
                        toggle.setOnClickListener {
                            val expanded = extraClips.visibility != View.VISIBLE
                            extraClips.visibility = if (expanded) View.VISIBLE else View.GONE
                            toggle.text = getString(
                                if (expanded) R.string.history_more_segments_expanded
                                else R.string.history_more_segments_collapsed, unlinked.size)
                        }
                        content.addView(toggle)
                        unlinked.forEachIndexed { index, name ->
                            extraClips.addView(Button(this).apply {
                                tag = "history-extra-play:" + index
                                text = getString(R.string.history_play_extra_numbered, index + 1)
                                setOnClickListener { playFragment(session.id, name) }
                            }, LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = UiDesign.dp(this@ConversationHistoryActivity, 10) })
                        }
                        content.addView(extraClips)
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
