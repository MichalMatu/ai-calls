package pl.michalmatu.aicallbridge.debug

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import pl.michalmatu.aicallbridge.campaign.CampaignToolActivity

/** Visible owner-facing entry for the same module used from Mac ADB. No second state store. */
class UsbDebugPanelActivity : UiChromeActivity(AppTab.SETTINGS) {
    private lateinit var state: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@UsbDebugPanelActivity, 32), UiDesign.dp(this@UsbDebugPanelActivity, 36), UiDesign.dp(this@UsbDebugPanelActivity, 32), UiDesign.dp(this@UsbDebugPanelActivity, 36))
        }
        fun label(value: String, size: Float = 15f) {
            content.addView(TextView(this).apply {
                text = value
                textSize = size
                setPadding(0, UiDesign.dp(this@UsbDebugPanelActivity, 8), 0, UiDesign.dp(this@UsbDebugPanelActivity, 12))
            })
        }
        fun button(value: String, action: () -> Unit) {
            content.addView(Button(this).apply {
                text = value
                setOnClickListener { action() }
            })
        }

        label("AI Calls — USB / ADB diagnostyka", 22f)
        label("Lokalny panel odczytu. Mac Local Agent używa istniejącej sondy DUMP " +
            "oraz prywatnego eksportera; dane rozmów nie trafiają do GitHub ani logcat.")
        state = TextView(this).apply {
            textSize = 14f
            setTextIsSelectable(true)
        }
        content.addView(state)
        button("Odśwież diagnostykę") { refresh() }
        button("Ustawienia silników (wspólne z aplikacją)") {
            startActivity(Intent(this, AudioEngineSettingsActivity::class.java))
        }
        button("Granty, STOP i prywatne transkrypty") {
            startActivity(Intent(this, CampaignToolActivity::class.java))
        }
        label("Mac: ai_calls_usb_debug.py status | profile-set | profile-reset | " +
            "grant-revoke | evidence-export | probe parakeet-pl.\n" +
            "Eksport pełnych nagrań i transkryptów odbywa się wyłącznie na Macu. " +
            "Z poziomu USB nie można tworzyć grantów, wymuszać telefonowania ani omijać STOP.")
        setContentView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::state.isInitialized) refresh()
    }

    private fun refresh() {
        state.text = runCatching {
            UsbDebugBridge.snapshot(this).toString(2)
        }.getOrDefault("Nie udało się odczytać bieżącego stanu.")
    }
}
