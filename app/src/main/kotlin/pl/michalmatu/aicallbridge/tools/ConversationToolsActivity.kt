package pl.michalmatu.aicallbridge.tools

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteKind
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiActionTone

/** Presentation and user interactions only; capability checks remain in app-owned policy. */
class ConversationToolsActivity : UiChromeActivity(AppTab.AGENT) {
    override fun toolbarTitle(): String = getString(R.string.tools_ui_title)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var notebookContent: LinearLayout
    private lateinit var controller: ConversationToolsSettingsController
    private val refresh = object : Runnable {
        override fun run() {
            status.text = controller.latestStatus()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = ConversationToolsSettingsController(this)
        var storedSelection = controller.enabled().toSet()
        val enabled = storedSelection
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@ConversationToolsActivity, 20),
                UiDesign.dp(this@ConversationToolsActivity, 16),
                UiDesign.dp(this@ConversationToolsActivity, 20),
                UiDesign.dp(this@ConversationToolsActivity, 28))
        }
        content.addView(UiDesign.caption(this, getString(R.string.tools_ui_description)),
            UiDesign.spaceAfter(this, 16))
        val choices = ConversationToolId.entries.associateWith { tool ->
            val card = UiDesign.card(this, 12)
            val choice = CheckBox(this).apply {
                text = when (tool) {
                    ConversationToolId.END_CALL -> getString(R.string.tools_ui_end_call_title)
                    ConversationToolId.SAVE_NOTE -> getString(R.string.tools_ui_save_note_title)
                }
                isChecked = tool in enabled
                minimumHeight = UiDesign.dp(this@ConversationToolsActivity, 48)
            }
            card.addView(choice, UiDesign.spaceAfter(this, 4))
            card.addView(UiDesign.caption(this, when (tool) {
                ConversationToolId.END_CALL -> getString(R.string.tools_ui_end_call_detail)
                ConversationToolId.SAVE_NOTE -> getString(R.string.tools_ui_save_note_detail)
            }))
            content.addView(card, UiDesign.spaceAfter(this, 12))
            choice
        }
        val feedback = UiDesign.caption(this, "")
        val save = UiDesign.action(this, getString(R.string.tools_ui_save), UiActionTone.PRIMARY) {
            // Listener below owns the latest selected state.
        }.apply { tag = "tools-save-settings"; isEnabled = false }
        save.setOnClickListener {
            val selected = choices.filterValues { it.isChecked }.keys
            val success = controller.save(selected)
            feedback.text = getString(if (success) R.string.tools_ui_saved else R.string.tools_ui_failed)
            if (success) {
                storedSelection = selected.toSet()
                save.isEnabled = false
            }
        }
        choices.values.forEach { choice ->
            choice.setOnCheckedChangeListener { _, _ ->
                save.isEnabled = choices.filterValues { it.isChecked }.keys != storedSelection
            }
        }
        content.addView(save, UiDesign.spaceAfter(this, 10))
        content.addView(feedback, UiDesign.spaceAfter(this, 16))
        content.addView(UiDesign.heading(this, getString(R.string.tools_ui_latest_status)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 8))
        status = UiDesign.caption(this, "").apply { tag = "tools-latest-status" }
        content.addView(status, UiDesign.spaceAfter(this, 10))
        content.addView(UiDesign.caption(this, getString(R.string.tools_ui_end_hint)),
            UiDesign.spaceAfter(this, 20))
        content.addView(UiDesign.heading(this, getString(R.string.notebook_ui_title)).apply {
            textSize = 19f
        }, UiDesign.spaceAfter(this, 8))
        content.addView(UiDesign.caption(this, getString(R.string.notebook_ui_description)),
            UiDesign.spaceAfter(this, 12))
        content.addView(UiDesign.action(this, getString(R.string.notebook_ui_refresh)) {
            renderNotebooks()
        }.apply { tag = "tools-notebook-refresh" }, UiDesign.spaceAfter(this, 12))
        notebookContent = LinearLayout(this).apply {
            tag = "tools-notebook-list"
            orientation = LinearLayout.VERTICAL
        }
        content.addView(notebookContent)
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onResume() {
        super.onResume()
        renderNotebooks()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    private fun renderNotebooks() {
        if (!::notebookContent.isInitialized) return
        notebookContent.removeAllViews()
        val books = runCatching {
            AndroidConversationPreparationStore(this).notebooks()
        }.getOrElse {
            notebookContent.addView(UiDesign.caption(this,
                getString(R.string.ux07_agent_load_error)))
            return
        }
        if (books.isEmpty()) {
            notebookContent.addView(UiDesign.caption(this,
                getString(R.string.notebook_ui_empty)))
            return
        }
        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        books.forEach { book ->
            val firstTime = book.entries.firstOrNull()?.timestampMs ?: 0L
            val card = UiDesign.card(this).apply {
                tag = "tools-notebook"
                addView(UiDesign.heading(this@ConversationToolsActivity,
                    getString(R.string.notebook_ui_group,
                        getString(if (book.direction == "INCOMING")
                            R.string.prep_ui_incoming else R.string.prep_ui_outgoing),
                        book.entries.size, time.format(Date(firstTime)))).apply {
                    textSize = 16f
                }, UiDesign.spaceAfter(this@ConversationToolsActivity, 8))
                book.entries.forEach { note ->
                    val label = getString(when (note.kind) {
                        ConversationNoteKind.NOTE -> R.string.notebook_ui_note
                        ConversationNoteKind.TASK -> R.string.notebook_ui_task
                        ConversationNoteKind.CALENDAR_PROPOSAL -> R.string.notebook_ui_calendar
                        ConversationNoteKind.MESSAGE_DRAFT -> R.string.notebook_ui_message
                    })
                    addView(UiDesign.caption(this@ConversationToolsActivity,
                        label + " · " + time.format(Date(note.timestampMs))),
                        UiDesign.spaceAfter(this@ConversationToolsActivity, 3))
                    addView(TextView(this@ConversationToolsActivity).apply {
                        text = note.text
                        textSize = 15f
                        setTextColor(UiDesign.ink)
                        setTextIsSelectable(true)
                    }, UiDesign.spaceAfter(this@ConversationToolsActivity, 12))
                }
            }
            notebookContent.addView(card, UiDesign.spaceAfter(this, 12))
        }
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }
}
