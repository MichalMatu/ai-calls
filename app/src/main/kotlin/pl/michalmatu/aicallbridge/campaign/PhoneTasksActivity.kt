package pl.michalmatu.aicallbridge.campaign

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.widget.*
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversation.LiveConversationOverlay
import pl.michalmatu.aicallbridge.session.LocalAdbGsmSetupActivity
import pl.michalmatu.aicallbridge.conversation.ConversationHistoryActivity
import pl.michalmatu.aicallbridge.ui.*
import pl.michalmatu.aicallbridge.presentation.tasks.PhoneTaskListController
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.activity.OnBackPressedCallback

internal object PhoneTaskLabels {
    fun state(task: PhoneTask): Int = when (task.state) {
        PhoneTaskState.CANCELLED -> R.string.task_cancelled
        PhoneTaskState.FINISHED -> R.string.task_finished
        PhoneTaskState.PAUSED -> R.string.task_paused
        PhoneTaskState.READY -> when (task.recipients.flatMap { it.attempts }.firstOrNull { it.endedAt == null }?.stage) {
            PhoneAttemptStage.PREPARING -> R.string.task_preparing
            PhoneAttemptStage.DIALING -> R.string.task_dialing
            PhoneAttemptStage.TALKING -> R.string.task_talking
            null -> if (task.startAt > System.currentTimeMillis()) R.string.task_scheduled else R.string.task_waiting
        }
    }
    fun recipient(value: PhoneTaskRecipient): Int = when (value.state) {
        PhoneRecipientState.QUEUED -> if (value.attempts.isEmpty()) R.string.task_queued else R.string.task_retry_wait
        PhoneRecipientState.IN_FLIGHT -> when (value.attempts.last().stage) {
            PhoneAttemptStage.PREPARING -> R.string.task_preparing
            PhoneAttemptStage.DIALING -> R.string.task_dialing
            PhoneAttemptStage.TALKING -> R.string.task_talking
        }
        PhoneRecipientState.ANSWERED -> R.string.task_answered
        PhoneRecipientState.UNKNOWN -> when (value.attempts.lastOrNull()?.outcome) {
            PhoneAttemptOutcome.BUSY -> R.string.task_busy_number
            PhoneAttemptOutcome.NO_ANSWER -> R.string.task_no_answer
            else -> R.string.task_unknown
        }
        PhoneRecipientState.SKIPPED -> R.string.task_skipped
        PhoneRecipientState.EXPIRED -> R.string.task_expired
    }
    fun issue(value: PhoneTaskIssue): Int = when (value) {
        PhoneTaskIssue.PHONE_BUSY -> R.string.task_issue_busy
        PhoneTaskIssue.PERMISSIONS -> R.string.task_issue_permissions
        PhoneTaskIssue.DIALER -> R.string.task_issue_dialer
        PhoneTaskIssue.SIM_CHANGED -> R.string.task_issue_sim
        PhoneTaskIssue.MODEL -> R.string.task_issue_model
        PhoneTaskIssue.SPEECH_LANGUAGE_CHANGED -> R.string.task_issue_speech_language
        PhoneTaskIssue.SHIZUKU, PhoneTaskIssue.GSM_HOST_UNAVAILABLE -> R.string.task_issue_gsm_host
        PhoneTaskIssue.ALARM_PERMISSION -> R.string.task_alarm_permission
        PhoneTaskIssue.BACKGROUND_START -> R.string.task_issue_background
        PhoneTaskIssue.INTERRUPTED -> R.string.task_issue_interrupted
        PhoneTaskIssue.STORAGE -> R.string.task_storage_error
        PhoneTaskIssue.TAKEOVER -> R.string.task_issue_takeover
        PhoneTaskIssue.EXECUTION -> R.string.task_issue_execution
    }
    fun time(epoch: Long, zone: String): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.of(zone))
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
}

