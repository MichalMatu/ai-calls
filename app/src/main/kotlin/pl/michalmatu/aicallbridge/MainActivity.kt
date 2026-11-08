package pl.michalmatu.aicallbridge

import pl.michalmatu.aicallbridge.conversation.LiveConversationOverlay
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.AppNavigation
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.AppBaseActivity

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import pl.michalmatu.aicallbridge.campaign.CampaignLiveUiLabels
import android.view.ViewGroup
import android.view.WindowInsets
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import pl.michalmatu.aicallbridge.campaign.CampaignToolActivity
import pl.michalmatu.aicallbridge.campaign.AppOwnedCampaignService
import pl.michalmatu.aicallbridge.campaign.CampaignAuthorizationStore
import pl.michalmatu.aicallbridge.campaign.CampaignAccountScope
import pl.michalmatu.aicallbridge.campaign.ClirCampaignSpec
import pl.michalmatu.aicallbridge.campaign.CampaignAuthorizationPolicy
import pl.michalmatu.aicallbridge.developerrelay.SupervisedCallPilotFailure
import pl.michalmatu.aicallbridge.developerrelay.SupervisedCallReadinessDiagnostics
import pl.michalmatu.aicallbridge.identity.PhoneEnrollmentActivity
import pl.michalmatu.aicallbridge.runtime.CallAudioMode
import pl.michalmatu.aicallbridge.runtime.CallRuntimePreferences
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider

class MainActivity : AppBaseActivity() {
    private lateinit var statusView: TextView
    private lateinit var taskStatusView: TextView
    private lateinit var homeLiveStatusView: TextView
    private lateinit var homeActionFeedback: TextView
    private val liveStateHandler = Handler(Looper.getMainLooper())
    private val liveStateRefresh = object : Runnable {
        override fun run() {
            if (::homeLiveStatusView.isInitialized) {
                homeLiveStatusView.text = getString(
                    CampaignLiveUiLabels.title(AppOwnedCampaignService.liveUiPhase()))
            }
            liveStateHandler.postDelayed(this, 1_000L)
        }
    }
    private lateinit var runtimePreferences: CallRuntimePreferences
    private lateinit var selectedAudioMode: CallAudioMode
    private lateinit var selectedTextLlmProvider: TextLlmProvider
    private var displayedModel: pl.michalmatu.aicallbridge.audioengines.DialogueModelId? = null
    private lateinit var developerProbes: MainActivityDeveloperProbes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runtimePreferences = CallRuntimePreferences(this)
        val initialSelection = runtimePreferences.load()
        selectedAudioMode = initialSelection.audioMode
        selectedTextLlmProvider = initialSelection.textLlmProvider

        statusView = TextView(this).apply {
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = runtimeSelectionSummary()
            textSize = 13f
            setTextColor(Color.rgb(39, 58, 83))
            background = roundedSurface(Color.rgb(234, 243, 253))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextIsSelectable(true)
        }
        taskStatusView = TextView(this).apply {
            tag = "home-task-authorization"
            text = taskAuthorizationSummary()
            textSize = 13f
            setTextColor(Color.rgb(39, 58, 83))
            background = roundedSurface(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        homeLiveStatusView = TextView(this).apply {
            tag = "home-live-runtime"
            textSize = 13f
            setTextColor(Color.rgb(39, 58, 83))
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = getString(CampaignLiveUiLabels.title(AppOwnedCampaignService.liveUiPhase()))
        }
        homeActionFeedback = TextView(this).apply {
            tag = "home-action-feedback"
            text = getString(R.string.home_ux06_no_action)
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
            textSize = 13f
            setTextColor(Color.rgb(39, 58, 83))
            background = roundedSurface(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextIsSelectable(true)
        }
        developerProbes = MainActivityDeveloperProbes(this, homeActionFeedback)

        val oneTapButton = Button(this).apply {
            text = getString(R.string.home_ui_one_tap)
            setOnClickListener { startOneTapSupervisedTest() }
        }
        val stopAiButton = Button(this).apply {
            text = getString(R.string.ui_stop_ai)
            setOnClickListener {
                startService(
                    Intent(this@MainActivity, AppOwnedCampaignService::class.java)
                        .setAction(AppOwnedCampaignService.ACTION_STOP_ALL_AI),
                )
                homeActionFeedback.text = getString(R.string.home_ui_stop_sent)
            }
        }

        val startAuthorizedTaskButton = Button(this).apply {
            text = getString(R.string.home_ui_start)
            setOnClickListener {
                // This tap enters the same existing app-owned START flow, without
                // requiring a second click in the campaign settings screen.
                startActivity(
                    Intent(this@MainActivity, CampaignToolActivity::class.java)
                        .putExtra(CampaignToolActivity.EXTRA_START_AUTHORIZED_CAMPAIGN, true),
                )
            }
        }

        val campaignToolsButton = Button(this).apply {
            text = getString(R.string.ui_campaigns)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, CampaignToolActivity::class.java))
            }
        }

