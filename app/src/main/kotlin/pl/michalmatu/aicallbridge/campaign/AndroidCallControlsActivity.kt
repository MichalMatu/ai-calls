package pl.michalmatu.aicallbridge.campaign

import pl.michalmatu.aicallbridge.R

import pl.michalmatu.aicallbridge.conversation.ConversationChatPanel

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.telecom.Call
import android.telecom.TelecomManager
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Small functional call UI required when the user consciously selects AI Calls as dialer. */
class AndroidCallControlsActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var number: EditText
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshState = object : Runnable {
        override fun run() { refresh(); refreshHandler.postDelayed(this, 500L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply { textSize = 18f }
        number = EditText(this).apply {
            hint = getString(R.string.phone_ui_field)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(intent?.data?.schemeSpecificPart.orEmpty())
        }
        fun button(label: String, click: () -> Unit): Button =
            Button(this).apply { text = label; setOnClickListener { click() } }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            addView(status)
            addView(number)
            addView(button(getString(R.string.dialer_ui_dial)) {
                val destination = number.text.toString().trim()
                if (destination.isEmpty()) {
                    status.text = getString(R.string.dialer_ui_number_required)
                } else if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
                } else {
                    runCatching {
                        getSystemService(TelecomManager::class.java)?.placeCall(
                            Uri.fromParts("tel", destination, null), Bundle()
                        )
                    }.onFailure { status.text = getString(R.string.dialer_ui_dial_error) }
                }
            })
            addView(button(getString(R.string.dialer_ui_answer)) {
                AndroidOwnedInCallService.incomingCall()?.let { call ->
                    AndroidOwnedInCallService.takeOverIncoming(call)
                    runCatching { call.answer(0) }
                }
                refresh()
            })
            addView(button(getString(R.string.dialer_ui_take_over)) {
                AndroidOwnedInCallService.currentCall()?.let { call ->
                    AndroidOwnedInCallService.takeOverIncoming(call)
                    if (AppOwnedClirCampaignExecutor.takeOver(call)) {
                        startService(Intent(this@AndroidCallControlsActivity,AppOwnedCampaignService::class.java)
                            .setAction(AppOwnedCampaignService.ACTION_PAUSE_AFTER_TAKEOVER))
                    }
                }
                refresh()
            })
            addView(button(getString(R.string.dialer_ui_resume_ai)) {
                val call=AndroidOwnedInCallService.currentCall()
                val started=call!=null && AndroidOwnedInCallService.resumeIncoming(call)
                refresh()
                if (!started) status.append("\n" + getString(R.string.dialer_ui_resume_failed))
            })
            addView(button(getString(R.string.dialer_ui_answer_settings)) {
                startActivity(Intent(this@AndroidCallControlsActivity,
                    pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsActivity::class.java))
            })
            addView(button(getString(R.string.dialer_ui_reject)) {
                AndroidOwnedInCallService.incomingCall()?.let { call ->
                    AndroidOwnedInCallService.takeOverIncoming(call)
                    runCatching { call.reject(false, null) }
                }
                refresh()
            })
            addView(button(getString(R.string.dialer_ui_end_displayed)) {
                // Only an explicit human UI action; autonomous calls use exact owned session.
                AndroidOwnedInCallService.currentCall()?.disconnect()
                refresh()
            })
        }
        // The dialer UI is what the owner sees during the active cellular
        // call. Keep the live chat pinned below the manual call controls.
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ScrollView(this@AndroidCallControlsActivity).apply {
                addView(layout, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            ))
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
        status.text = if (call == null) getString(R.string.dialer_ui_no_call)
        else when(call.state) {
            Call.STATE_RINGING -> getString(R.string.dialer_ui_ringing)
            Call.STATE_ACTIVE -> getString(R.string.dialer_ui_active)
            Call.STATE_HOLDING -> getString(R.string.dialer_ui_holding)
            Call.STATE_DISCONNECTED -> getString(R.string.dialer_ui_disconnected)
            else -> getString(R.string.dialer_ui_connecting)
        } + (AndroidOwnedInCallService.incomingState(call)?.let { "\n" + getString(R.string.dialer_ui_assistant_status, it) } ?: "")
    }
}
