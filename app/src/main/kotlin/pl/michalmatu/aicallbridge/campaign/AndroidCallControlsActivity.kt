package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.TelecomManager
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import pl.michalmatu.aicallbridge.LiveCallChatPanel
import android.widget.TextView

/** Small functional call UI required when the user consciously selects AI Calls as dialer. */
class AndroidCallControlsActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var number: EditText

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
            addView(button("Odbierz") {
                AndroidOwnedInCallService.incomingCall()?.answer(0)
                refresh()
            })
            addView(button("Odrzuć") {
                AndroidOwnedInCallService.incomingCall()?.reject(false, null)
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
            addView(LiveCallChatPanel(this@AndroidCallControlsActivity))
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val call = AndroidOwnedInCallService.currentCall()
        status.text = if (call == null) "Brak trwającego połączenia"
        else "Połączenie: stan ${call.state}"
    }
}
