package pl.michalmatu.aicallbridge

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.campaign.AppOwnedCampaignService
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsActivity
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsController
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode
import pl.michalmatu.aicallbridge.session.GsmMediaTransportReadiness
import pl.michalmatu.aicallbridge.ui.IncomingHomeSummary
import pl.michalmatu.aicallbridge.campaign.PhoneTaskWizardActivity
import pl.michalmatu.aicallbridge.campaign.PhoneTasksActivity
import pl.michalmatu.aicallbridge.campaign.PhoneTaskWork
import pl.michalmatu.aicallbridge.campaign.PhoneTaskState
import pl.michalmatu.aicallbridge.campaign.PhoneTaskScheduler
import pl.michalmatu.aicallbridge.campaign.PhoneTaskLabels
import pl.michalmatu.aicallbridge.campaign.CampaignLivePhase
import pl.michalmatu.aicallbridge.conversation.ConversationChatFeed
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore
import pl.michalmatu.aicallbridge.tools.ConversationToolsActivity
import pl.michalmatu.aicallbridge.ui.HomeActiveStatus
import pl.michalmatu.aicallbridge.conversation.LiveConversationOverlay
import pl.michalmatu.aicallbridge.ui.AppBaseActivity
import pl.michalmatu.aicallbridge.ui.AppNavigation
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.DialerRoleCard

