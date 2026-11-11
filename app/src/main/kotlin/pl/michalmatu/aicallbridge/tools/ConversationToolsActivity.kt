package pl.michalmatu.aicallbridge.tools

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore
import pl.michalmatu.aicallbridge.conversation.ConversationHistoryActivity
import pl.michalmatu.aicallbridge.conversation.ConversationNotebook
import pl.michalmatu.aicallbridge.conversation.ConversationChatFeed
import pl.michalmatu.aicallbridge.conversation.ConversationNotebookListPolicy
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteKind
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiSelect

/** Notebook-first UI only; call tools and their authorization remain independent. */
class ConversationToolsActivity : UiChromeActivity(AppTab.AGENT) {
    override fun toolbarTitle(): String = getString(R.string.tools_ui_title)

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    // Keystore construction and legacy migration must not happen on the UI thread.
    private val repository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidConversationPreparationStore(applicationContext)
    }
    private lateinit var notebookList: LinearLayout
    private lateinit var notebookCounter: TextView
    private lateinit var controller: ConversationToolsSettingsController
    private var books: List<ConversationNotebook> = emptyList()
    private var loadGeneration = 0
    private var loading = true
    private var loadFailed = false
    private var search = ""
    private var direction: String? = null
    private var pageCount = 20
    private val expanded = mutableSetOf<String>()
    private val shownEntries = mutableMapOf<String, Int>()
    private var deletionInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        controller = ConversationToolsSettingsController(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@ConversationToolsActivity, 18),
                UiDesign.dp(this@ConversationToolsActivity, 12),
                UiDesign.dp(this@ConversationToolsActivity, 18),
                UiDesign.dp(this@ConversationToolsActivity, 28))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(UiDesign.heading(this, getString(R.string.notebook_ui_title)).apply {
            textSize = 19f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        notebookCounter = UiDesign.caption(this, "").apply {
            tag = "tools-notebook-count"
        }
        header.addView(notebookCounter)
        content.addView(header, UiDesign.spaceAfter(this, 12))

        val input = EditText(this).apply {
            tag = "tools-notebook-search"
            hint = getString(R.string.notebook_ui_search)
            setSingleLine(true)
            textSize = 15f
            minimumHeight = UiDesign.dp(this@ConversationToolsActivity, 48)
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    search = s?.toString().orEmpty()
                    pageCount = 20
                    renderNotebooks()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        content.addView(input, UiDesign.spaceAfter(this, 8))
        val filter = Spinner(this).apply {
            tag = "tools-notebook-filter"
            UiSelect.setOptions(this, listOf(
                getString(R.string.notebook_ui_filter_all),
                getString(R.string.notebook_ui_filter_incoming),
                getString(R.string.notebook_ui_filter_outgoing),
            ))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?,
                    view: View?, position: Int, id: Long) {
                    direction = when (position) {
                        1 -> "INCOMING"
                        2 -> "OUTGOING"
                        else -> null
                    }
                    pageCount = 20
                    renderNotebooks()
                }
            }
        }
        content.addView(filter, UiDesign.spaceAfter(this, 12))
        notebookList = LinearLayout(this).apply {
            tag = "tools-notebook-list"
            orientation = LinearLayout.VERTICAL
        }
        content.addView(notebookList, UiDesign.spaceAfter(this, 18))

        content.addView(UiDesign.heading(this, getString(R.string.notebook_ui_tools_heading)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 9))
        var storedSelection = controller.enabled().toSet()
        val options = ConversationToolId.entries.associateWith { tool ->
            CheckBox(this).apply {
                tag = "tools-setting:" + tool.wireName
                text = getString(when (tool) {
                    ConversationToolId.END_CALL -> R.string.tools_ui_end_call_title
                    ConversationToolId.SAVE_NOTE -> R.string.tools_ui_save_note_title
                })
                textSize = 15f
                isChecked = tool in storedSelection
                minimumHeight = UiDesign.dp(this@ConversationToolsActivity, 48)
            }.also { content.addView(it, UiDesign.spaceAfter(this, 4)) }
        }
        val feedback = UiDesign.caption(this, "").apply {
            visibility = View.GONE
            tag = "tools-settings-feedback"
        }
        val save = UiDesign.action(this, getString(R.string.tools_ui_save), UiActionTone.PRIMARY) {
            // Actual guarded action is attached below.
        }.apply {
            tag = "tools-save-settings"
            visibility = View.GONE
        }
        fun selected() = options.filterValues { it.isChecked }.keys.toSet()
        save.setOnClickListener {
            if (controller.save(selected())) {
                storedSelection = selected()
                save.visibility = View.GONE
                feedback.text = getString(R.string.tools_ui_saved)
            } else {
                feedback.text = getString(R.string.tools_ui_failed)
            }
            feedback.visibility = View.VISIBLE
        }
        options.values.forEach { option ->
            option.setOnCheckedChangeListener { _, _ ->
                save.visibility = if (selected() != storedSelection) View.VISIBLE else View.GONE
                feedback.visibility = View.GONE
            }
        }
        content.addView(save, UiDesign.spaceAfter(this, 6))
        content.addView(feedback)
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
    }

    override fun onResume() {
        super.onResume()
        loadNotebooks()
    }

    private fun loadNotebooks() {
        val token = ++loadGeneration
        loading = true
        loadFailed = false
        renderNotebooks()
        worker.execute {
            val outcome = runCatching { repository.notebooks() }
            main.post {
                if (token != loadGeneration || isFinishing || isDestroyed) return@post
                loading = false
                loadFailed = outcome.isFailure
                if (outcome.isSuccess) books = outcome.getOrThrow()
                renderNotebooks()
            }
        }
    }

    private fun renderNotebooks() {
        if (!::notebookList.isInitialized || !::notebookCounter.isInitialized) return
        notebookList.removeAllViews()
        notebookCounter.text = getString(R.string.notebook_ui_count, books.size)
        if (loading || loadFailed || books.isEmpty()) {
            notebookList.addView(UiDesign.caption(this, getString(when {
                loading -> R.string.notebook_ui_loading
                loadFailed -> R.string.ux07_agent_load_error
                else -> R.string.notebook_ui_empty
            })))
            return
        }
        val visible = ConversationNotebookListPolicy.filter(books, search, direction)
        if (visible.isEmpty()) {
            notebookList.addView(UiDesign.caption(this,
                getString(R.string.notebook_ui_no_matches)))
            return
        }
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        visible.take(pageCount).forEach { book ->
            val last = book.entries.lastOrNull()
            val card = UiDesign.card(this, 12).apply {
                tag = "tools-notebook"
            }
            val expandedNow = book.sessionId in expanded
            val dateText = last?.let { date.format(Date(it.timestampMs)) }.orEmpty()
            val title = getString(R.string.notebook_ui_group,
                getString(if (book.direction == "INCOMING") R.string.notebook_ui_filter_incoming
                    else R.string.notebook_ui_filter_outgoing), book.entries.size, dateText)
            val toggle = LinearLayout(this).apply {
                tag = "tools-notebook-toggle:" + book.sessionId
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                minimumHeight = UiDesign.dp(this@ConversationToolsActivity, 58)
                background = UiDesign.touchSurface(this@ConversationToolsActivity)
                contentDescription = title + " · " + getString(if (expandedNow)
                    R.string.notebook_ui_collapse else R.string.notebook_ui_expand)
                setPadding(UiDesign.dp(this@ConversationToolsActivity, 8),
                    UiDesign.dp(this@ConversationToolsActivity, 6),
                    UiDesign.dp(this@ConversationToolsActivity, 8),
                    UiDesign.dp(this@ConversationToolsActivity, 6))
                setOnClickListener {
                    if (!expanded.add(book.sessionId)) expanded.remove(book.sessionId)
                    renderNotebooks()
                }
            }
            toggle.addView(TextView(this).apply {
                text = title + if (expandedNow) "  ⌃" else "  ›"
                textSize = 15f
                setTextColor(UiDesign.ink)
                maxLines = 2
            })
            if (!expandedNow) toggle.addView(UiDesign.caption(this,
                last?.text?.replace('\n', ' ')?.take(105).orEmpty()).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            card.addView(toggle)
            if (expandedNow) {
                val limit = shownEntries[book.sessionId] ?: 12
                book.entries.asReversed().take(limit).forEach { note ->
                    val kind = getString(when (note.kind) {
                        ConversationNoteKind.NOTE -> R.string.notebook_ui_note
                        ConversationNoteKind.TASK -> R.string.notebook_ui_task
                        ConversationNoteKind.CALENDAR_PROPOSAL -> R.string.notebook_ui_calendar
                        ConversationNoteKind.MESSAGE_DRAFT -> R.string.notebook_ui_message
                    })
                    card.addView(UiDesign.caption(this,
                        kind + " · " + date.format(Date(note.timestampMs))),
                        UiDesign.spaceAfter(this, 3))
                    card.addView(TextView(this).apply {
                        tag = "tools-notebook-entry"
                        text = note.text
                        textSize = 15f
                        setTextColor(UiDesign.ink)
                        setTextIsSelectable(true)
                    }, UiDesign.spaceAfter(this, 11))
                }
                if (book.entries.size > limit) card.addView(UiDesign.action(this,
                    getString(R.string.notebook_ui_older, book.entries.size - limit)) {
                    shownEntries[book.sessionId] = limit + 12
                    renderNotebooks()
                }.apply { tag = "tools-notebook-older:" + book.sessionId },
                    UiDesign.spaceAfter(this, 10))
                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                actions.addView(UiDesign.action(this,
                    getString(R.string.notebook_ui_history), UiActionTone.SECONDARY) {
                    startActivity(Intent(this, ConversationHistoryActivity::class.java))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                actions.addView(UiDesign.action(this,
                    getString(R.string.notebook_ui_delete), UiActionTone.REVOCATION) {
                    confirmDelete(book)
                }.apply {
                    tag = "tools-notebook-delete:" + book.sessionId
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = UiDesign.dp(this@ConversationToolsActivity, 8)
                })
                card.addView(actions, UiDesign.spaceAfter(this, 2))
            }
            notebookList.addView(card, UiDesign.spaceAfter(this, 8))
        }
        if (visible.size > pageCount) notebookList.addView(UiDesign.action(this,
            getString(R.string.notebook_ui_more, visible.size - pageCount)) {
            pageCount += 20
            renderNotebooks()
        }.apply { tag = "tools-notebook-more" })
    }

    private fun confirmDelete(book: ConversationNotebook) {
        if (deletionInProgress) return
        if (ConversationChatFeed.snapshot().active) {
            AlertDialog.Builder(this)
                .setMessage(R.string.notebook_ui_delete_busy)
                .setPositiveButton(android.R.string.ok, null).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.notebook_ui_delete_title)
            .setMessage(R.string.notebook_ui_delete_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.notebook_ui_delete) { _, _ ->
                if (deletionInProgress) return@setPositiveButton
                deletionInProgress = true
                val token = ++loadGeneration
                worker.execute {
                    val result = runCatching {
                        if (ConversationChatFeed.snapshot().active) false
                        else repository.deleteNotebook(book.sessionId)
                    }
                    main.post {
                        if (token != loadGeneration || isDestroyed) return@post
                        deletionInProgress = false
                        if (result.getOrDefault(false)) {
                            expanded.remove(book.sessionId)
                            shownEntries.remove(book.sessionId)
                            loadNotebooks()
                        } else {
                            AlertDialog.Builder(this)
                                .setMessage(R.string.notebook_ui_delete_failed)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                            loadNotebooks()
                        }
                    }
                }
            }
            .show()
    }

    override fun onDestroy() {
        loadGeneration++
        worker.shutdown()
        super.onDestroy()
    }
}
