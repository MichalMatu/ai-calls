package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.text.InputType
import android.view.View
import android.widget.*
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversationcore.*
import pl.michalmatu.aicallbridge.ui.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

/** One wizard, one final owner approval, independent of recipient count. Navigation never dials. */
class PhoneTaskWizardActivity : UiChromeActivity(AppTab.HOME) {
    override fun toolbarTitle() = getString(R.string.task_new)
    private var draft = PhoneTaskDraft()
    private var loaded = false
    private var approved = false
    private var saving = false
    private var pendingApproval: PhoneTaskDraft? = null
    private var waitingForAlarmPermission = false
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (pendingApproval != null && pendingApproval == draft && results.values.all { it }) approve()
        else { pendingApproval = null; if (loaded) render() }
    }
    private val systemApproval = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = if (waitingForAlarmPermission) PhoneTaskScheduler.allowed(this)
            else AndroidTelecomOwnedCallController.canControl(this)
        if (pendingApproval != null && pendingApproval == draft && granted) approve()
        else { pendingApproval = null; if (loaded) render() }
    }
    private val stepBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { backStep() }
    }
    private var capture: () -> PhoneTaskDraft = { draft }
    private lateinit var feedback: TextView
    private var personal = ScenarioPersonalLibrary()
    private val language get() = resources.configuration.locales[0].language
    private val definition get() = requireNotNull(ScenarioCatalog.byId(draft.scenarioId))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, stepBack)
        setContentView(UiDesign.caption(this, getString(R.string.task_loading)))
        PhoneTaskWork.io.execute {
            val result = runCatching {
                val store = PhoneTaskWork.store(this)
                val library = store.loadPersonalScenarios()
                val incomingId = intent.getStringExtra(ScenarioOutgoingCallActivity.EXTRA_SCENARIO_ID)
                val next = if (savedInstanceState == null && ScenarioCatalog.byId(incomingId.orEmpty()) != null) {
                    PhoneTaskDraft(scenarioId = requireNotNull(incomingId),
                        title = intent.getStringExtra(ScenarioOutgoingCallActivity.EXTRA_SCENARIO_TITLE).orEmpty(),
                        goal = intent.getStringExtra(ScenarioOutgoingCallActivity.EXTRA_SCENARIO_GOAL).orEmpty(),
                        suppliedBrief = intent.getStringExtra(ScenarioOutgoingCallActivity.EXTRA_SCENARIO_BRIEF).orEmpty())
                } else store.phoneTaskDraft()?.takeUnless { value -> store.phoneTasks().any { it.id == value.id } }
                    ?: PhoneTaskDraft()
                next to library
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (value, library) ->
                    draft = value
                    personal = library
                    loaded = true
                    render()
                }.onFailure { setContentView(UiDesign.caption(this, getString(R.string.task_storage_error))) }
            }
        }
    }

    private fun render() {
        stepBack.isEnabled = draft.step > 0
        capture = { draft }
        val body = UiDesign.content(this).apply { tag = "task-wizard" }
        val titles = listOf(R.string.task_step_goal, R.string.task_step_recipients,
            R.string.task_step_schedule, R.string.task_step_review)
        body.addView(UiDesign.caption(this, getString(R.string.task_step, draft.step + 1, 4)), UiDesign.spaceAfter(this, 6))
        body.addView(UiDesign.heading(this, getString(titles[draft.step])), UiDesign.spaceAfter(this, 16))
        when (draft.step) {
            0 -> goalStep(body)
            1 -> recipientsStep(body)
            2 -> scheduleStep(body)
            3 -> reviewStep(body)
        }
        feedback = UiDesign.caption(this, "").apply {
            tag = "task-feedback"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(feedback, UiDesign.spaceAfter(this, 12))
        body.addView(UiDesign.action(this, getString(if (draft.step == 3)
            if (draft.scheduled) R.string.task_approve_schedule else R.string.task_approve_start
            else R.string.task_next), UiActionTone.PRIMARY) {
            draft = capture()
            if (validateStep()) {
                if (draft.step == 3) approve()
                else { draft = draft.copy(step = draft.step + 1); persist(); render() }
            }
        }.apply { tag = if (draft.step == 3) "task-approve" else "task-next" }, UiDesign.spaceAfter(this, 12))
        if (draft.step > 0) body.addView(UiDesign.action(this, getString(R.string.task_back)) { backStep() })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(body) })
    }

    private fun input(body: LinearLayout, label: Int, value: String, key: String,
        multiline: Boolean = false, hintValue: String = ""): EditText = input(body, getString(label), value, key, multiline, hintValue)
    private fun input(body: LinearLayout, label: String, value: String, key: String,
        multiline: Boolean = false, hintValue: String = ""): EditText {
        val field = EditText(this).apply {
            tag = key
            id = View.generateViewId()
            isSaveEnabled = false
            inputType = InputType.TYPE_CLASS_TEXT or if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
            setSingleLine(!multiline)
            if (multiline) minLines = 3
            setText(value)
            hint = hintValue
            textSize = 16f
            setTextColor(UiDesign.ink)
            setHintTextColor(UiDesign.secondary)
            background = UiDesign.surfaceDrawable(this@PhoneTaskWizardActivity, UiDesign.softAccent)
            val pad = UiDesign.dp(this@PhoneTaskWizardActivity, 12)
            setPadding(pad, pad, pad, pad)
            minimumHeight = UiDesign.dp(this@PhoneTaskWizardActivity, 48)
        }
        body.addView(UiDesign.caption(this, label).apply { labelFor = field.id }, UiDesign.spaceAfter(this, 6))
        body.addView(field, UiDesign.spaceAfter(this, 16))
        return field
    }

    private fun goalStep(body: LinearLayout) {
        val templates = ScenarioCatalog.templates
        val variants = personal.items
        val spinner = Spinner(this).apply { tag = "task-scenario"; isSaveEnabled = false }
        UiSelect.setOptions(spinner, templates.map { it.title(language) } + variants.map { it.current.title })
        val variantIndex = variants.indexOfFirst { it.id == draft.personalScenarioId }
        spinner.setSelection(if (variantIndex >= 0) templates.size + variantIndex
            else templates.indexOfFirst { it.id == draft.scenarioId }.coerceAtLeast(0))
        body.addView(spinner, UiDesign.spaceAfter(this, 16))
        val title = input(body, R.string.scenario_ui_name,
            draft.title.ifBlank { definition.title(language) }, "task-title")
        val goal = input(body, R.string.scenario_ui_goal,
            draft.goal.ifBlank { definition.goal(language) }, "task-goal", true)
        val fields = linkedMapOf<String, EditText>()
        val supplied = if (draft.suppliedBrief.isNotBlank())
            input(body, R.string.task_questions, draft.suppliedBrief, "task-brief", true)
        else null
        if (supplied == null) definition.fields.forEach { field ->
            fields[field.id] = input(body, field.title(language) + if (field.required) " *" else "",
                draft.fields[field.id] ?: field.defaultValue, "task-field:${field.id}",
                field.id in setOf("additional_instructions", "issue"), field.placeholder(language))
        }
        capture = { draft.copy(title = title.text.toString().trim(), goal = goal.text.toString().trim(),
            fields = fields.mapValues { it.value.text.toString().trim() },
            suppliedBrief = supplied?.text?.toString()?.trim().orEmpty()) }
        var initial = true
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (initial) { initial = false; return }
                if (position < templates.size) {
                    val selected = templates[position]
                    draft = draft.copy(scenarioId = selected.id, personalScenarioId = null, title = selected.title(language),
                        goal = selected.goal(language), fields = selected.draft().values, suppliedBrief = "")
                } else {
                    val selected = variants[position - templates.size]
                    draft = draft.copy(scenarioId = selected.templateId, personalScenarioId = selected.id, title = selected.current.title,
                        goal = selected.current.goal, fields = selected.current.values, suppliedBrief = "")
                }
                persist(); render()
            }
        }
    }

    private fun recipientsStep(body: LinearLayout) {
        val recipients = input(body, R.string.task_numbers, draft.recipients, "task-recipients", true,
            getString(R.string.task_numbers_hint))
        body.addView(UiDesign.caption(this, getString(R.string.task_numbers_help)), UiDesign.spaceAfter(this, 16))
        val sim = Spinner(this).apply { tag = "task-sim"; isSaveEnabled = false }
        val allowed = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val subscriptions = if (allowed) runCatching {
            getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty().mapNotNull { info ->
                CampaignAccountScope.forSubscription(this, info.subscriptionId)?.let { scope ->
                    Triple(info.subscriptionId, scope, info.displayName.toString())
                }
            }
        }.getOrDefault(emptyList()) else emptyList()
        // The blank entry is essential when the previously selected SIM disappeared.
        UiSelect.setOptions(sim, listOf(getString(R.string.task_choose_sim)) + subscriptions.map { it.third })
        val index = subscriptions.indexOfFirst { it.first == draft.subscriptionId && it.second == draft.accountScope }
        sim.setSelection(if (index >= 0) index + 1 else if (draft.subscriptionId == null && subscriptions.size == 1) 1 else 0)
        body.addView(UiDesign.caption(this, getString(R.string.scenario_call_sim)), UiDesign.spaceAfter(this, 6))
        body.addView(sim, UiDesign.spaceAfter(this, 12))
        if (!allowed) body.addView(UiDesign.action(this, getString(R.string.task_phone_permission)) {
            draft = capture()
            permissions.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
        })
        capture = {
            val selected = subscriptions.getOrNull(sim.selectedItemPosition - 1)
            draft.copy(recipients = recipients.text.toString(), subscriptionId = selected?.first,
                accountScope = selected?.second)
        }
    }

    private fun scheduleStep(body: LinearLayout) {
        val later = CheckBox(this).apply {
            text = getString(R.string.task_later); tag = "task-later"; isChecked = draft.scheduled
        }
        body.addView(later, UiDesign.spaceAfter(this, 12))
        val start = dateButton(R.string.task_start_time, draft.startLocal) {
            val current = capture()
            val start = LocalDateTime.parse(it)
            draft = current.copy(startLocal = it, endLocal = if (LocalDateTime.parse(current.endLocal) <= start)
                start.plusHours(1).toString() else current.endLocal)
            render()
        }.apply { tag = "task-start-time"; visibility = if (draft.scheduled) View.VISIBLE else View.GONE }
        body.addView(start, UiDesign.spaceAfter(this, 12))
        body.addView(dateButton(R.string.task_end_time, draft.endLocal) {
            draft = capture().copy(endLocal = it); render()
        }.apply { tag = "task-end-time" }, UiDesign.spaceAfter(this, 8))
        body.addView(UiDesign.caption(this, getString(R.string.task_schedule_help, draft.zoneId)), UiDesign.spaceAfter(this, 16))
        val retry = CheckBox(this).apply {
            text = getString(R.string.task_retry); tag = "task-retry"; isChecked = draft.retry
        }
        body.addView(retry, UiDesign.spaceAfter(this, 12))
        val minutes = input(body, R.string.task_retry_minutes, draft.retryMinutes, "task-retry-minutes").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            isEnabled = draft.retry
        }
        later.setOnCheckedChangeListener { _, checked -> start.visibility = if (checked) View.VISIBLE else View.GONE }
        retry.setOnCheckedChangeListener { _, checked -> minutes.isEnabled = checked }
        capture = { draft.copy(scheduled = later.isChecked, retry = retry.isChecked, retryMinutes = minutes.text.toString()) }
    }

    private fun dateButton(label: Int, value: String, picked: (String) -> Unit): Button {
        val local = LocalDateTime.parse(value)
        return UiDesign.action(this, getString(label) + "\n" + local.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))) {
            DatePickerDialog(this, { _, year, month, day ->
                TimePickerDialog(this, { _, hour, minute ->
                    picked(LocalDateTime.of(year, month + 1, day, hour, minute).toString())
                }, local.hour, local.minute, true).show()
            }, local.year, local.monthValue - 1, local.dayOfMonth).show()
        }
    }

    private fun brief(): String = draft.suppliedBrief.ifBlank {
        ScenarioDraft(draft.scenarioId, draft.fields).summary(definition, language)
    }

    private fun reviewStep(body: LinearLayout) {
        val numbers = PhoneTaskInput.recipients(draft.recipients)
        val summary = UiDesign.card(this).apply { tag = "task-review" }
        summary.addView(UiDesign.heading(this, draft.title), UiDesign.spaceAfter(this, 12))
        summary.addView(UiDesign.caption(this, draft.goal + "\n\n" + brief()), UiDesign.spaceAfter(this, 16))
        summary.addView(UiDesign.caption(this, getString(R.string.task_recipient_count, numbers.values.size, numbers.duplicates)), UiDesign.spaceAfter(this, 8))
        summary.addView(UiDesign.caption(this, numbers.values.joinToString("\n") {
            listOf(it.name, it.number).filter(String::isNotBlank).joinToString(" · ")
        }), UiDesign.spaceAfter(this, 16))
        val simName = if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
            runCatching { getSystemService(SubscriptionManager::class.java)
                ?.getActiveSubscriptionInfo(draft.subscriptionId ?: -1)?.displayName?.toString() }.getOrNull() else null
        summary.addView(UiDesign.caption(this, getString(R.string.scenario_call_sim) + ": " +
            (simName ?: getString(R.string.task_choose_sim))), UiDesign.spaceAfter(this, 12))
        summary.addView(UiDesign.caption(this, getString(R.string.task_schedule_summary,
            if (draft.scheduled) LocalDateTime.parse(draft.startLocal).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
            else getString(R.string.task_now),
            LocalDateTime.parse(draft.endLocal).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), draft.zoneId)),
            UiDesign.spaceAfter(this, 12))
        summary.addView(UiDesign.caption(this, if (draft.retry) getString(R.string.task_retry_summary, draft.retryMinutes)
            else getString(R.string.task_no_retry)))
        body.addView(summary, UiDesign.spaceAfter(this, 16))
        body.addView(UiDesign.caption(this, getString(R.string.task_scope)), UiDesign.spaceAfter(this, 16))
    }

    private fun validateStep(): Boolean = try {
        when (draft.step) {
            0 -> {
                require(draft.title.length in 2..140 && draft.goal.length in 5..300 && brief().length in 5..3500)
                require(draft.suppliedBrief.isNotBlank() || ScenarioDraft(draft.scenarioId, draft.fields)
                    .missingRequiredFields(definition).isEmpty())
            }
            1 -> { PhoneTaskInput.recipients(draft.recipients); require(draft.subscriptionId != null && draft.accountScope != null) }
            2, 3 -> buildTask(System.currentTimeMillis())
        }
        true
    } catch (_: Exception) {
        feedback.text = getString(when (draft.step) {
            0 -> R.string.task_invalid_goal
            1 -> R.string.task_invalid_recipients
            else -> R.string.task_invalid_schedule
        })
        false
    }

    private fun buildTask(now: Long): PhoneTask {
        val zone = ZoneId.of(draft.zoneId)
        val start = if (draft.scheduled) PhoneTaskInput.instant(LocalDateTime.parse(draft.startLocal), zone) else now
        require(!draft.scheduled || start > now)
        val end = PhoneTaskInput.instant(LocalDateTime.parse(draft.endLocal), zone)
        val delay = if (draft.retry) Math.multiplyExact(draft.retryMinutes.toLong(), 60_000L).also { require(it > 0) } else null
        return PhoneTask(draft.id, draft.title, draft.scenarioId, draft.goal, brief(),
            requireNotNull(draft.subscriptionId), requireNotNull(draft.accountScope), start, end,
            draft.zoneId, delay, now, PhoneTaskInput.recipients(draft.recipients).values,
            language = if (language == "en") "en" else "pl").validated()
    }

    private fun approve() {
        if (saving || approved) return
        val needed = listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) { pendingApproval = draft; permissions.launch(needed.toTypedArray()); return }
        if (!AndroidTelecomOwnedCallController.canControl(this)) {
            val role = getSystemService(RoleManager::class.java)
            if (role?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
                pendingApproval = draft
                waitingForAlarmPermission = false
                systemApproval.launch(role.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            }
            feedback.text = getString(R.string.scenario_call_role)
            return
        }
        if (!PhoneTaskScheduler.allowed(this)) {
            if (Build.VERSION.SDK_INT >= 31) {
                pendingApproval = draft
                waitingForAlarmPermission = true
                systemApproval.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName")))
            }
            feedback.text = getString(R.string.task_alarm_permission)
            return
        }
        val task = runCatching { buildTask(System.currentTimeMillis()) }.getOrElse {
            feedback.text = getString(R.string.task_invalid_schedule); return
        }
        if (CampaignAccountScope.forSubscription(this, task.subscriptionId) != task.accountScope) {
            feedback.text = getString(R.string.scenario_call_sim_missing); return
        }
        saving = true
        pendingApproval = null
        val approvalGeneration = PhoneTaskWork.generation.get()
        feedback.text = getString(R.string.task_saving)
        PhoneTaskWork.io.execute {
            val result = runCatching {
                PhoneTaskWork.recover(this)
                val store = PhoneTaskWork.store(this)
                store.updatePhoneTasks { tasks ->
                    check(approvalGeneration == PhoneTaskWork.generation.get()) { "task_approval_cancelled" }
                    // Repeated tap, recreation or a retried save must never approve a second copy.
                    if (tasks.any { it.id == task.id }) tasks else tasks + task
                }
                PhoneTaskScheduler.reconcile(this)
                store.savePhoneTaskDraft(PhoneTaskDraft())
            }
            runOnUiThread {
                saving = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess {
                    approved = true
                    if (!draft.scheduled) runCatching {
                        startForegroundService(Intent(this, AppOwnedCampaignService::class.java)
                            .setAction(AppOwnedCampaignService.ACTION_RUN_PHONE_TASKS))
                    }.onFailure { PhoneTaskScheduler.attention(this) }
                    startActivity(Intent(this, PhoneTasksActivity::class.java)
                        .putExtra(AppOwnedCampaignService.EXTRA_PHONE_TASK_ID, task.id))
                    finish()
                }.onFailure { feedback.text = getString(R.string.task_storage_error) }
            }
        }
    }

    private fun persist() {
        if (!loaded || approved || saving) return
        val value = draft
        PhoneTaskWork.io.execute { runCatching { PhoneTaskWork.store(this).savePhoneTaskDraft(value) }
            .onFailure { runOnUiThread { if (::feedback.isInitialized && !isDestroyed)
                feedback.text = getString(R.string.task_storage_error) } } }
    }
    private fun backStep() { draft = capture().copy(step = (draft.step - 1).coerceAtLeast(0)); persist(); render() }
    override fun consumeChromeBack(): Boolean {
        if (loaded && draft.step > 0) { backStep(); return true }
        return false
    }
    override fun onPause() { if (loaded) { draft = capture(); persist() }; super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        if (loaded) { draft = capture(); persist() }
        super.onSaveInstanceState(outState)
    }
}