/** Consumer home: a real task entry, one STOP, current state and the shared live chat. */
class MainActivity : AppBaseActivity() {
    private lateinit var dialerRole: DialerRoleCard
    private lateinit var liveStatus: TextView
    private lateinit var actionFeedback: TextView
    private lateinit var taskSummary: TextView
    private lateinit var notebookSummary: TextView
    private lateinit var taskDestination: View
    private lateinit var notebookDestination: View
    private lateinit var stopButton: View
    private lateinit var incomingSummary: TextView
    private lateinit var incomingDestination: View
    private var taskReading = false
    private var lastTaskRead = 0L
    private var diagnosticProbes: MainActivityDeveloperProbes? = null
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRefresh = object : Runnable {
        override fun run() {
            if (::liveStatus.isInitialized) refreshLiveStatus()
            refreshTasks()
            statusHandler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialerRole = DialerRoleCard(this, showOnlyWhenMissing = true)
        liveStatus = UiDesign.caption(this, "").apply {
            tag = "home-live-runtime"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = getString(R.string.home_activity_idle)
        }
        actionFeedback = UiDesign.caption(this, "").apply {
            tag = "home-action-feedback"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }

        taskSummary = UiDesign.caption(this, getString(R.string.task_loading))
        notebookSummary = UiDesign.caption(this, getString(R.string.notebook_ui_loading))
        taskDestination = shortcutCard(R.string.task_list, taskSummary) {
            startActivity(Intent(this, PhoneTasksActivity::class.java))
        }.apply { tag = "home-tasks" }
        notebookDestination = shortcutCard(R.string.home_notebook_shortcut, notebookSummary) {
            startActivity(Intent(this, ConversationToolsActivity::class.java)
                .putExtra(ConversationToolsActivity.EXTRA_NOTEBOOK_ONLY, true))
        }.apply { tag = "home-notebook" }
        val start = UiDesign.action(this, getString(R.string.home_outgoing_action),
            UiActionTone.PRIMARY) {
            startActivity(Intent(this, PhoneTaskWizardActivity::class.java))
        }.apply { tag = "home-task-start" }

        val stop = UiDesign.action(this, getString(R.string.task_stop_all), UiActionTone.REVOCATION) {
            startService(Intent(this, AppOwnedCampaignService::class.java)
                .setAction(AppOwnedCampaignService.ACTION_STOP_ALL_AI))
            actionFeedback.text = getString(R.string.home_ui_stop_sent)
            actionFeedback.visibility = View.VISIBLE
        }.apply { tag = "home-stop-ai"; visibility = View.GONE }
        stopButton = stop

        incomingSummary = UiDesign.caption(this, getString(R.string.home_incoming_summary)).apply {
            tag = "home-incoming-status"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        incomingDestination = UiDesign.navigationRow(this, getString(R.string.incoming_ui_title),
            incomingSummary) {
            startActivity(Intent(this, IncomingAssistantSettingsActivity::class.java))
        }.apply { tag = "home-incoming-settings" }

        val shortcuts = LinearLayout(this).apply {
            tag = "home-shortcuts"
            orientation = LinearLayout.HORIZONTAL
            addView(taskDestination, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = UiDesign.dp(this@MainActivity, 6)
            })
            addView(notebookDestination, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = UiDesign.dp(this@MainActivity, 6)
            })
        }
        val content = UiDesign.content(this).apply {
            tag = "main-screen-root"
            addView(UiDesign.heading(this@MainActivity, getString(R.string.home_title)).apply {
                tag = "home-heading"
            }, UiDesign.spaceAfter(this@MainActivity, 16))
            addView(dialerRole.view, UiDesign.spaceAfter(this@MainActivity, 12))
            addView(incomingDestination, UiDesign.spaceAfter(this@MainActivity, 12))
            addView(UiDesign.card(this@MainActivity).apply {
                tag = "home-new-call-card"
                addView(start, UiDesign.spaceAfter(this@MainActivity, 6))
                addView(UiDesign.caption(this@MainActivity, getString(R.string.home_outgoing_hint)))
            }, UiDesign.spaceAfter(this@MainActivity, 12))
            addView(shortcuts, UiDesign.spaceAfter(this@MainActivity, 12))
            addView(UiDesign.card(this@MainActivity).apply {
                tag = "home-call-status-card"
                addView(UiDesign.caption(this@MainActivity,
                    getString(R.string.home_activity_heading)), UiDesign.spaceAfter(this@MainActivity, 6))
                addView(liveStatus, UiDesign.spaceAfter(this@MainActivity, 10))
                addView(stop)
                addView(actionFeedback)
            })
        }
        val scroll = ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AppNavigation.install(this, LiveConversationOverlay(this@MainActivity, scroll).apply {
            tag = "home-conversation-floating-overlay"
        }, AppTab.HOME)
        // Preserve an explicitly requested read-only diagnostic probe, never expose it as a home action.
        if (intent.getBooleanExtra("run_probe", false)) {
            diagnosticProbes = MainActivityDeveloperProbes(this, actionFeedback).also {
                it.attach()
                it.runCapabilityProbe()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::dialerRole.isInitialized) dialerRole.refresh()
        lastTaskRead = 0L
        statusHandler.removeCallbacks(statusRefresh)
        statusHandler.post(statusRefresh)
    }

    override fun onPause() {
        statusHandler.removeCallbacks(statusRefresh)
        super.onPause()
    }

    override fun onDestroy() {
        diagnosticProbes?.close()
        super.onDestroy()
    }

    /** Compact two-column navigation; the counts never expose notebook text or phone numbers. */
    private fun shortcutCard(titleId: Int, summary: TextView, onClick: () -> Unit) =
        UiDesign.card(this, 14).apply {
            minimumHeight = UiDesign.dp(this@MainActivity, 96)
            background = UiDesign.touchSurface(this@MainActivity)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(UiDesign.heading(this@MainActivity, getString(titleId)).apply {
                textSize = 16f
            }, UiDesign.spaceAfter(this@MainActivity, 10))
            addView(summary)
            contentDescription = getString(titleId) + ". " + summary.text
        }

    private fun refreshLiveStatus() {
        val state = HomeActiveStatus.resolve(
            AppOwnedCampaignService.liveUiPhase(), ConversationChatFeed.snapshot().active,
            PhoneTaskWork.activeTaskId != null)
        liveStatus.text = getString(state.label)
        stopButton.visibility = if (state.showStop) View.VISIBLE else View.GONE
        if (!state.showStop) actionFeedback.visibility = View.GONE
    }

    private fun refreshTasks() {
        if (taskReading || System.currentTimeMillis() - lastTaskRead < 5_000L) return
        taskReading = true
        lastTaskRead = System.currentTimeMillis()
        PhoneTaskWork.io.execute {
            val result = runCatching {
                PhoneTaskWork.recover(this)
                PhoneTaskWork.store(this).phoneTasks()
            }
            val notebookCount = runCatching {
                AndroidConversationPreparationStore(applicationContext).notebooks().size
            }
            val incoming = runCatching {
                val controller = IncomingAssistantSettingsController(applicationContext)
                val policy = controller.current().getOrThrow()
                val selectedSimPresent = policy.accountKey?.let { accountKey ->
                    controller.selectedAccountIndex(controller.availableAccounts(), accountKey) >= 0
                } ?: false
                val hostReady = policy.mode == AnsweringMode.AI && selectedSimPresent &&
                    GsmMediaTransportReadiness.isAvailable(applicationContext)
                IncomingHomeSummary.resource(policy.mode, selectedSimPresent, hostReady)
            }
            runOnUiThread {
                taskReading = false
                if (isDestroyed) return@runOnUiThread
                incomingSummary.text = getString(incoming.getOrDefault(R.string.home_incoming_read_error))
                incomingDestination.contentDescription = getString(R.string.incoming_ui_title) +
                    ". " + incomingSummary.text.toString()
                taskSummary.text = result.fold(
                    onSuccess = { tasks -> resources.getQuantityString(
                        R.plurals.home_task_count, tasks.size, tasks.size) },
                    onFailure = { getString(R.string.home_count_unavailable) })
                notebookSummary.text = notebookCount.fold(
                    onSuccess = { count -> resources.getQuantityString(
                        R.plurals.home_notebook_count, count, count) },
                    onFailure = { getString(R.string.home_count_unavailable) })
                taskDestination.contentDescription = getString(R.string.task_list) +
                    ". " + taskSummary.text
                notebookDestination.contentDescription = getString(R.string.home_notebook_shortcut) +
                    ". " + notebookSummary.text
            }
        }
    }
}
