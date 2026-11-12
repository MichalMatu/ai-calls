package pl.michalmatu.aicallbridge.conversation

import android.app.AlertDialog
import android.app.ActivityManager
import android.os.Debug
import android.os.SystemClock
import android.text.InputType
import pl.michalmatu.aicallbridge.textagent.NativeModelTiming
import android.content.Intent
import java.util.UUID
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import android.view.View
import android.view.ViewGroup
import android.widget.*
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiSelect

/**
 * Synthetic offline test only. Model tool decisions are observed and scored,
 * never executed; no Telecom calls, audio, permission changes or tool dispatch.
 */
class ConversationSkillLabActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.lab_ui_title)
    private lateinit var runner: SkillLabRunner
    private var lastState = SkillLabUiState()
    private var renderedTurnCount = 0
    private val visiblePending = mutableSetOf<Pair<Int, Int>>()
    private var stopBannerShown = false
    private lateinit var waitClock: Chronometer
    private lateinit var status: TextView
    private lateinit var currentTurn: TextView
    private lateinit var scoreView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var timeline: LinearLayout
    private lateinit var emptyTranscript: TextView
    private lateinit var scroll: ScrollView
    private var userReadingOlderTurns = false
    private var currentTurnTotal = 2
    private lateinit var runButton: Button
    private lateinit var stopButton: Button
    private lateinit var ratingGroup: LinearLayout
    private lateinit var naturalness: Spinner
    private lateinit var manualInput: EditText
    private lateinit var sendButton: Button
    private lateinit var adaptiveButton: Button
    private lateinit var sessionFactory: AndroidSkillLabSessionFactory
    private lateinit var modelLabel: TextView
    private lateinit var contextBudgetInput: EditText
    private lateinit var skillModePicker: Spinner
    private lateinit var skillRoutingView: TextView
    private val turnRoutingRows = mutableListOf<String>()
    private var initialRoutingSummary = ""
    private lateinit var diagnosticsView: TextView
    private val timingMeasurements = mutableListOf<NativeModelTiming>()
    private var diagnosticRunId = 0L
    private var runningContextBudget = 4_096
    private var activeReportId: String? = null
    private var activeCaseId: String? = null
    private var activeCaseVersion: Int? = null
    private var activeCaseReference: LabCaseRevision? = null
    private var activePreset: String = ""
    private var activeModel: String = ""
    private var reportSaved = false
    private lateinit var casePicker: Spinner
    private var caseEntries: List<LabCase> = emptyList()
    private lateinit var setupScreen: ScrollView
    private lateinit var sessionScreen: LinearLayout
    private lateinit var detailContent: LinearLayout
    private lateinit var turnDecisions: LinearLayout
    private lateinit var returnToSetup: Button
    private lateinit var resumeChat: Button
    private var diagnosticsDialog: AlertDialog? = null
    private var conversationVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = UiDesign.content(this)
        content.addView(UiDesign.heading(this, getString(R.string.lab_setup_title)), UiDesign.spaceAfter(this, 8))
        content.addView(UiDesign.caption(this, getString(if (savedInstanceState?.getBoolean("lab_interrupted") == true)
            R.string.lab_chat_interrupted else R.string.lab_setup_intro)), UiDesign.spaceAfter(this))
        val library = try { SkillLabIsolatedTestStore.forContext(this).loadSkillLibrary() }
        catch (error: Exception) {
            setContentView(TextView(this).apply {
                text = getString(R.string.lab_ui_library_error, error.javaClass.simpleName)
            })
            return
        }
        val presets = library.presets
        val picker = Spinner(this).apply {
            tag = "lab-preset-picker"
            UiSelect.setOptions(this, presets.map { it.name })
            setSelection(presets.indexOfFirst { it.id == library.activePresetId }.coerceAtLeast(0))
        }
        content.addView(UiDesign.caption(this, getString(R.string.lab_ui_preset)),
            UiDesign.spaceAfter(this, 6))
        content.addView(picker, UiDesign.spaceAfter(this, 12))
        modelLabel = UiDesign.caption(this, "").apply { tag = "lab-model-selected" }
        content.addView(modelLabel, UiDesign.spaceAfter(this, 6))
        content.addView(UiDesign.action(this, getString(R.string.lab_model_settings)) {
            startActivity(Intent(this, AudioEngineSettingsActivity::class.java))
        }, UiDesign.spaceAfter(this, 10))
        // This is a Lab-only allocation setting. Phone-call budget remains unchanged.
        val storedBudget = getSharedPreferences("lab-diagnostics", MODE_PRIVATE)
            .getInt("context_tokens", 4_096).takeIf { it > 0 } ?: 4_096
        runningContextBudget = storedBudget
        val advanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "lab-advanced-options"
            visibility = View.GONE
        }
        content.addView(UiDesign.action(this, getString(R.string.lab_advanced)) {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }.apply { tag = "lab-advanced-toggle" }, UiDesign.spaceAfter(this, 8))
        advanced.addView(UiDesign.caption(this, getString(R.string.lab_context_label)),
            UiDesign.spaceAfter(this, 4))
        contextBudgetInput = EditText(this).apply {
            tag = "lab-context-tokens"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(storedBudget.toString())
            contentDescription = getString(R.string.lab_context_label)
        }
        advanced.addView(contextBudgetInput, UiDesign.spaceAfter(this, 4))
        advanced.addView(UiDesign.caption(this, getString(R.string.lab_context_hint)),
            UiDesign.spaceAfter(this, 10))
        val savedSkillMode = getSharedPreferences("lab-diagnostics", MODE_PRIVATE)
            .getString("skill_loading_mode", SkillLoadingMode.ALL_ACTIVE.name)
        advanced.addView(UiDesign.caption(this, getString(R.string.lab_skill_mode_label)),
            UiDesign.spaceAfter(this, 4))
        skillModePicker = Spinner(this).apply {
            tag = "lab-skill-mode"
            UiSelect.setOptions(this, listOf(
                getString(R.string.lab_skill_mode_all),
                getString(R.string.lab_skill_mode_contextual)))
            setSelection(if (savedSkillMode == SkillLoadingMode.CONTEXTUAL.name) 1 else 0)
        }
        advanced.addView(skillModePicker, UiDesign.spaceAfter(this, 4))
        skillRoutingView = UiDesign.caption(this, getString(R.string.lab_skill_mode_hint))
            .apply { tag = "lab-skill-routing-diagnostics" }

        content.addView(advanced, UiDesign.spaceAfter(this))
        content.addView(UiDesign.caption(this, getString(R.string.lab_case_select)),
            UiDesign.spaceAfter(this, 6))
        casePicker = Spinner(this).apply { tag = "lab-case-picker" }
        content.addView(casePicker, UiDesign.spaceAfter(this, 8))
        content.addView(UiDesign.action(this, getString(R.string.lab_case_manage)) {
            showCaseList()
        }, UiDesign.spaceAfter(this, 8))
        content.addView(UiDesign.action(this, getString(R.string.lab_reports)) {
            showReports()
        }, UiDesign.spaceAfter(this, 12))
        refreshCases()
        runButton = UiDesign.action(this, getString(R.string.lab_manual_new_session), UiActionTone.PRIMARY) {
            val selected = presets.getOrNull(picker.selectedItemPosition) ?: return@action
            startDialogue(selected.id, adaptive = false)
        }.apply { tag = "lab-start" }
        content.addView(runButton, UiDesign.spaceAfter(this, 8))
        adaptiveButton = UiDesign.action(this, getString(R.string.lab_adaptive_start)) {
            val preset = presets.getOrNull(picker.selectedItemPosition) ?: return@action
            startDialogue(preset.id, adaptive = true)
        }.apply { tag = "lab-adaptive-start" }
        content.addView(adaptiveButton, UiDesign.spaceAfter(this, 8))
        resumeChat = UiDesign.action(this, getString(R.string.lab_resume_chat)) { showConversation() }
            .apply { tag = "lab-resume-chat"; visibility = View.GONE }
        content.addView(resumeChat, UiDesign.spaceAfter(this))
        stopButton = UiDesign.action(this, getString(R.string.lab_ui_stop), UiActionTone.DANGER) {
            stopLab()
        }.apply { visibility = View.GONE; tag = "lab-stop" }
        // STOP remains pinned, even when the options pane scrolls.
        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            tag = "lab-manual-composer"
        }
        manualInput = EditText(this).apply {
            tag = "lab-manual-input"
            hint = getString(R.string.lab_manual_hint)
            setSingleLine(false)
            minLines = 1
            maxLines = 4
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        composer.addView(manualInput, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sendButton = UiDesign.action(this, getString(R.string.lab_manual_send)) {
            if (runner.submitManual(manualInput.text.toString())) manualInput.setText("")
        }.apply { tag = "lab-manual-send" }
        composer.addView(sendButton)
        // Composer is pinned, never hidden by tall options or transcript.

        val transcriptArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@ConversationSkillLabActivity, 20),
                UiDesign.dp(this@ConversationSkillLabActivity, 10),
                UiDesign.dp(this@ConversationSkillLabActivity, 20),
                UiDesign.dp(this@ConversationSkillLabActivity, 28))
        }
        val transcriptCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "lab-conversation-card"
        }
        status = UiDesign.caption(this, "").apply {
            tag = "lab-status"
            textSize = 15f
            setTextColor(UiDesign.ink)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        detailContent = UiDesign.content(this)
        diagnosticsView = UiDesign.caption(this, getString(R.string.lab_diagnostics_waiting)).apply {
            tag = "lab-model-diagnostics"
            setTextIsSelectable(true)
        }
        detailContent.addView(diagnosticsView, UiDesign.spaceAfter(this))
        detailContent.addView(skillRoutingView, UiDesign.spaceAfter(this))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            tag = "lab-live-progress"
        }
        currentTurn = UiDesign.caption(this, "").apply {
            tag = "lab-current-turn"
            setTextIsSelectable(true)
        }
        detailContent.addView(currentTurn, UiDesign.spaceAfter(this))
        turnDecisions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        detailContent.addView(turnDecisions, UiDesign.spaceAfter(this))

        scoreView = UiDesign.caption(this, "").apply {
            tag = "lab-score"
            textSize = 14f
            setTextColor(UiDesign.ink)
            visibility = View.GONE
            setTextIsSelectable(true)
            setPadding(UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 12))
            background = UiDesign.surfaceDrawable(this@ConversationSkillLabActivity)
        }


        ratingGroup = UiDesign.card(this).apply {
            tag = "lab-rating-group"
            visibility = View.GONE
            addView(UiDesign.heading(this@ConversationSkillLabActivity,
                getString(R.string.lab_ui_rating)).apply { textSize = 17f },
                UiDesign.spaceAfter(this@ConversationSkillLabActivity, 6))
            addView(UiDesign.caption(this@ConversationSkillLabActivity,
                getString(R.string.lab_ui_rating_help)),
                UiDesign.spaceAfter(this@ConversationSkillLabActivity, 8))
        }
        naturalness = Spinner(this).apply {
            UiSelect.setOptions(this, listOf(getString(R.string.lab_ui_unrated), "1", "2", "3", "4", "5"))
        }
        ratingGroup.addView(naturalness, UiDesign.spaceAfter(this, 10))
        ratingGroup.addView(UiDesign.action(this, getString(R.string.lab_ui_apply_rating)) {
            lastState.metrics?.let(::showMetrics)
        })
        emptyTranscript = UiDesign.caption(this, getString(R.string.lab_chat_empty)).apply {
            tag = "lab-empty-transcript"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(0, UiDesign.dp(this@ConversationSkillLabActivity, 22),
                0, UiDesign.dp(this@ConversationSkillLabActivity, 22))
        }
        transcriptCard.addView(emptyTranscript)
        timeline = LinearLayout(this).apply {
            tag = "lab-turn-timeline"
            orientation = LinearLayout.VERTICAL
        }
        transcriptCard.addView(timeline)
        transcriptArea.addView(transcriptCard, UiDesign.spaceAfter(this, 12))
        detailContent.addView(scoreView, UiDesign.spaceAfter(this, 12))
        detailContent.addView(ratingGroup, UiDesign.spaceAfter(this, 16))
        scroll = ScrollView(this).apply {
            tag = "lab-transcript-scroll"
            addView(transcriptArea)
            setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    userReadingOlderTurns = true
                } else if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
                    event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) {
                    postDelayed({
                        val body = getChildAt(0)
                        val remaining = ((body?.height ?: 0) - height - scrollY).coerceAtLeast(0)
                        userReadingOlderTurns = remaining > UiDesign.dp(this@ConversationSkillLabActivity, 48)
                    }, 120L)
                }
                false
            }
        }
        setupScreen = ScrollView(this).apply {
            tag = "lab-options-scroll"
            addView(content)
        }
        val composerHolder = LinearLayout(this).apply {
            tag = "lab-composer-holder"
            orientation = LinearLayout.VERTICAL
            val horizontal = UiDesign.dp(this@ConversationSkillLabActivity, 20)
            setPadding(horizontal, 0, horizontal, UiDesign.dp(this@ConversationSkillLabActivity, 12))
            addView(stopButton, UiDesign.spaceAfter(this@ConversationSkillLabActivity, 8))
            addView(composer)
        }
        val sessionHeading = UiDesign.content(this).apply {
            setPadding(UiDesign.dp(this@ConversationSkillLabActivity, 20), 0,
                UiDesign.dp(this@ConversationSkillLabActivity, 20), 0)
            addView(LinearLayout(this@ConversationSkillLabActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(UiDesign.heading(this@ConversationSkillLabActivity, getString(R.string.lab_chat_title)).apply {
                    textSize = 18f
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(UiDesign.iconButton(this@ConversationSkillLabActivity, R.drawable.ic_aicalls_lab,
                    getString(R.string.lab_observe)).apply {
                    tag = "lab-observe"
                    setOnClickListener { showDiagnostics() }
                }, LinearLayout.LayoutParams(UiDesign.dp(this@ConversationSkillLabActivity, 48),
                    UiDesign.dp(this@ConversationSkillLabActivity, 48)))
            })
            waitClock = Chronometer(this@ConversationSkillLabActivity).apply {
                tag = "lab-wait-clock"
                textSize = 13f
                setTextColor(UiDesign.secondary)
                visibility = View.GONE
            }
            addView(LinearLayout(this@ConversationSkillLabActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(waitClock)
            }, UiDesign.spaceAfter(this@ConversationSkillLabActivity, 4))
            addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                UiDesign.dp(this@ConversationSkillLabActivity, 4)))
        }
        returnToSetup = UiDesign.action(this, getString(R.string.lab_setup_return)) { showSetup() }
            .apply { tag = "lab-return-setup" }
        composerHolder.addView(returnToSetup, 0, UiDesign.spaceAfter(this, 8))
        sessionScreen = LinearLayout(this).apply {
            tag = "lab-session-screen"
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(sessionHeading)
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(composerHolder)
        }
        setContentView(FrameLayout(this).apply {
            addView(setupScreen, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(sessionScreen, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })
        sessionFactory = AndroidSkillLabSessionFactory(applicationContext)
        runner = SkillLabRunner(sessionFactory,
            dispatchToUi = { action -> runOnUiThread(action) }, onState = ::renderState)
        renderState(runner.state)
        updateModelDiagnostics()
    }


    private fun showConversation() {
        conversationVisible = true
        setupScreen.visibility = View.GONE
        sessionScreen.visibility = View.VISIBLE
    }

    private fun showSetup() {
        stopLab()
        currentFocus?.clearFocus()
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(manualInput.windowToken, 0)
        conversationVisible = false
        sessionScreen.visibility = View.GONE
        setupScreen.visibility = View.VISIBLE
        resumeChat.visibility = View.VISIBLE
    }

    override fun consumeChromeBack(): Boolean {
        if (!conversationVisible) return false
        showSetup()
        return true
    }

    private fun showDiagnostics() {
        if (diagnosticsDialog?.isShowing == true) return
        updateModelDiagnostics()
        val host = ScrollView(this).apply { addView(detailContent) }
        diagnosticsDialog = AlertDialog.Builder(this).setTitle(R.string.lab_observe)
            .setView(host).setPositiveButton(android.R.string.ok, null).create().apply {
                setOnDismissListener { host.removeView(detailContent); diagnosticsDialog = null }
                show()
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // No messages or prompts in Android saved state; recreation never restarts a test.
        outState.putBoolean("lab_interrupted", conversationVisible)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::casePicker.isInitialized) refreshCases()
        if (::modelLabel.isInitialized) {
            val profile = runCatching { AudioEngineProfileStore(this).load() }.getOrNull()
            val modelId = profile?.dialogue?.name
            val modelName = AudioEngineRegistry.dialogues.firstOrNull { it.id == modelId }?.title
                ?: modelId ?: getString(R.string.lab_model_unavailable)
            modelLabel.text = getString(R.string.lab_model_selected, modelName)
            updateModelDiagnostics()
        }
    }

    private fun caseStore() = SkillLabIsolatedTestStore.forContext(this)

    private fun refreshCases() {
        val selectedId = caseEntries.getOrNull(casePicker.selectedItemPosition - 1)?.id
        caseEntries = runCatching { caseStore().loadLabCases().cases }.getOrElse {
            statusIfReady(it.message.orEmpty())
            emptyList()
        }
        UiSelect.setOptions(casePicker, listOf(getString(R.string.lab_case_none)) +
            caseEntries.map { it.selected().title + " · v" + it.selectedRevision })
        casePicker.setSelection(caseEntries.indexOfFirst { it.id == selectedId }
            .let { if (it < 0) 0 else it + 1 })
    }

    private fun statusIfReady(message: String) {
        if (::status.isInitialized) status.text = message
    }

    private fun showCaseList() {
        val saved = runCatching { caseStore().loadLabCases().cases }.getOrElse {
            showError(it.message.orEmpty()); return
        }
        val choices = listOf(getString(R.string.lab_case_new)) +
            saved.map { it.selected().title + " · v" + it.selectedRevision }
        AlertDialog.Builder(this).setTitle(R.string.lab_case_manage)
            .setItems(choices.toTypedArray()) { _, position ->
                if (position == 0) editCase(null) else showCaseOptions(saved[position - 1])
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showCaseOptions(item: LabCase) {
        val actions = arrayOf(getString(R.string.lab_case_edit),
            getString(R.string.lab_case_duplicate), getString(R.string.lab_case_versions),
            getString(R.string.lab_case_delete))
        AlertDialog.Builder(this).setTitle(item.selected().title)
            .setItems(actions) { _, selected ->
                when (selected) {
                    0 -> editCase(item)
                    1 -> runCaseMutation { it.duplicate(item.id) }
                    2 -> {
                        val choices = item.versions.map { "v" + it.revision +
                            " · " + it.title }.toTypedArray()
                        AlertDialog.Builder(this).setTitle(R.string.lab_case_versions)
                            .setItems(choices) { _, index ->
                                runCaseMutation { it.select(item.id, item.versions[index].revision) }
                            }.show()
                    }
                    3 -> AlertDialog.Builder(this)
                        .setMessage(R.string.lab_case_confirm_delete)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.lab_case_delete) { _, _ ->
                            runCaseMutation { it.delete(item.id) }
                        }.show()
                }
            }.show()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this).setMessage(message)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun runCaseMutation(transform: (LabCaseLibrary) -> LabCaseLibrary) {
        runCatching { caseStore().saveLabCases(transform(caseStore().loadLabCases())) }
            .onSuccess { refreshCases() }
            .onFailure { showError(it.message.orEmpty()) }
    }

    private fun editCase(item: LabCase?) {
        val existing = item?.selected()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@ConversationSkillLabActivity, 20),
                UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 20),
                UiDesign.dp(this@ConversationSkillLabActivity, 12))
        }
        val inputs = linkedMapOf<String, EditText>()
        fun entry(key: String, label: Int, initial: String, single: Boolean = false) {
            body.addView(UiDesign.caption(this, getString(label)))
            val editor = EditText(this).apply {
                tag = "lab-case-field:" + key
                setText(initial)
                setSingleLine(single)
                if (!single) { minLines = 2; maxLines = 5 }
            }
            body.addView(editor, UiDesign.spaceAfter(this, 8))
            inputs[key] = editor
        }
        entry("title", R.string.lab_case_title, existing?.title.orEmpty(), true)
        entry("goal", R.string.lab_case_goal, existing?.goal.orEmpty())
        body.addView(UiDesign.caption(this, getString(R.string.lab_case_role)))
        val role = Spinner(this).apply {
            tag = "lab-case-role"
            UiSelect.setOptions(this, LabRole.entries.map { it.name })
            setSelection(LabRole.entries.indexOf(existing?.role ?: LabRole.GENERAL))
        }
        body.addView(role, UiDesign.spaceAfter(this, 8))
        entry("opening", R.string.lab_case_opening, existing?.opening.orEmpty())
        entry("state", R.string.lab_case_state, existing?.callerState.orEmpty())
        entry("facts", R.string.lab_case_facts, existing?.facts.orEmpty())
        entry("expected", R.string.lab_case_expected, existing?.expectations.orEmpty())
        entry("rules", R.string.lab_case_rules, existing?.rules.orEmpty())
        entry("finish", R.string.lab_case_finish, existing?.completion.orEmpty())
        val scroll = ScrollView(this).apply { addView(body) }
        val dialog = AlertDialog.Builder(this).setTitle(if (item == null)
            R.string.lab_case_new else R.string.lab_case_edit)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.lab_case_save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val texts = inputs.mapValues { it.value.text.toString().trim() }
                val candidate = LabCaseRevision(1, texts.getValue("title"),
                    texts.getValue("goal"), LabRole.entries[role.selectedItemPosition],
                    texts.getValue("opening"), texts.getValue("state"),
                    texts.getValue("facts"), texts.getValue("expected"),
                    texts.getValue("rules"), texts.getValue("finish"))
                runCatching {
                    val current = caseStore().loadLabCases()
                    caseStore().saveLabCases(if (item == null) current.create(candidate)
                        else current.update(item.id, candidate))
                }.onSuccess { dialog.dismiss(); refreshCases() }
                    .onFailure { showError(it.message.orEmpty()) }
            }
        }
        dialog.show()
    }


    private fun startDialogue(presetId: String, adaptive: Boolean) {
        val chosen = caseEntries.getOrNull(casePicker.selectedItemPosition - 1)
        if (adaptive && chosen == null) {
            showError(getString(R.string.lab_adaptive_select_case)); return
        }
        val budget = contextBudgetInput.text.toString().trim().toIntOrNull()
        if (budget == null || budget <= 0) {
            (contextBudgetInput.parent as View).visibility = View.VISIBLE
            contextBudgetInput.error = getString(R.string.lab_context_invalid)
            contextBudgetInput.requestFocus()
            contextBudgetInput.post { contextBudgetInput.requestRectangleOnScreen(
                android.graphics.Rect(0, 0, contextBudgetInput.width, contextBudgetInput.height), true) }
            return
        }
        val test = chosen?.selected()
        sessionFactory.selectedCase = test
        sessionFactory.contextTokenBudget = budget
        val loadingMode = if (skillModePicker.selectedItemPosition == 1)
            SkillLoadingMode.CONTEXTUAL else SkillLoadingMode.ALL_ACTIVE
        sessionFactory.skillLoadingMode = loadingMode
        // Snapshot one observer per session, rejecting late telemetry from a stopped run.
        val thisRun = ++diagnosticRunId
        turnRoutingRows.clear()
        initialRoutingSummary = getString(R.string.lab_skill_mode_hint)
        skillRoutingView.text = initialRoutingSummary
        sessionFactory.onSkillSelection = { decision ->
            runOnUiThread {
                if (!isDestroyed && thisRun == diagnosticRunId) {
                    initialRoutingSummary = getString(R.string.lab_skill_routing_result,
                        decision.selectedIds.size, decision.availableCount,
                        decision.selectorMicros)
                    skillRoutingView.text = (listOf(initialRoutingSummary) + turnRoutingRows)
                        .joinToString("\n")
                }
            }
        }
        sessionFactory.onTurnRouting = { decision ->
            runOnUiThread {
                if (!isDestroyed && thisRun == diagnosticRunId) {
                    val unchanged = getString(R.string.lab_skill_turn_unchanged)
                    turnRoutingRows.add(getString(R.string.lab_skill_turn_route,
                        decision.turn,
                        decision.selectedIds.joinToString(", "),
                        decision.activatedIds.joinToString(", ").ifBlank { unchanged },
                        decision.deactivatedIds.joinToString(", ").ifBlank { unchanged }))
                    skillRoutingView.text = (listOf(initialRoutingSummary) + turnRoutingRows)
                        .joinToString("\n")
                }
            }
        }
        sessionFactory.onTiming = { timing ->
            runOnUiThread {
                if (!isDestroyed && thisRun == diagnosticRunId) {
                    timingMeasurements.add(timing)
                    updateModelDiagnostics()
                }
            }
        }
        runningContextBudget = budget
        timingMeasurements.clear()
        updateModelDiagnostics()
        manualInput.setText(if (adaptive) "" else test?.opening.orEmpty())
        showConversation()
        val started = runCatching {
            if (adaptive) runner.startAdaptive(presetId, requireNotNull(test))
            else runner.startManual(presetId)
        }.getOrElse {
            renderState(SkillLabUiState(phase = SkillLabPhase.FAILED,
                error = it.message ?: it.javaClass.simpleName))
            return
        }
        if (!started) { showSetup(); return }
        getSharedPreferences("lab-diagnostics", MODE_PRIVATE).edit()
            .putInt("context_tokens", budget)
            .putString("skill_loading_mode", loadingMode.name).apply()
        activeReportId = UUID.randomUUID().toString()
        reportSaved = false
        activeCaseId = chosen?.id
        activeCaseVersion = test?.revision
        activeCaseReference = test
        activePreset = presetId
        activeModel = runCatching {
            AudioEngineProfileStore(this).load()?.dialogue?.name
        }.getOrNull().orEmpty()
    }

    private fun saveTerminalReport(state: SkillLabUiState) {
        if (!state.manual || reportSaved || state.completed.isEmpty() ||
            state.phase !in setOf(SkillLabPhase.STOPPED,
                SkillLabPhase.COMPLETED, SkillLabPhase.FAILED)) return
        val id = activeReportId ?: return
        runCatching {
            val report = LabDialogueReport.fromSnapshot(activeCaseId, activeCaseVersion,
                activeModel, activePreset, state, id, activeCaseReference)
            val store = caseStore()
            store.saveLabReports(store.loadLabReports().add(report))
            report
        }.onSuccess { report ->
            reportSaved = true
            scoreView.text = report.evidenceText()
            scoreView.visibility = View.VISIBLE
        }.onFailure { statusIfReady(it.message.orEmpty()) }
    }

    private fun showReports() {
        val saved = runCatching { caseStore().loadLabReports().reports }.getOrElse {
            showError(it.message.orEmpty()); return
        }
        if (saved.isEmpty()) { showError(getString(R.string.lab_reports_empty)); return }
        val items = saved.reversed()
        AlertDialog.Builder(this).setTitle(R.string.lab_reports)
            .setItems(items.map { it.modelId + " · " + it.turns.size +
                " " + getString(R.string.lab_reports_turns) }.toTypedArray()) { _, index ->
                showReport(items[index])
            }.show()
    }

    private fun showReport(report: LabDialogueReport) {
        val details = report.evidenceText() + "\n\n" + report.turns.joinToString("\n\n") {
            "#" + it.turn + "\n" + getString(R.string.lab_ui_input) + ": " + it.caller +
                "\n" + getString(R.string.lab_ui_output) + ": " + it.assistant
        }
        val text = TextView(this).apply {
            setPadding(UiDesign.dp(this@ConversationSkillLabActivity, 24),
                UiDesign.dp(this@ConversationSkillLabActivity, 24),
                UiDesign.dp(this@ConversationSkillLabActivity, 24),
                UiDesign.dp(this@ConversationSkillLabActivity, 24))
            setTextIsSelectable(true)
            this.text = details
        }
        AlertDialog.Builder(this).setTitle(R.string.lab_report_details)
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton(R.string.lab_report_review) { _, _ -> chooseDimension(report) }
            .setNeutralButton(R.string.lab_report_compare) { _, _ -> chooseComparison(report) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun chooseDimension(report: LabDialogueReport) {
        val dimensions = LabReviewDimension.entries
        AlertDialog.Builder(this).setTitle(R.string.lab_report_review)
            .setItems(dimensions.map { it.name }.toTypedArray()) { _, idx ->
                val dimension = dimensions[idx]
                val choices = LabReviewVerdict.entries
                AlertDialog.Builder(this).setTitle(dimension.name)
                    .setItems(choices.map { it.name }.toTypedArray()) { _, verdictIndex ->
                        val verdict = choices[verdictIndex]
                        if (verdict == LabReviewVerdict.UNASSESSED) {
                            saveReview(report, dimension, verdict, null, "")
                        } else {
                            AlertDialog.Builder(this).setTitle(R.string.lab_report_select_turn)
                                .setItems(report.turns.map { "#" + it.turn }.toTypedArray()) { _, i ->
                                    val note = EditText(this).apply {
                                        hint = getString(R.string.lab_report_evidence_hint)
                                    }
                                    AlertDialog.Builder(this)
                                        .setTitle(R.string.lab_report_evidence_hint)
                                        .setView(note)
                                        .setPositiveButton(R.string.lab_case_save) { _, _ ->
                                            saveReview(report, dimension, verdict,
                                                report.turns[i].turn, note.text.toString())
                                        }
                                        .setNegativeButton(android.R.string.cancel, null).show()
                                }.show()
                        }
                    }.show()
            }.show()
    }

    private fun saveReview(report: LabDialogueReport, dimension: LabReviewDimension,
                           verdict: LabReviewVerdict, turn: Int?, note: String) {
        runCatching {
            val store = caseStore()
            store.saveLabReports(store.loadLabReports().review(
                report.id, dimension, verdict, turn, note))
        }.onSuccess {
            val saved = caseStore().loadLabReports().reports.first { it.id == report.id }
            showReport(saved)
        }.onFailure { showError(it.message.orEmpty()) }
    }

    private fun chooseComparison(report: LabDialogueReport) {
        val others = runCatching { caseStore().loadLabReports().reports.filter {
            it.id != report.id
        } }.getOrElse { showError(it.message.orEmpty()); return }
        if (others.isEmpty()) { showError(getString(R.string.lab_reports_need_two)); return }
        AlertDialog.Builder(this).setTitle(R.string.lab_report_compare)
            .setItems(others.map { it.modelId + " · " + it.turns.size }.toTypedArray()) { _, idx ->
                val b = others[idx]
                val comparison = LabABComparison.between(report, b)
                val summary = getString(if (comparison.comparable)
                    R.string.lab_report_comparable else R.string.lab_report_not_comparable) +
                    "\nA: " + comparison.aTurns + " turns, " + comparison.aLatencyMs + " ms, " +
                    comparison.aEndProposals + " END_CALL\nB: " + comparison.bTurns + " turns, " +
                    comparison.bLatencyMs + " ms, " + comparison.bEndProposals + " END_CALL\n" +
                    comparison.assessments.entries.joinToString("\n") {
                        it.key.name + ": " + it.value.first + " / " + it.value.second
                    }
                AlertDialog.Builder(this).setTitle(R.string.lab_report_compare)
                    .setMessage(summary).setPositiveButton(android.R.string.ok, null).show()
            }.show()
    }

    private fun runLab(presetId: String, quick: Boolean) {
        if (::runner.isInitialized) runner.startManual(presetId)
    }

    /** Live metrics are app-owned and contain no prompts, replies or user identity. */
    private fun updateModelDiagnostics() {
        if (!::diagnosticsView.isInitialized) return
        val modelId = runCatching { AudioEngineProfileStore(this).load()?.dialogue?.name }
            .getOrNull()
        val model = AudioEngineRegistry.dialogues.firstOrNull { it.id == modelId }?.title
            ?: modelId ?: getString(R.string.lab_model_unavailable)
        val last = timingMeasurements.lastOrNull()
        val memory = Debug.MemoryInfo()
        Debug.getMemoryInfo(memory)
        val system = ActivityManager.MemoryInfo()
        getSystemService(ActivityManager::class.java)?.getMemoryInfo(system)
        val processPssMib = memory.totalPss / 1024L
        val systemFreeMib = system.availMem / (1024L * 1024L)
        diagnosticsView.text = if (last == null) {
            getString(R.string.lab_diagnostics_header, model, runningContextBudget) + "\n" +
                getString(R.string.lab_diagnostics_waiting) + "\n" +
                getString(R.string.lab_diagnostics_memory, processPssMib, systemFreeMib)
        } else {
            val complete = timingMeasurements.filter { it.status == "complete" }
            val meanGenerateMs = if (complete.isEmpty()) 0L
                else complete.sumOf { it.generateMs } / complete.size
            getString(R.string.lab_diagnostics_header, model, last.contextTokens) + "\n" +
                getString(R.string.lab_diagnostics_last, last.backend, last.turn,
                    last.engineReadyMs, last.conversationCreateMs, last.generateMs,
                    last.totalMs) + "\n" +
                getString(R.string.lab_diagnostics_summary, complete.size,
                    meanGenerateMs, last.outputChars, last.status,
                    if (last.conversationReused) getString(R.string.lab_diagnostics_warm)
                    else getString(R.string.lab_diagnostics_cold)) + "\n" +
                getString(R.string.lab_diagnostics_memory, processPssMib, systemFreeMib)
        }
    }

    private fun renderState(next: SkillLabUiState) {
        if (isDestroyed) return
        val previous = lastState
        lastState = next
        val busy = next.phase in setOf(SkillLabPhase.STARTING, SkillLabPhase.WAITING)
        if (busy) {
            if (previous.phase != next.phase || previous.pending != next.pending) waitClock.base = SystemClock.elapsedRealtime()
            waitClock.visibility = View.VISIBLE
            waitClock.start()
        } else {
            waitClock.stop()
            waitClock.visibility = View.GONE
        }
        currentTurnTotal = next.totalTurns
        saveTerminalReport(next)
        if (next.phase == SkillLabPhase.STARTING) {
            renderedTurnCount = 0
            userReadingOlderTurns = false
            visiblePending.clear()
            stopBannerShown = false
            timeline.removeAllViews()
            turnDecisions.removeAllViews()
            naturalness.setSelection(0)
            scoreView.visibility = View.GONE
            ratingGroup.visibility = View.GONE
        }

        // Snapshots contain both the input and output: skipped UI posts can be
        // reconstructed without losing the last turn or showing a stale response.
        next.completed.drop(renderedTurnCount).forEach { item ->
            val pending = item.pending
            val key = pending.storyIndex to pending.turnIndex
            if (visiblePending.add(key)) showTurnPending(
                pending.storyIndex, pending.storyTitle, pending.turnIndex,
                pending.utterance, renderedTurnCount)
            showTurnCompleted(pending.storyIndex, item.observation, renderedTurnCount + 1)
            renderedTurnCount++
        }
        next.pending?.let { pending ->
            val key = pending.storyIndex to pending.turnIndex
            if (visiblePending.add(key)) showTurnPending(
                pending.storyIndex, pending.storyTitle, pending.turnIndex,
                pending.utterance, next.completed.size)
        }
        if (next.phase in setOf(SkillLabPhase.STOPPED, SkillLabPhase.FAILED)) {
            visiblePending.forEach { (story, turn) ->
                timeline.findViewWithTag<TextView>("lab-pending:$story:$turn")?.setText(R.string.lab_reply_interrupted)
            }
        }
        emptyTranscript.visibility = if (next.phase == SkillLabPhase.STARTING && timeline.childCount == 0)
            View.VISIBLE else View.GONE
        if (next.phase == SkillLabPhase.STARTING) showConversation()
        status.visibility = View.VISIBLE
        progress.visibility = if (next.phase in setOf(SkillLabPhase.STARTING, SkillLabPhase.WAITING))
            View.VISIBLE else View.GONE
        progress.isIndeterminate = progress.visibility == View.VISIBLE
        progress.max = next.totalTurns
        progress.progress = next.completed.size
        runButton.isEnabled = next.manualStartAllowed
        adaptiveButton.isEnabled = next.manualStartAllowed
        contextBudgetInput.isEnabled = next.manualStartAllowed
        sendButton.isEnabled = next.phase == SkillLabPhase.INPUT_READY && !next.adaptive
        manualInput.isEnabled = sendButton.isEnabled
        manualInput.visibility = if (next.adaptive) View.GONE else View.VISIBLE
        sendButton.visibility = manualInput.visibility
        stopButton.visibility = if (!next.manualStartAllowed && next.phase in
            setOf(SkillLabPhase.STARTING, SkillLabPhase.INPUT_READY,
                SkillLabPhase.WAITING, SkillLabPhase.TURN_COMPLETED)) View.VISIBLE else View.GONE
        returnToSetup.visibility = if (next.manualStartAllowed) View.VISIBLE else View.GONE
        status.setTextColor(if (next.phase == SkillLabPhase.FAILED) UiDesign.danger else UiDesign.ink)
        currentTurn.visibility = View.VISIBLE
        when (next.phase) {
            SkillLabPhase.READY -> status.setText(R.string.lab_manual_empty)
            SkillLabPhase.STARTING -> status.setText(R.string.lab_chat_starting)
            SkillLabPhase.INPUT_READY -> status.setText(if (next.adaptive)
                R.string.lab_chat_adaptive_ready else R.string.lab_chat_input_ready)
            SkillLabPhase.WAITING -> status.text = getString(R.string.lab_chat_waiting, next.completed.size + 1)
            SkillLabPhase.TURN_COMPLETED -> status.text = getString(R.string.lab_chat_turn_done, next.completed.size)
            SkillLabPhase.COMPLETED -> {
                next.metrics?.let(::showMetrics)
                ratingGroup.visibility = View.VISIBLE
                status.text = getString(R.string.lab_chat_complete, next.completed.size)
                followLatestCardIfEnabled()
            }
            SkillLabPhase.STOPPED -> {
                status.text = getString(R.string.lab_chat_stopped, next.completed.size)
                if (!stopBannerShown) { showStopNotice(); stopBannerShown = true }
            }
            SkillLabPhase.FAILED -> renderFailure(next.error ?: "unknown_failure")
        }
        if (next.phase != SkillLabPhase.FAILED) currentTurn.text = status.text
    }

    /** Explicitly distinct error state, shared by model/timeout failures and UI tests. */
    private fun renderFailure(reason: String) {
        showConversation()
        status.text = getString(if (reason.contains("timeout", ignoreCase = true))
            R.string.lab_chat_timeout else R.string.lab_chat_error)
        status.setTextColor(UiDesign.danger)
        currentTurn.text = reason
        followLatestCardIfEnabled()
    }

    private fun showStopNotice() {
        val note = UiDesign.caption(this, getString(R.string.lab_chat_stopped, progress.progress)).apply {
            tag = "lab-stopped-banner"
            textSize = 15f
            setTextColor(UiDesign.ink)
            background = UiDesign.surfaceDrawable(this@ConversationSkillLabActivity)
            setPadding(UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 12))
        }
        timeline.addView(note, UiDesign.spaceAfter(this, 10))
        followCardIfEnabled(note)
    }

    private fun followLatestCardIfEnabled() {
        val last = timeline.getChildAt(timeline.childCount - 1) ?: return
        followCardIfEnabled(last)
    }

    private fun showTurnPending(storyIndex: Int, story: String, index: Int,
                                utterance: String, completedBefore: Int) {
        showConversation()
        emptyTranscript.visibility = View.GONE
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "lab-turn:" + storyIndex + ":" + index
        }
        card.addView(conversationMessageView(this, ConversationChatFeed.Line(index,
            ConversationChatFeed.Speaker.COUNTERPART, utterance),
            headerOverride = getString(R.string.lab_chat_caller) + " · " + getString(R.string.lab_ui_turn_short, index)))
        card.addView(UiDesign.caption(this, getString(R.string.lab_ui_waiting_response)).apply {
            tag = "lab-pending:" + storyIndex + ":" + index
            setPadding(0, UiDesign.dp(this@ConversationSkillLabActivity, 8), 0, 0)
        })
        timeline.addView(card, UiDesign.spaceAfter(this, 12))
        followCardIfEnabled(card)
    }

    private fun showTurnCompleted(storyIndex: Int, record: SkillLabObservation, completed: Int) {
        val card = timeline.findViewWithTag<LinearLayout>(
            "lab-turn:" + storyIndex + ":" + record.index) ?: return
        card.findViewWithTag<View>("lab-pending:" + storyIndex + ":" + record.index)?.let(card::removeView)
        val footer = getString(R.string.lab_chat_latency, record.elapsedMs) +
            if (record.actualEnd) " · " + getString(R.string.lab_chat_end_proposed) else ""
        card.addView(conversationMessageView(this, ConversationChatFeed.Line(record.index,
            ConversationChatFeed.Speaker.ASSISTANT,
            record.reply.trim().ifBlank { getString(R.string.lab_ui_empty_reply) },
            txStatus = footer, responseKind = ConversationChatFeed.ResponseKind.MODEL_DIRECT),
            bodyTag = "lab-reply:" + storyIndex + ":" + record.index,
            headerOverride = getString(R.string.lab_chat_assistant) + " · " + getString(R.string.lab_ui_turn_short, record.index)))
        turnDecisions.addView(UiDesign.caption(this, getString(R.string.lab_ui_turn_short, record.index) +
            " · " + getString(R.string.lab_manual_end_observed,
                getString(if (record.actualEnd) R.string.lab_ui_end_yes else R.string.lab_ui_end_no), record.elapsedMs)).apply {
            tag = "lab-decision:" + storyIndex + ":" + record.index
        }, UiDesign.spaceAfter(this, 8))
        progress.progress = completed
        currentTurn.text = getString(R.string.lab_ui_last_result, record.story, record.index, record.elapsedMs)
        followCardIfEnabled(card)
    }

    private fun followCardIfEnabled(card: View) {
        if (userReadingOlderTurns) return
        // Follow latest only while the owner has not scrolled up to read older turns.
        scroll.post {
            if (isDestroyed || userReadingOlderTurns || !card.isAttachedToWindow) return@post
            scroll.post {
                if (isDestroyed || userReadingOlderTurns || !card.isAttachedToWindow) return@post
                val body = scroll.getChildAt(0) ?: return@post
                val top = card.top + timeline.top + (timeline.parent as View).top
                val target = (top + card.height - scroll.height + UiDesign.dp(this, 30))
                    .coerceAtLeast(0)
                val bottom = (body.height - scroll.height).coerceAtLeast(0)
                scroll.smoothScrollTo(0, target.coerceAtMost(bottom))
            }
        }
    }

    private fun showMetrics(metrics: SkillLabMetrics) {
        val naturalnessScore = naturalness.selectedItemPosition.takeIf { it > 0 }
            ?.let { getString(R.string.lab_ui_naturalness_score, it) }
            ?: getString(R.string.lab_ui_naturalness_unrated)
        scoreView.text = getString(R.string.lab_ui_score_title) + "\n" +
            getString(R.string.lab_ui_score_decisions,
                metrics.accurateEndDecisions, metrics.turns) + "\n" +
            getString(R.string.lab_ui_score_errors,
                metrics.prematureEnds, metrics.missingEnds, metrics.unexpectedQuestions) + "\n" +
            getString(R.string.lab_ui_score_latency, metrics.meanLatencyMs) + "\n" +
            naturalnessScore + "\n\n" + getString(R.string.lab_ui_quality_not_scored)
        scoreView.visibility = View.VISIBLE
    }

    private fun stopLab() {
        if (::runner.isInitialized) {
            runner.stop()
            saveTerminalReport(runner.state)
        }
    }

    override fun onStop() {
        stopLab()
        super.onStop()
    }

    override fun onDestroy() {
        diagnosticsDialog?.dismiss()
        if (::waitClock.isInitialized) waitClock.stop()
        if (::runner.isInitialized) runner.close()
        super.onDestroy()
    }
}
