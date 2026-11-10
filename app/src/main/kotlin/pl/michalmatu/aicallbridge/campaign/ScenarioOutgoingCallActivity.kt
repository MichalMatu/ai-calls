package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore
import pl.michalmatu.aicallbridge.conversationcore.ScenarioCatalog
import pl.michalmatu.aicallbridge.ui.*

/** Private confirmation screen. Opening it never grants, calls, or discloses identity. */
class ScenarioOutgoingCallActivity : UiChromeActivity(AppTab.AGENT) {
    override fun toolbarTitle(): String = getString(R.string.scenario_call_title)
    private val preparation by lazy { AndroidConversationPreparationStore(this) }
    private val grants by lazy { CampaignAuthorizationStore(this) }
    private lateinit var scenarioId: String
    private lateinit var scenarioTitle: String
    private lateinit var scenarioGoal: String
    private lateinit var scenarioBrief: String
    private lateinit var number: EditText
    private lateinit var sim: Spinner
    private lateinit var feedback: TextView
    private val subscriptions = mutableListOf<Pair<Int, String>>()
    private var starting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scenarioId = intent.getStringExtra(EXTRA_SCENARIO_ID).orEmpty()
        scenarioTitle = intent.getStringExtra(EXTRA_SCENARIO_TITLE).orEmpty()
        scenarioGoal = intent.getStringExtra(EXTRA_SCENARIO_GOAL).orEmpty()
        scenarioBrief = intent.getStringExtra(EXTRA_SCENARIO_BRIEF).orEmpty()
        if (ScenarioCatalog.byId(scenarioId) == null ||
            scenarioTitle.length !in 2..140 ||
            scenarioGoal.length !in 5..300 ||
            scenarioBrief.length !in 5..3500
        ) {
            setContentView(UiDesign.caption(this, getString(R.string.scenario_call_missing)))
            return
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiDesign.dp(this@ScenarioOutgoingCallActivity, 20)
            setPadding(pad, pad, pad, pad)
        }
        body.addView(UiDesign.heading(this, scenarioTitle).apply { textSize = 21f },
            UiDesign.spaceAfter(this, 10))
        body.addView(UiDesign.caption(this, getString(R.string.scenario_call_scope)),
            UiDesign.spaceAfter(this, 16))
        val details = UiDesign.card(this)
        details.addView(UiDesign.heading(this, getString(R.string.scenario_call_summary)).apply {
            textSize = 16f
        }, UiDesign.spaceAfter(this, 8))
        details.addView(UiDesign.caption(this, scenarioBrief).apply {
            setTextIsSelectable(true)
        })
        body.addView(details, UiDesign.spaceAfter(this, 16))
        body.addView(UiDesign.caption(this, getString(R.string.scenario_call_number)),
            UiDesign.spaceAfter(this, 6))
        number = EditText(this).apply {
            tag = "scenario-call-number"
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_PHONE
            hint = "+48..."
            setSingleLine(true)
            setText(savedInstanceState?.getString("number").orEmpty())
        }
        body.addView(number, UiDesign.spaceAfter(this, 12))
        body.addView(UiDesign.caption(this, getString(R.string.scenario_call_sim)),
            UiDesign.spaceAfter(this, 6))
        sim = Spinner(this).apply { tag = "scenario-call-sim" }
        body.addView(sim, UiDesign.spaceAfter(this, 12))
        feedback = UiDesign.caption(this, "").apply {
            tag = "scenario-call-feedback"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(UiDesign.action(this, getString(R.string.scenario_call_authorize),
            UiActionTone.PRIMARY) { authorizeAndStart() }.apply {
            tag = "scenario-call-authorize"
        }, UiDesign.spaceAfter(this, 12))
        body.addView(UiDesign.action(this, getString(R.string.scenario_call_stop),
            UiActionTone.DANGER) {
            startService(Intent(this, AppOwnedCampaignService::class.java)
                .setAction(AppOwnedCampaignService.ACTION_CANCEL_OUTGOING_TASK))
            starting = false
            feedback.text = getString(R.string.scenario_call_stop_sent)
        }, UiDesign.spaceAfter(this, 12))
        body.addView(feedback)
        setContentView(ScrollView(this).apply {
            addView(body, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        refreshSim()
    }

    private fun refreshSim() {
        val previous = subscriptions.getOrNull(sim.selectedItemPosition)?.first
        subscriptions.clear()
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) {
            UiSelect.setOptions(sim, listOf(getString(R.string.scenario_call_sim_permission)))
            return
        }
        val infos = runCatching {
            getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
        }.getOrDefault(emptyList())
        for (info in infos) {
            CampaignAccountScope.forSubscription(this, info.subscriptionId)?.let {
                subscriptions.add(info.subscriptionId to it)
            }
        }
        UiSelect.setOptions(sim, if (subscriptions.isEmpty())
            listOf(getString(R.string.scenario_call_sim_missing))
        else subscriptions.mapIndexed { index, pair ->
            getString(R.string.scenario_call_sim_option, index + 1,
                infos.firstOrNull { it.subscriptionId == pair.first }
                    ?.displayName?.toString().orEmpty().take(45))
        })
        val ix = subscriptions.indexOfFirst { it.first == previous }
        sim.setSelection(ix.coerceAtLeast(0))
    }

    private fun authorizeAndStart() {
        if (starting) return
        val needed = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO,
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 972)
            feedback.text = getString(R.string.scenario_call_permissions)
            return
        }
        if (!AndroidTelecomOwnedCallController.canControl(this)) {
            val role = getSystemService(RoleManager::class.java)
            if (role?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
                startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_DIALER), 973)
                feedback.text = getString(R.string.scenario_call_role)
            } else feedback.text = getString(R.string.scenario_call_role_missing)
            return
        }
        val telecom = getSystemService(TelecomManager::class.java)
        if (telecom == null || runCatching { telecom.isInCall }.getOrDefault(true) ||
            AppOwnedClirCampaignExecutor.isRunning() ||
            AppOwnedCampaignService.liveUiPhase() != CampaignLivePhase.INACTIVE ||
            CampaignContinuationStore(this).load() != null
        ) {
            feedback.text = getString(R.string.scenario_call_busy)
            return
        }
        val selected = subscriptions.getOrNull(sim.selectedItemPosition)
        if (selected == null ||
            CampaignAccountScope.forSubscription(this, selected.first) != selected.second ||
            CampaignPhoneAccountResolver.resolveSelectedVoiceAccount(
                this, telecom, selected.first) == null
        ) {
            feedback.text = getString(R.string.scenario_call_sim_missing)
            return
        }
        val profile = AudioEngineProfileStore(this).load()
        if (profile == null || AudioEngineRegistry.incompatibility(profile) != null) {
            feedback.text = getString(R.string.scenario_call_engine_missing)
            return
        }
        val task = runCatching {
            ScenarioOutgoingTask.create(number.text.toString(), selected.first, selected.second,
                scenarioId, scenarioTitle, scenarioGoal, scenarioBrief)
        }.getOrElse {
            feedback.text = getString(R.string.scenario_call_invalid)
            return
        }
        if (!CampaignAuthorizationPolicy.matchesExactTask(
                task.grant(System.currentTimeMillis()), task.request())) {
            feedback.text = getString(R.string.scenario_call_invalid)
            return
        }
        val saved = runCatching {
            preparation.saveOutgoingScenarioTask(task)
            grants.save(task.grant(System.currentTimeMillis()))
        }.isSuccess
        if (!saved) {
            feedback.text = getString(R.string.scenario_call_save_failed)
            return
        }
        try {
            startForegroundService(Intent(this, AppOwnedCampaignService::class.java)
                .setAction(AppOwnedCampaignService.ACTION_START_SCENARIO_INQUIRY))
            starting = true
            feedback.text = getString(R.string.scenario_call_preparing)
        } catch (_: RuntimeException) {
            grants.revoke(System.currentTimeMillis())
            feedback.text = getString(R.string.scenario_call_start_failed)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::sim.isInitialized) refreshSim()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::number.isInitialized) outState.putString("number", number.text.toString())
        super.onSaveInstanceState(outState)
    }
    override fun onRequestPermissionsResult(code: Int, names: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, names, results)
        if (code == 972) {
            refreshSim()
            feedback.text = getString(R.string.scenario_call_confirm_again)
        }
    }
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code, result, data)
        if (code == 973) feedback.text = getString(R.string.scenario_call_confirm_again)
    }

    companion object {
        const val EXTRA_SCENARIO_ID = "scenario_outgoing_id"
        const val EXTRA_SCENARIO_TITLE = "scenario_outgoing_title"
        const val EXTRA_SCENARIO_GOAL = "scenario_outgoing_goal"
        const val EXTRA_SCENARIO_BRIEF = "scenario_outgoing_brief"
    }
}