/** Owner-facing queue and evidence. It never interprets a finished call as a confirmed booking. */
class PhoneTasksActivity : UiChromeActivity(AppTab.HOME) {
    override fun toolbarTitle() = getString(R.string.task_list)
    private var selectedId: String? = null
    private var previous: List<PhoneTask>? = null
    private lateinit var body: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var reading = false
    private var deleting = false
    // Keep user-expanded evidence open across the 2-second task-state refresh.
    private val expandedRecipients = mutableSetOf<String>()
    private val detailsBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { selectedId = null; previous?.let(::render) }
    }
    private val refresh = object : Runnable {
        override fun run() { load(); handler.postDelayed(this, 2_000L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, detailsBack)
        selectedId = savedInstanceState?.getString("task_id")
            ?: intent.getStringExtra(AppOwnedCampaignService.EXTRA_PHONE_TASK_ID)
        body = UiDesign.content(this)
        setContentView(LiveConversationOverlay(this, ScrollView(this).apply { addView(body) }))
    }
    private fun load() {
        if (reading || !active) return
        reading = true
        PhoneTaskWork.io.execute {
            val result = runCatching {
                PhoneTaskWork.recover(this)
                PhoneTaskWork.store(this).updatePhoneTasks { list -> list.map {
                    PhoneTaskQueue.settle(it, System.currentTimeMillis())
                } }
            }
            runOnUiThread {
                reading = false
                if (!active || isDestroyed) return@runOnUiThread
                result.onSuccess {
                    if (it != previous) { previous = it; render(it) }
                }.onFailure {
                    body.removeAllViews()
                    body.addView(UiDesign.caption(this, getString(R.string.task_storage_error)))
                }
            }
        }
    }
    private fun render(tasks: List<PhoneTask>) {
        body.removeAllViews()
        val task = tasks.firstOrNull { it.id == selectedId }
        detailsBack.isEnabled = task != null
        if (task == null) {
            selectedId = null
            body.tag = "task-list"
            body.addView(UiDesign.action(this, getString(R.string.task_new), UiActionTone.PRIMARY) {
                startActivity(Intent(this, PhoneTaskWizardActivity::class.java))
            }, UiDesign.spaceAfter(this, 16))
            if (tasks.isEmpty()) body.addView(UiDesign.caption(this, getString(R.string.task_empty)))
            for (section in PhoneTaskListController.sections(tasks, System.currentTimeMillis())) {
                val group = section.group
                val title = when (group) {
                    PhoneTaskManagement.Group.CURRENT -> R.string.task_group_current
                    PhoneTaskManagement.Group.SCHEDULED -> R.string.task_group_scheduled
                    PhoneTaskManagement.Group.HISTORY -> R.string.task_group_history
                }
                body.addView(UiDesign.heading(this, getString(title)).apply {
                    tag = "task-group:" + group.name.lowercase()
                    textSize = 17f
                }, UiDesign.spaceAfter(this, 10))
                section.tasks.forEach { value ->
                    body.addView(UiDesign.navigationRow(this, value.title, UiDesign.caption(this,
                        getString(PhoneTaskLabels.state(value)) + " · " + getString(R.string.task_progress,
                            value.recipients.count { it.state !in setOf(PhoneRecipientState.QUEUED, PhoneRecipientState.IN_FLIGHT) },
                            value.recipients.size) + "\n" + PhoneTaskLabels.time(value.startAt, value.zoneId))) {
                        selectedId = value.id; render(tasks)
                    }.apply { tag = "task-row:" + value.id }, UiDesign.spaceAfter(this, 12))
                }
            }
            return
        }
        body.tag = "task-details"
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(UiDesign.heading(this, task.title), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(UiDesign.action(this, getString(R.string.task_options)) {
            showTaskOptions(task)
        }.apply { tag = "task-options" }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = UiDesign.dp(this@PhoneTasksActivity, 8)
        })
        body.addView(header, UiDesign.spaceAfter(this, 8))
        body.addView(UiDesign.caption(this, getString(PhoneTaskLabels.state(task))).apply {
            tag = "task-state"; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }, UiDesign.spaceAfter(this, 12))
        body.addView(UiDesign.caption(this, getString(R.string.task_schedule_summary,
            PhoneTaskLabels.time(task.startAt, task.zoneId), PhoneTaskLabels.time(task.endAt, task.zoneId), task.zoneId)),
            UiDesign.spaceAfter(this, 12))
        task.issue?.let { body.addView(UiDesign.caption(this, getString(PhoneTaskLabels.issue(it))), UiDesign.spaceAfter(this, 12)) }
        body.addView(UiDesign.caption(this, getString(if (task.recordAudio)
            R.string.task_record_audio_on else R.string.task_record_audio_off)),
            UiDesign.spaceAfter(this, 12))
        val primaryActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun primaryAction(text: Int, tone: UiActionTone = UiActionTone.SECONDARY,
            onClick: () -> Unit) {
            val button = UiDesign.action(this, getString(text), tone, onClick)
            primaryActions.addView(button, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (primaryActions.childCount > 0) marginStart = UiDesign.dp(this@PhoneTasksActivity, 8)
            })
        }
        if (task.state == PhoneTaskState.READY) primaryAction(R.string.task_pause_short) {
            startService(Intent(this, AppOwnedCampaignService::class.java)
                .setAction(AppOwnedCampaignService.ACTION_PAUSE_PHONE_TASK)
                .putExtra(AppOwnedCampaignService.EXTRA_PHONE_TASK_ID, task.id))
        }
        if (task.state == PhoneTaskState.PAUSED && task.endAt > System.currentTimeMillis())
            primaryAction(R.string.task_resume_short, UiActionTone.PRIMARY) {
                update(task.id) { if (it.state == PhoneTaskState.PAUSED)
                    it.copy(state = PhoneTaskState.READY, issue = null) else it }
            }
        if (task.state in setOf(PhoneTaskState.READY, PhoneTaskState.PAUSED))
            primaryAction(R.string.task_cancel_short, UiActionTone.REVOCATION) {
                startService(Intent(this, AppOwnedCampaignService::class.java)
                    .setAction(AppOwnedCampaignService.ACTION_CANCEL_PHONE_TASK)
                    .putExtra(AppOwnedCampaignService.EXTRA_PHONE_TASK_ID, task.id))
            }
        if (primaryActions.childCount > 0) body.addView(primaryActions, UiDesign.spaceAfter(this, 12))
        if (task.issue != null) body.addView(UiDesign.action(this, getString(
            if (task.issue == PhoneTaskIssue.GSM_HOST_UNAVAILABLE || task.issue == PhoneTaskIssue.SHIZUKU)
                R.string.local_adb_open else R.string.task_settings)) {
            if (task.issue == PhoneTaskIssue.ALARM_PERMISSION && android.os.Build.VERSION.SDK_INT >= 31)
                startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:$packageName")))
            else if (task.issue == PhoneTaskIssue.GSM_HOST_UNAVAILABLE ||
                     task.issue == PhoneTaskIssue.SHIZUKU)
                startActivity(Intent(this, LocalAdbGsmSetupActivity::class.java))
            else startActivity(Intent(this, SettingsHubActivity::class.java))
        }, UiDesign.spaceAfter(this, 12))
        task.recipients.forEach { recipient -> recipientCard(task, recipient) }
        // Less-frequent commands live in the header's More options menu.
    }

    private fun showTaskOptions(task: PhoneTask) {
        val actions = mutableListOf<Pair<Int, () -> Unit>>(
            R.string.task_scope_details to {
                AlertDialog.Builder(this).setTitle(task.title)
                    .setMessage(task.goal + "\n\n" + task.brief + "\n\n" +
                        getString(R.string.task_scope))
                    .setPositiveButton(android.R.string.ok, null).show()
            },
            R.string.task_copy to { copyTask(task) },
        )
        if (PhoneTaskManagement.hasComparison(task)) {
            actions += R.string.task_compare to { showComparison(task) }
        }
        if (PhoneTaskManagement.canDelete(task, PhoneTaskWork.activeTaskId)) {
            actions += R.string.task_delete to { confirmDeleteTask(task) }
        }
        AlertDialog.Builder(this).setTitle(R.string.task_options)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, index ->
                actions[index].second()
            }.show()
    }

    private fun copyTask(task: PhoneTask) {
        PhoneTaskWork.io.execute {
            val result = runCatching {
                PhoneTaskWork.store(this).savePhoneTaskDraft(PhoneTaskListController.duplicateDraft(task))
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                result.onSuccess { startActivity(Intent(this, PhoneTaskWizardActivity::class.java)) }
                    .onFailure { Toast.makeText(this, R.string.task_storage_error, Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun confirmDeleteTask(task: PhoneTask) {
        if (deleting || !PhoneTaskManagement.canDelete(task, PhoneTaskWork.activeTaskId)) return
        AlertDialog.Builder(this).setTitle(R.string.task_delete_title)
            .setMessage(R.string.task_delete_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.task_delete) { _, _ ->
                if (deleting) return@setPositiveButton
                deleting = true
                PhoneTaskWork.io.execute {
                    val result = runCatching {
                        // Re-read and validate atomically under the existing encrypted store lock.
                        // In-flight calls are never removed; no chat, WAV or notebook deletion.
                        PhoneTaskWork.store(applicationContext).updatePhoneTasks { list ->
                            PhoneTaskManagement.removeTerminal(list, task.id, PhoneTaskWork.activeTaskId)
                        }
                    }
                    runOnUiThread {
                        deleting = false
                        if (isDestroyed || isFinishing) return@runOnUiThread
                        result.onSuccess {
                            selectedId = null
                            previous = null
                            body.removeAllViews()
                            load()
                        }.onFailure {
                            Toast.makeText(this, R.string.task_delete_failed, Toast.LENGTH_LONG).show()
                            previous = null
                            load()
                        }
                    }
                }
            }.show()
    }

    private fun showComparison(task: PhoneTask) {
        val entries = PhoneTaskResults.byRecipient(task)
        val container = UiDesign.content(this)
        task.recipients.forEach { recipient ->
            val evidence = requireNotNull(entries[recipient.id])
            val card = UiDesign.card(this)
            card.addView(UiDesign.heading(this, recipient.name.ifBlank { recipient.number }),
                UiDesign.spaceAfter(this, 6))
            card.addView(UiDesign.caption(this, getString(PhoneTaskLabels.recipient(recipient))),
                UiDesign.spaceAfter(this, 12))
            listOf(R.string.task_price to evidence.price,
                R.string.task_availability to evidence.availability,
                R.string.task_terms to evidence.terms).forEach { (label, values) ->
                card.addView(UiDesign.caption(this, getString(label)), UiDesign.spaceAfter(this, 4))
                card.addView(TextView(this).apply {
                    text = if (values.isEmpty()) getString(R.string.task_no_data)
                        else values.joinToString("\n\n")
                    textSize = 15f
                    setTextColor(UiDesign.ink)
                    setTextIsSelectable(true)
                }, UiDesign.spaceAfter(this, 12))
            }
            container.addView(card, UiDesign.spaceAfter(this, 12))
        }
        container.addView(UiDesign.caption(this, getString(R.string.task_quotes_notice)))
        AlertDialog.Builder(this).setTitle(R.string.task_compare)
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun recipientCard(task: PhoneTask, recipient: PhoneTaskRecipient) {
        val card = UiDesign.card(this).apply { tag = "task-recipient:" + recipient.id }
        val recipientRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val recipientInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        recipientInfo.addView(UiDesign.heading(this, recipient.name.ifBlank { recipient.number })
            .apply { textSize = 17f }, UiDesign.spaceAfter(this, 6))
        if (recipient.name.isNotBlank())
            recipientInfo.addView(UiDesign.caption(this, recipient.number), UiDesign.spaceAfter(this, 6))
        recipientInfo.addView(UiDesign.caption(this, getString(PhoneTaskLabels.recipient(recipient))))
        recipientRow.addView(recipientInfo, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (task.state in setOf(PhoneTaskState.READY, PhoneTaskState.PAUSED) &&
            recipient.state == PhoneRecipientState.QUEUED) {
            recipientRow.addView(UiDesign.action(this, getString(R.string.task_skip_short)) {
                update(task.id) { current -> PhoneTaskManagement.skipQueued(current, recipient.id) }
            }.apply { tag = "task-skip:" + recipient.id }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = UiDesign.dp(this@PhoneTasksActivity, 8)
            })
        } else if (PhoneTaskManagement.canRestore(task, recipient.id, System.currentTimeMillis())) {
            recipientRow.addView(UiDesign.action(this, getString(R.string.task_restore)) {
                confirmRestoreRecipient(task, recipient)
            }.apply { tag = "task-restore:" + recipient.id }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = UiDesign.dp(this@PhoneTasksActivity, 8)
            })
        }
        card.addView(recipientRow, UiDesign.spaceAfter(this, 10))
        if (recipient.state == PhoneRecipientState.QUEUED && recipient.nextAt > 0)
            card.addView(UiDesign.caption(this, getString(R.string.task_next_attempt,
                PhoneTaskLabels.time(recipient.nextAt, task.zoneId))), UiDesign.spaceAfter(this, 12))
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "task-recipient-details:" + recipient.id
            visibility = if (recipient.id in expandedRecipients) View.VISIBLE else View.GONE
        }
        val answers = recipient.attempts.flatMap { it.answers }
        if (answers.isNotEmpty()) {
            val result = PhoneTaskResults.comparison(answers)
            listOf(R.string.task_price to result.price, R.string.task_availability to result.availability,
                R.string.task_terms to result.terms).forEach { (label, values) ->
                details.addView(UiDesign.caption(this, getString(label)), UiDesign.spaceAfter(this, 4))
                details.addView(TextView(this).apply {
                    text = if (values.isEmpty()) getString(R.string.task_no_data) else values.joinToString("\n\n")
                    textSize = 15f; setTextColor(UiDesign.ink); setTextIsSelectable(true)
                }, UiDesign.spaceAfter(this, 12))
            }
            details.addView(UiDesign.caption(this, getString(R.string.task_quotes_notice)), UiDesign.spaceAfter(this, 12))
        }
        if (recipient.attempts.isNotEmpty()) details.addView(UiDesign.action(this,
            getString(R.string.task_attempts, recipient.attempts.size)) {
            val text = recipient.attempts.joinToString("\n\n") { attempt ->
                PhoneTaskLabels.time(attempt.startedAt, task.zoneId) + " · " + getString(when (attempt.outcome) {
                    PhoneAttemptOutcome.ANSWERED -> R.string.task_answered
                    PhoneAttemptOutcome.BUSY -> R.string.task_busy_number
                    PhoneAttemptOutcome.NO_ANSWER -> R.string.task_no_answer
                    PhoneAttemptOutcome.BLOCKED -> R.string.task_paused
                    PhoneAttemptOutcome.STOPPED -> R.string.task_cancelled
                    PhoneAttemptOutcome.UNKNOWN -> R.string.task_unknown
                    null -> R.string.task_running
                }) + attempt.answers.joinToString("", prefix = "") { "\n“${it.text}”" }
            }
            val content = UiDesign.content(this).apply { addView(UiDesign.caption(this@PhoneTasksActivity, text)) }
            AlertDialog.Builder(this).setTitle(R.string.task_attempt_history)
                .setView(ScrollView(this).apply { addView(content) }).setPositiveButton(android.R.string.ok, null).show()
        }, UiDesign.spaceAfter(this, 12))
        recipient.attempts.lastOrNull { it.sessionId != null }?.sessionId?.let { sessionId ->
            details.addView(UiDesign.action(this, getString(R.string.task_conversation)) {
                startActivity(Intent(this, ConversationHistoryActivity::class.java)
                    .putExtra("open_session_id", sessionId))
            }, UiDesign.spaceAfter(this, 12))
        }
        if (task.state != PhoneTaskState.CANCELLED && task.endAt > System.currentTimeMillis() &&
            recipient.state == PhoneRecipientState.UNKNOWN) card.addView(UiDesign.action(this, getString(R.string.task_retry_explicit)) {
            // This is an explicit owner request; unknown attempts are never automatically replayed.
            update(task.id) { current -> if (current.state == PhoneTaskState.CANCELLED) current else current.copy(state = PhoneTaskState.READY, issue = null,
                recipients = current.recipients.map {
                    if (it.id == recipient.id && it.state == PhoneRecipientState.UNKNOWN)
                        it.copy(state = PhoneRecipientState.QUEUED, nextAt = System.currentTimeMillis()) else it
                }) }
        })
        if (recipient.attempts.isNotEmpty()) {
            val toggle = UiDesign.action(this,
                getString(if (details.visibility == View.VISIBLE)
                    R.string.task_hide_details else R.string.task_show_details)) {}
                .apply { tag = "task-details-toggle:" + recipient.id }
            toggle.setOnClickListener {
                val expanding = details.visibility != View.VISIBLE
                if (expanding) expandedRecipients.add(recipient.id) else expandedRecipients.remove(recipient.id)
                details.visibility = if (expanding) View.VISIBLE else View.GONE
                toggle.text = getString(if (expanding) R.string.task_hide_details else R.string.task_show_details)
            }
            card.addView(toggle, UiDesign.spaceAfter(this, 8))
            card.addView(details)
        }
        body.addView(card, UiDesign.spaceAfter(this, 16))
    }

    private fun confirmRestoreRecipient(task: PhoneTask, recipient: PhoneTaskRecipient) {
        if (!PhoneTaskManagement.canRestore(task, recipient.id, System.currentTimeMillis())) return
        AlertDialog.Builder(this).setTitle(R.string.task_restore)
            .setMessage(if (task.state == PhoneTaskState.FINISHED)
                R.string.task_restore_finished_warning else R.string.task_restore_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.task_restore) { _, _ ->
                update(task.id) { current ->
                    if (PhoneTaskManagement.canRestore(current, recipient.id, System.currentTimeMillis()))
                        PhoneTaskManagement.restoreSkipped(current, recipient.id, System.currentTimeMillis())
                    else current
                }
            }.show()
    }

    private fun update(id: String, change: (PhoneTask) -> PhoneTask) {
        PhoneTaskWork.io.execute {
            val result = runCatching {
                PhoneTaskWork.store(this).updatePhoneTasks { list -> list.map { if (it.id == id) change(it) else it } }
                PhoneTaskScheduler.reconcile(this)
                PhoneTaskWork.store(this).phoneTasks().any {
                    PhoneTaskQueue.nextAt(it)?.let { due -> due <= System.currentTimeMillis() } == true
                }
            }
            runOnUiThread {
                result.onSuccess { due -> if (due && active && !isDestroyed) runCatching {
                    startForegroundService(Intent(this, AppOwnedCampaignService::class.java)
                        .setAction(AppOwnedCampaignService.ACTION_RUN_PHONE_TASKS))
                }.onFailure { PhoneTaskScheduler.attention(this) } }
                    .onFailure { Toast.makeText(this, R.string.task_storage_error, Toast.LENGTH_LONG).show() }
                load()
            }
        }
    }
    override fun consumeChromeBack(): Boolean {
        if (selectedId == null) return false
        selectedId = null; previous?.let(::render); return true
    }
    override fun onResume() { super.onResume(); active = true; handler.post(refresh) }
    override fun onPause() { active = false; handler.removeCallbacks(refresh); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("task_id", selectedId); super.onSaveInstanceState(outState) }
}
