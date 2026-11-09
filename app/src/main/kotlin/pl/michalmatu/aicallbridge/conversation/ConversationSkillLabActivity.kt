package pl.michalmatu.aicallbridge.conversation

import android.app.AlertDialog
import android.app.ActivityManager
import android.os.Debug
import android.text.InputType
import pl.michalmatu.aicallbridge.textagent.NativeModelTiming
import android.content.Intent
import java.util.UUID
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import android.os.Bundle
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
class ConversationSkillLabActivity : UiChromeActivity(AppTab.LAB) {
    private lateinit var runner: SkillLabRunner
    private var lastState = SkillLabUiState()
    private var renderedTurnCount = 0
    private val visiblePending = mutableSetOf<Pair<Int, Int>>()
    private var stopBannerShown = false
    private lateinit var status: TextView
    private lateinit var currentTurn: TextView
    private lateinit var scoreView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var timeline: LinearLayout
    private lateinit var emptyTranscript: TextView
    private lateinit var scroll: ScrollView
    private var userReadingOlderTurns = false
    private lateinit var modePicker: Spinner
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep START/STOP visible while the transcript scrolls independently below.
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@ConversationSkillLabActivity, 20),
                UiDesign.dp(this@ConversationSkillLabActivity, 12),
                UiDesign.dp(this@ConversationSkillLabActivity, 20),
                UiDesign.dp(this@ConversationSkillLabActivity, 4))
        }
        content.addView(UiDesign.heading(this, getString(R.string.lab_ui_title)).apply {
            textSize = 23f
        }, UiDesign.spaceAfter(this, 8))

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
        content.addView(UiDesign.caption(this, getString(R.string.lab_context_label)),
            UiDesign.spaceAfter(this, 4))
        contextBudgetInput = EditText(this).apply {
            tag = "lab-context-tokens"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(storedBudget.toString())
            contentDescription = getString(R.string.lab_context_label)
        }
        content.addView(contextBudgetInput, UiDesign.spaceAfter(this, 4))
        content.addView(UiDesign.caption(this, getString(R.string.lab_context_hint)),
            UiDesign.spaceAfter(this, 10))
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
        // Scripted quick/full modes are retired; all dialogue comes from a person or a saved case.
        modePicker = Spinner(this).apply {
            tag = "lab-scope-picker"
            UiSelect.setOptions(this, listOf(
                getString(R.string.lab_ui_mode_quick),
                getString(R.string.lab_ui_mode_full)))
            setSelection(0)
        }
        modePicker.visibility = View.GONE

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
        val transcriptCard = UiDesign.card(this).apply {
            tag = "lab-conversation-card"
        }
        transcriptCard.addView(UiDesign.heading(this, getString(R.string.ux11_lab_timeline_title)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 8))
        status = UiDesign.caption(this, getString(R.string.lab_manual_empty)).apply {
            tag = "lab-status"
            textSize = 14f
            setTextColor(UiDesign.ink)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        transcriptCard.addView(status, UiDesign.spaceAfter(this, 8))
        diagnosticsView = UiDesign.caption(this, getString(R.string.lab_diagnostics_waiting)).apply {
            tag = "lab-model-diagnostics"
            setTextIsSelectable(true)
        }
        transcriptCard.addView(diagnosticsView, UiDesign.spaceAfter(this, 8))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = currentTurnTotal
            progress = 0
            tag = "lab-live-progress"
            contentDescription = getString(R.string.lab_ui_progress, 0, currentTurnTotal)
        }
        transcriptCard.addView(progress, UiDesign.spaceAfter(this, 10))
        currentTurn = UiDesign.caption(this, getString(R.string.lab_ui_empty)).apply {
            tag = "lab-current-turn"
            textSize = 14f
            setTextColor(UiDesign.ink)
            setTextIsSelectable(true)
        }
        transcriptCard.addView(currentTurn, UiDesign.spaceAfter(this, 8))

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
        emptyTranscript = UiDesign.caption(this, getString(R.string.lab_manual_empty)).apply {
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
        transcriptArea.addView(scoreView, UiDesign.spaceAfter(this, 12))
        transcriptArea.addView(ratingGroup, UiDesign.spaceAfter(this, 16))
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
        val optionScroll = ScrollView(this).apply {
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
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Independent scroll regions preserve visible chat and a fixed composer.
            addView(optionScroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.36f))
            addView(scroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.64f))
            addView(composerHolder, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        sessionFactory = AndroidSkillLabSessionFactory(applicationContext)
        runner = SkillLabRunner(sessionFactory,
            dispatchToUi = { action -> runOnUiThread(action) }, onState = ::renderState)
        renderState(runner.state)
        updateModelDiagnostics()
        modePicker.isEnabled = false
    }


    override fun onResume() {
        super.onResume()
        if (::casePicker.isInitialized) refreshCases()
        if (::modelLabel.isInitialized) {
            val profile = runCatching { AudioEngineProfileStore(this).load() }.getOrNull()
            modelLabel.text = getString(R.string.lab_model_selected,
                profile?.dialogue?.name ?: getString(R.string.lab_model_unavailable))
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
            contextBudgetInput.error = getString(R.string.lab_context_invalid)
            return
        }
        val test = chosen?.selected()
        sessionFactory.selectedCase = test
        sessionFactory.contextTokenBudget = budget
        // Snapshot one observer per session, rejecting late telemetry from a stopped run.
        val thisRun = ++diagnosticRunId
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
        val started = runCatching {
            if (adaptive) runner.startAdaptive(presetId, requireNotNull(test))
            else runner.startManual(presetId)
        }.getOrElse { showError(it.message.orEmpty()); return }
        if (!started) return
        getSharedPreferences("lab-diagnostics", MODE_PRIVATE).edit()
            .putInt("context_tokens", budget).apply()
        activeReportId = UUID.randomUUID().toString()
        reportSaved = false
        activeCaseId = chosen?.id
        activeCaseVersion = test?.revision
        activeCaseReference = test
        activePreset = presetId
        activeModel = runCatching {
            AudioEngineProfileStore(this).load()?.dialogue?.name
        }.getOrNull().orEmpty()
        if (!adaptive && test != null) manualInput.setText(test.opening)
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
        val model = runCatching { AudioEngineProfileStore(this).load()?.dialogue?.name }
            .getOrNull() ?: getString(R.string.lab_model_unavailable)
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
        lastState = next
        currentTurnTotal = next.totalTurns
        saveTerminalReport(next)
        if (next.phase == SkillLabPhase.STARTING) {
            renderedTurnCount = 0
            userReadingOlderTurns = false
            visiblePending.clear()
            stopBannerShown = false
            timeline.removeAllViews()
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
        emptyTranscript.visibility = if (next.completed.isEmpty() && next.pending == null)
            View.VISIBLE else View.GONE
        progress.visibility = View.GONE
        progress.max = next.totalTurns
        progress.progress = next.completed.size
        progress.contentDescription = getString(R.string.lab_ui_progress,
            next.completed.size, next.totalTurns)
        runButton.isEnabled = next.manualStartAllowed
        adaptiveButton.isEnabled = next.manualStartAllowed
        // Editing context during a running native session must not mutate it.
        contextBudgetInput.isEnabled = next.manualStartAllowed
        runButton.visibility = View.VISIBLE
        sendButton.isEnabled = next.phase == SkillLabPhase.INPUT_READY
        manualInput.isEnabled = next.phase == SkillLabPhase.INPUT_READY
        stopButton.visibility = if (!next.manualStartAllowed && next.phase in
            setOf(SkillLabPhase.STARTING, SkillLabPhase.INPUT_READY,
                SkillLabPhase.WAITING, SkillLabPhase.TURN_COMPLETED)) View.VISIBLE else View.GONE

        currentTurn.visibility = if (next.phase == SkillLabPhase.READY) View.GONE
            else View.VISIBLE
        when (next.phase) {
            SkillLabPhase.READY -> {
                status.text = getString(R.string.lab_manual_empty)
                currentTurn.text = getString(R.string.lab_manual_empty)
            }
            SkillLabPhase.STARTING -> {
                status.text = getString(R.string.lab_ui_starting)
                currentTurn.text = getString(R.string.lab_ui_waiting_initial)
            }
            SkillLabPhase.INPUT_READY -> {
                status.text = getString(R.string.lab_manual_ready)
                currentTurn.text = getString(R.string.lab_manual_ready)
            }
            SkillLabPhase.WAITING -> next.pending?.let { pending ->
                status.text = getString(R.string.lab_ui_waiting,
                    next.completed.size + 1, next.totalTurns)
                currentTurn.text = getString(R.string.lab_ui_working_on,
                    pending.storyTitle, pending.turnIndex)
            }
            SkillLabPhase.TURN_COMPLETED -> next.completed.lastOrNull()?.let { item ->
                status.text = getString(R.string.lab_ui_progress,
                    next.completed.size, next.totalTurns)
                currentTurn.text = getString(R.string.lab_ui_last_result,
                    item.observation.story, item.observation.index, item.observation.elapsedMs)
            }
            SkillLabPhase.COMPLETED -> {
                next.metrics?.let(::showMetrics)
                ratingGroup.visibility = View.VISIBLE
                status.text = getString(R.string.lab_ui_complete)
                currentTurn.text = getString(R.string.lab_ui_progress,
                    next.completed.size, next.totalTurns)
                followLatestCardIfEnabled()
            }
            SkillLabPhase.STOPPED -> {
                status.text = getString(R.string.lab_ui_cancelled)
                currentTurn.text = getString(R.string.lab_ui_stopped_partial)
                if (!stopBannerShown) {
                    showStopNotice()
                    stopBannerShown = true
                }
            }
            SkillLabPhase.FAILED -> renderFailure(next.error ?: "unknown_failure")
        }
    }

    /** Explicitly distinct error state, shared by model/timeout failures and UI tests. */
    private fun renderFailure(reason: String) {
        status.text = getString(R.string.lab_ui_failed, reason)
        currentTurn.text = getString(R.string.lab_ui_stopped_partial)
        followLatestCardIfEnabled()
    }

    private fun showStopNotice() {
        val note = UiDesign.caption(this, getString(R.string.lab_ui_stop_banner,
            progress.progress, currentTurnTotal)).apply {
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
        status.text = getString(R.string.lab_ui_waiting, completedBefore + 1, currentTurnTotal)
        currentTurn.text = getString(R.string.lab_ui_working_on, story, index)
        if (index == 1) {
            timeline.addView(UiDesign.heading(this, story).apply {
                textSize = 17f
                tag = "lab-story:" + storyIndex
            }, UiDesign.spaceAfter(this, 8))
        }
        val card = UiDesign.card(this, 12).apply { tag = "lab-turn:" + storyIndex + ":" + index }
        card.addView(UiDesign.heading(this, getString(R.string.lab_ui_turn_short,
            index)).apply { textSize = 15f }, UiDesign.spaceAfter(this, 8))
        card.addView(UiDesign.caption(this, getString(R.string.lab_ui_input)),
            UiDesign.spaceAfter(this, 4))
        card.addView(TextView(this).apply {
            text = utterance
            textSize = 14f
            setTextColor(UiDesign.ink)
            setTextIsSelectable(true)
        }, UiDesign.spaceAfter(this, 8))
        card.addView(UiDesign.caption(this, getString(R.string.lab_ui_waiting_response)).apply {
            tag = "lab-pending:" + storyIndex + ":" + index
        })
        timeline.addView(card, UiDesign.spaceAfter(this, 10))
        followCardIfEnabled(card)
    }

    private fun showTurnCompleted(storyIndex: Int, record: SkillLabObservation, completed: Int) {
        val card = timeline.findViewWithTag<LinearLayout>(
            "lab-turn:" + storyIndex + ":" + record.index) ?: return
        card.findViewWithTag<View>("lab-pending:" + storyIndex + ":" + record.index)?.let {
            card.removeView(it)
        }
        card.addView(UiDesign.caption(this, getString(R.string.lab_ui_output)),
            UiDesign.spaceAfter(this, 4))
        card.addView(TextView(this).apply {
            text = record.reply.trim().ifBlank { getString(R.string.lab_ui_empty_reply) }
            textSize = 15f
            setTextColor(UiDesign.ink)
            setLineSpacing(UiDesign.dp(this@ConversationSkillLabActivity, 3).toFloat(), 1f)
            setTextIsSelectable(true)
            tag = "lab-reply:" + storyIndex + ":" + record.index
        }, UiDesign.spaceAfter(this, 8))
        val matched = record.expectedEnd == record.actualEnd
        val manualDecision = getString(R.string.lab_manual_end_observed,
            getString(if (record.actualEnd) R.string.lab_ui_end_yes else R.string.lab_ui_end_no),
            record.elapsedMs)
        card.addView(TextView(this).apply {
            text = if (lastState.manual) manualDecision else getString(R.string.lab_ui_decision,
                getString(if (record.expectedEnd) R.string.lab_ui_end_yes else R.string.lab_ui_end_no),
                getString(if (record.actualEnd) R.string.lab_ui_end_yes else R.string.lab_ui_end_no),
                record.elapsedMs) + " · " +
                getString(if (matched) R.string.lab_ui_match else R.string.lab_ui_mismatch)
            textSize = 12f
            setTextColor(if (lastState.manual || matched) UiDesign.secondary else UiDesign.danger)
            tag = "lab-decision:" + storyIndex + ":" + record.index
        })
        progress.progress = completed
        progress.contentDescription = getString(R.string.lab_ui_progress, completed, currentTurnTotal)
        status.text = getString(R.string.lab_ui_progress, completed, currentTurnTotal)
        currentTurn.text = getString(R.string.lab_ui_last_result,
            record.story, record.index, record.elapsedMs)
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
        if (::runner.isInitialized) runner.close()
        super.onDestroy()
    }
}
