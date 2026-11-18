package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.TelecomManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversation.ConversationChatPanel
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiDesign

/** State-specific human controls. No automatic dial, take-over or disconnect. */
class AndroidCallControlsActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var number: EditText
    private lateinit var idleControls: LinearLayout
    private lateinit var ringingControls: LinearLayout
    private lateinit var activeControls: LinearLayout
    private lateinit var takeOverButton: Button
    private lateinit var resumeAiButton: Button
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshState = object : Runnable {
        override fun run() {
            refresh()
            refreshHandler.postDelayed(this, 500L)
        }
    }

    private fun group(name: String) = LinearLayout(this).apply {
        tag = name
        orientation = LinearLayout.VERTICAL
    }

    private fun action(
        parent: LinearLayout, name: String, label: Int,
        tone: UiActionTone = UiActionTone.SECONDARY, click: () -> Unit,
    ): Button = UiDesign.action(this, getString(label), tone, click).apply {
        tag = name
        minimumHeight = UiDesign.dp(this@AndroidCallControlsActivity, 56)
        parent.addView(this, UiDesign.spaceAfter(this@AndroidCallControlsActivity, 12))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UiDesign.background
        window.navigationBarColor = UiDesign.surface
        val body = UiDesign.content(this)
        body.addView(UiDesign.heading(this, getString(R.string.dialer_ui_title)),
            UiDesign.spaceAfter(this, 12))
        val statusCard = UiDesign.card(this)
        status = UiDesign.status(this, getString(R.string.dialer_ui_no_call)).apply {
            tag = "manual-call-status"
            textSize = 18f
        }
        statusCard.addView(status)
        body.addView(statusCard, UiDesign.spaceAfter(this, 20))

        idleControls = group("manual-call-idle-controls")
        number = EditText(this).apply {
            tag = "manual-call-number"
            hint = getString(R.string.phone_ui_field)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            minimumHeight = UiDesign.dp(this@AndroidCallControlsActivity, 56)
            setText(intent?.data?.schemeSpecificPart.orEmpty())
        }
        idleControls.addView(number, UiDesign.spaceAfter(this, 12))
        action(idleControls, "manual-call-dial", R.string.dialer_ui_dial, UiActionTone.PRIMARY) {
            val destination = number.text.toString().trim()
            if (destination.isEmpty()) {
                status.text = getString(R.string.dialer_ui_number_required)
            } else if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
            } else {
                runCatching {
                    getSystemService(TelecomManager::class.java)?.placeCall(
                        Uri.fromParts("tel", destination, null), Bundle())
                }.onFailure { status.text = getString(R.string.dialer_ui_dial_error) }
            }
        }
        action(idleControls, "manual-call-settings", R.string.dialer_ui_answer_settings) {
            startActivity(Intent(this@AndroidCallControlsActivity,
                pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsActivity::class.java))
        }
        body.addView(idleControls)

        ringingControls = group("manual-call-ringing-controls")
        action(ringingControls, "manual-call-answer", R.string.dialer_ui_answer, UiActionTone.PRIMARY) {
            AndroidOwnedInCallService.incomingCall()?.let { call ->
                AndroidOwnedInCallService.takeOverIncoming(call)
                runCatching { call.answer(0) }
            }
            refresh()
        }
        action(ringingControls, "manual-call-reject", R.string.dialer_ui_reject, UiActionTone.DANGER) {
            AndroidOwnedInCallService.incomingCall()?.let { call ->
                AndroidOwnedInCallService.takeOverIncoming(call)
                runCatching { call.reject(false, null) }
            }
            refresh()
        }
        body.addView(ringingControls)

        activeControls = group("manual-call-active-controls")
        takeOverButton = action(activeControls, "manual-call-take-over",
            R.string.dialer_ui_take_over, UiActionTone.PRIMARY) {
            AndroidOwnedInCallService.currentCall()?.let { call ->
                AndroidOwnedInCallService.takeOverIncoming(call)
                if (AppOwnedClirCampaignExecutor.takeOver(call)) {
                    startService(Intent(this@AndroidCallControlsActivity,
                        AppOwnedCampaignService::class.java)
                        .setAction(AppOwnedCampaignService.ACTION_PAUSE_AFTER_TAKEOVER))
                }
            }
            refresh()
        }
        resumeAiButton = action(activeControls, "manual-call-resume-ai",
            R.string.dialer_ui_resume_ai) {
            val call = AndroidOwnedInCallService.currentCall()
            val started = call != null && AndroidOwnedInCallService.resumeIncoming(call)
            refresh()
            if (!started) status.append("\n" + getString(R.string.dialer_ui_resume_failed))
        }
        action(activeControls, "manual-call-end", R.string.dialer_ui_end_displayed,
            UiActionTone.DANGER) {
            AndroidOwnedInCallService.currentCall()?.disconnect()
            refresh()
        }
        body.addView(activeControls)

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiDesign.background)
            addView(ScrollView(this@AndroidCallControlsActivity).apply {
                addView(body)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(ConversationChatPanel(this@AndroidCallControlsActivity))
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.removeCallbacks(refreshState)
        refreshHandler.post(refreshState)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshState)
        super.onPause()
    }

    private fun refresh() {
        val call = AndroidOwnedInCallService.currentCall()
        val ringing = call?.state == Call.STATE_RINGING
        val inCall = call != null && !ringing && call.state != Call.STATE_DISCONNECTED &&
            call.state != Call.STATE_DISCONNECTING
        idleControls.visibility = if (!ringing && !inCall) View.VISIBLE else View.GONE
        ringingControls.visibility = if (ringing) View.VISIBLE else View.GONE
        activeControls.visibility = if (inCall) View.VISIBLE else View.GONE

        val assistant = call?.let { AndroidOwnedInCallService.incomingState(it) }
        val manual = assistant?.substringBefore(':') == "MANUAL"
        takeOverButton.visibility = if (inCall && !manual) View.VISIBLE else View.GONE
        resumeAiButton.visibility = if (inCall && manual && call?.state == Call.STATE_ACTIVE)
            View.VISIBLE else View.GONE

        status.text = (if (call == null) getString(R.string.dialer_ui_no_call)
        else when (call.state) {
            Call.STATE_RINGING -> getString(R.string.dialer_ui_ringing)
            Call.STATE_ACTIVE -> getString(R.string.dialer_ui_active)
            Call.STATE_HOLDING -> getString(R.string.dialer_ui_holding)
            Call.STATE_DISCONNECTED -> getString(R.string.dialer_ui_disconnected)
            else -> getString(R.string.dialer_ui_connecting)
        }) + (assistant?.let { raw ->
            "\n" + if (raw == "MANUAL:gsm_shell_host_unavailable") {
                getString(R.string.dialer_ui_gsm_shell_recovery)
            } else {
                getString(R.string.dialer_ui_assistant_status, raw)
            }
        } ?: "")
    }
}