        // Bottom tabs are the only routes to History, Lab and Settings.

        // Home contains only unique task, campaign, STOP and off-call assistant actions.
        fun styleAction(button: Button, color: Int, ink: Int) {
            button.isAllCaps = false
            button.textSize = 14f
            button.minHeight = dp(48)
            button.setPadding(dp(10), dp(10), dp(10), dp(10))
            button.setTextColor(ink)
            button.background = roundedSurface(color)
            button.stateListAnimator = null
            button.elevation = 0f
        }
        styleAction(startAuthorizedTaskButton, Color.rgb(26, 72, 137), Color.WHITE)
        styleAction(stopAiButton, Color.rgb(255, 232, 236), Color.rgb(151, 31, 51))
        styleAction(campaignToolsButton, Color.rgb(229, 237, 249), Color.rgb(27, 47, 75))
        styleAction(oneTapButton, Color.rgb(237, 243, 251), Color.rgb(27, 47, 75))
        startAuthorizedTaskButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(12) }
        oneTapButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) }

        val technicalDetails = UiAccordionSection(this, "home-technical",
            getString(R.string.ux09_home_technical_title)).apply {
            addControl(statusView)
            addControl(UiDesign.caption(this@MainActivity, getString(R.string.home_ui_task_hint)))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
            addView(TextView(this@MainActivity).apply {
                text = "AI Calls"
                textSize = 25f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(23, 39, 62))
            })
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.ui_subtitle)
                textSize = 13f
                setTextColor(Color.rgb(86, 100, 117))
                setPadding(0, 0, 0, dp(12))
            })
            addView(taskStatusView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) })
            addView(homeLiveStatusView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) })
            addView(homeActionFeedback, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) })
            addView(startAuthorizedTaskButton)
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.home_ux06_direct_clir_scope)
                textSize = 12f
                setTextColor(Color.rgb(86, 100, 117))
                setPadding(0, dp(5), 0, dp(8))
            })
            val actions = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                campaignToolsButton.layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
                ).apply { rightMargin = dp(8) }
                stopAiButton.layoutParams = LinearLayout.LayoutParams(
                    dp(108), ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                addView(campaignToolsButton)
                addView(stopAiButton)
            }
            addView(actions, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) })
            addView(oneTapButton)
            addView(technicalDetails, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(16) })
        }

        // Pinned chat dock: visible without scrolling the settings above it.
        // Live text is only held in process memory, not in saved view state.
        val root = LinearLayout(this).apply {
            tag = "main-screen-root"
            setBackgroundColor(Color.rgb(247, 249, 253))
            setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(0, bars.top, 0, bars.bottom)
                } else {
                    view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                }
                insets
            }
            orientation = LinearLayout.VERTICAL
            addView(LiveConversationOverlay(this@MainActivity,
                ScrollView(this@MainActivity).apply {
                    addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT))
                }).apply { tag = "home-conversation-floating-overlay" },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(AppNavigation.bar(this@MainActivity, AppTab.HOME))
        }
        window.statusBarColor = Color.rgb(247, 249, 253)
        window.navigationBarColor = Color.rgb(14, 23, 39)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContentView(root)

        developerProbes.attach()

        if (intent.getBooleanExtra(EXTRA_RUN_CAPABILITY_PROBE, false)) {
            developerProbes.runCapabilityProbe()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun roundedSurface(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(12).toFloat()
    }

    override fun onResume() {
        super.onResume()
        liveStateHandler.removeCallbacks(liveStateRefresh)
        liveStateHandler.post(liveStateRefresh)
        val current = runtimePreferences.load()
        val selected = pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore(this).load()?.dialogue
        taskStatusView.text = taskAuthorizationSummary()
        if (selected != displayedModel ||
            current.audioMode != selectedAudioMode ||
            current.textLlmProvider != selectedTextLlmProvider) {
            displayedModel = selected
            selectedAudioMode = current.audioMode
            selectedTextLlmProvider = current.textLlmProvider
            statusView.text = runtimeSelectionSummary()
        }
    }

    override fun onPause() {
        liveStateHandler.removeCallbacks(liveStateRefresh)
        super.onPause()
    }

    override fun onDestroy() {
        if (::developerProbes.isInitialized) developerProbes.close()
        super.onDestroy()
    }

    private fun taskAuthorizationSummary(): String {
        val grant = runCatching { CampaignAuthorizationStore(this).load() }.getOrNull()
        val currentScope = if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED) CampaignAccountScope.currentVoiceSubscription(this)
        else null
        val state = when {
            grant == null -> getString(R.string.home_ui_task_none)
            grant.revokedAtEpochMs != null -> getString(R.string.home_ui_task_revoked)
            currentScope == null -> getString(R.string.home_ux06_sim_unavailable)
            !CampaignAuthorizationPolicy.matchesExactTask(
                grant, ClirCampaignSpec.enableRequest(currentScope)
            ) -> getString(R.string.home_ux06_sim_mismatch)
            else -> getString(R.string.home_ui_task_authorized)
        }
        return getString(R.string.home_ui_task_heading) + "\n" + state
    }

    private fun runtimeSelectionSummary(): String = buildString {
        append(getString(R.string.home_ui_audio_mode_label)).append(": ")
            .append(selectedAudioMode.displayName).append('\n')
        when (selectedAudioMode) {
            CallAudioMode.LOCAL_STT_TTS -> {
                append(getString(R.string.home_ui_provider_label)).append(": ")
                    .append(selectedTextLlmProvider.displayName).append('\n')
            }
            CallAudioMode.OPENAI_REALTIME_AUDIO ->
                append(getString(R.string.home_ui_openai_realtime)).append('\n')
            CallAudioMode.LOCAL_REALTIME_AUDIO ->
                append(getString(R.string.home_ui_local_realtime)).append('\n')
        }
        val profile = pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore(this@MainActivity).load()
        if (profile == null) {
            append(getString(R.string.home_ui_invalid_engines))
            return@buildString
        }
        val model = pl.michalmatu.aicallbridge.textagent.LanguageModelCatalog[profile.dialogue]
        val readiness = pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness.check(this@MainActivity, model.id)
        append(getString(R.string.home_ui_dialogue_model_label)).append(": ")
            .append(model.title).append('\n')
        append(getString(R.string.home_ui_readiness_label)).append(": ")
            .append(getString(if (readiness.state.toString() == "READY")
                R.string.home_ui_ready else R.string.home_ui_needs_check))
    }

    /**
     * One explicit on-screen opt-in replaces multi-screen/manual attach clicks.
     * It never dials, hangs up, impersonates the dialer, or weakens the media gate.
     * The same user action continues after Android permission approval.
     */
    private fun startOneTapSupervisedTest() {
        val required = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
        )
        val missing = required.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            homeActionFeedback.text = getString(R.string.home_one_tap_permission)
            requestPermissions(missing.toTypedArray(), REQUEST_ONE_TAP_SUPERVISED)
            return
        }
        val snapshot = SupervisedCallReadinessDiagnostics.capture(this)
        val reportSaved = SupervisedCallReadinessDiagnostics.write(this, snapshot)
        val reportInfo = getString(if (reportSaved) R.string.home_one_tap_report_saved else R.string.home_one_tap_report_failed)
        when (snapshot.failure) {
            null -> {
                homeActionFeedback.text = getString(R.string.home_one_tap_starting)
                runCatching {
                    startForegroundService(
                        Intent(this, AppOwnedCampaignService::class.java)
                            .setAction(AppOwnedCampaignService.ACTION_START_SUPERVISED_AUDIO),
                    )
                }.onFailure {
                    homeActionFeedback.text = getString(R.string.home_one_tap_service_failed, it.javaClass.simpleName)
                }
            }
            SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL ->
                homeActionFeedback.text = getString(R.string.home_one_tap_no_call, reportInfo)
            SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY ->
                homeActionFeedback.text = getString(R.string.home_one_tap_route, snapshot.audioModeLabel, reportInfo)
            SupervisedCallPilotFailure.SHIZUKU_NOT_READY ->
                homeActionFeedback.text = getString(R.string.home_one_tap_shizuku, reportInfo)
            SupervisedCallPilotFailure.PERMISSION_REQUIRED ->
                homeActionFeedback.text = getString(R.string.home_one_tap_permissions, reportInfo)
        }
    }

    private fun requestMicrophonePermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            homeActionFeedback.text = getString(R.string.home_mic_already)
            return
        }

        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_ONE_TAP_SUPERVISED) {
            if (permissions.isNotEmpty() && permissions.indices.all {
                    grantResults.getOrNull(it) == PackageManager.PERMISSION_GRANTED
                }
            ) {
                startOneTapSupervisedTest()
            } else {
                homeActionFeedback.text = getString(R.string.home_one_tap_denied)
            }
            return
        }

        if (requestCode == REQUEST_RECORD_AUDIO) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            homeActionFeedback.text = if (granted) {
                getString(R.string.home_mic_granted)
            } else {
                getString(R.string.home_mic_denied)
            }
        }
    }

    private companion object {
        const val REQUEST_ONE_TAP_SUPERVISED = 1007
        const val REQUEST_RECORD_AUDIO = 1001
        const val EXTRA_RUN_CAPABILITY_PROBE = "run_probe"
    }
}
