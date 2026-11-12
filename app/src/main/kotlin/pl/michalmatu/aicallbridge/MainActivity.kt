package pl.michalmatu.aicallbridge

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.campaign.AppOwnedCampaignService
import pl.michalmatu.aicallbridge.campaign.AppointmentMvpActivity
import pl.michalmatu.aicallbridge.campaign.CampaignLiveUiLabels
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
    private var diagnosticProbes: MainActivityDeveloperProbes? = null
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRefresh = object : Runnable {
        override fun run() {
            if (::liveStatus.isInitialized) {
                liveStatus.text = getString(CampaignLiveUiLabels.title(AppOwnedCampaignService.liveUiPhase()))
            }
            statusHandler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialerRole = DialerRoleCard(this, showOnlyWhenMissing = true)
        liveStatus = UiDesign.caption(this, "").apply {
            tag = "home-live-runtime"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = getString(CampaignLiveUiLabels.title(AppOwnedCampaignService.liveUiPhase()))
        }
        actionFeedback = UiDesign.caption(this, "").apply {
            tag = "home-action-feedback"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }

        val start = UiDesign.action(this, getString(R.string.camp_mvp_appointment_title),
            UiActionTone.PRIMARY) {
            // Open the existing exact-number/SIM consent form. Navigation never dials.
            startActivity(Intent(this, AppointmentMvpActivity::class.java))
        }.apply { tag = "home-appointment-start" }

        val stop = UiDesign.action(this, getString(R.string.ui_stop_ai), UiActionTone.DANGER) {
            startService(Intent(this, AppOwnedCampaignService::class.java)
                .setAction(AppOwnedCampaignService.ACTION_STOP_ALL_AI))
            actionFeedback.text = getString(R.string.home_ui_stop_sent)
            actionFeedback.visibility = View.VISIBLE
        }.apply { tag = "home-stop-ai" }

        val content = UiDesign.content(this).apply {
            tag = "main-screen-root"
            addView(dialerRole.view, UiDesign.spaceAfter(this@MainActivity, 16))
            addView(UiDesign.card(this@MainActivity).apply {
                tag = "home-new-call-card"
                addView(start)
                addView(UiDesign.caption(this@MainActivity,
                    getString(R.string.camp_mvp_appointment_description)),
                    UiDesign.spaceAfter(this@MainActivity, 0).apply {
                        topMargin = UiDesign.dp(this@MainActivity, UiDesign.GAP_SMALL)
                    })
            }, UiDesign.spaceAfter(this@MainActivity, 16))
            addView(UiDesign.card(this@MainActivity).apply {
                tag = "home-call-status-card"
                addView(liveStatus, UiDesign.spaceAfter(this@MainActivity, 12))
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
}
