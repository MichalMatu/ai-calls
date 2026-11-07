package pl.michalmatu.aicallbridge.campaign

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
            hint = "Numer telefonu"
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
            addView(button("Zadzwoń (ręcznie)") {
                val destination = number.text.toString().trim()
                if (destination.isEmpty()) {
                    status.text = "Wpisz numer"
                } else if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
                } else {
                    runCatching {
                        getSystemService(TelecomManager::class.java)?.placeCall(
                            Uri.fromParts("tel", destination, null), Bundle()
                        )
                    }.onFailure { status.text = "Nie można wybrać numeru" }
                }
            })
            addView(button("Odbierz ręcznie") {
                AndroidOwnedInCallService.incomingCall()?.let { call ->
                    AndroidOwnedInCallService.takeOverIncoming(call)
                    runCatching { call.answer(0) }
                }
                refresh()
            })
            addView(button("Przejmij rozmowę — zatrzymaj AI") {
                AndroidOwnedInCallService.currentCall()?.let { call ->
                    AndroidOwnedInCallService.takeOverIncoming(call)
                    if (AppOwnedClirCampaignExecutor.takeOver(call)) {
                        startService(Intent(this@AndroidCallControlsActivity,AppOwnedCampaignService::class.java)
                            .setAction(AppOwnedCampaignService.ACTION_PAUSE_AFTER_TAKEOVER))
                    }
                }
                refresh()
            })
            addView(button("Włącz AI w rozmowie przychodzącej") {
                val call=AndroidOwnedInCallService.currentCall()
                val started=call!=null && AndroidOwnedInCallService.resumeIncoming(call)
                refresh()
                if (!started) status.append("\nAI nie zostało uruchomione. Sprawdź tryb AI, kartę SIM i gotowość w ustawieniach odbierania.")
            })
            addView(button("Ustawienia odbierania AI / ręcznie") {
                startActivity(Intent(this@AndroidCallControlsActivity,
                    pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsActivity::class.java))
            })
            addView(button("Odrzuć") {
                AndroidOwnedInCallService.incomingCall()?.let { call ->
                    AndroidOwnedInCallService.takeOverIncoming(call)
                    runCatching { call.reject(false, null) }
                }
                refresh()
            })
            addView(button("Zakończ wyświetlaną rozmowę") {
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
        status.text = if (call == null) "Brak trwającego połączenia"
        else when(call.state) {
            Call.STATE_RINGING -> "Połączenie przychodzące"
            Call.STATE_ACTIVE -> "Rozmowa trwa"
            Call.STATE_HOLDING -> "Rozmowa wstrzymana"
            Call.STATE_DISCONNECTED -> "Rozmowa zakończona"
            else -> "Łączenie…"
        } + (AndroidOwnedInCallService.incomingState(call)?.let { "\nAsystent: $it" } ?: "")
    }
}
