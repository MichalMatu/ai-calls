package pl.michalmatu.aicallbridge.tools

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

/** Presentation and user interactions only; capability checks remain in app-owned policy. */
class ConversationToolsActivity : UiChromeActivity(AppTab.SETTINGS) {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var controller: ConversationToolsSettingsController
    private val refresh = object : Runnable {
        override fun run() {
            status.text = controller.latestStatus()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = ConversationToolsSettingsController(this)
        val enabled = controller.enabled()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@ConversationToolsActivity, 24),
                UiDesign.dp(this@ConversationToolsActivity, 28),
                UiDesign.dp(this@ConversationToolsActivity, 24),
                UiDesign.dp(this@ConversationToolsActivity, 28))
        }
        content.addView(UiDesign.heading(this, getString(R.string.tools_ui_title)))
        content.addView(TextView(this).apply {
            text = getString(R.string.tools_ui_description)
            setPadding(0, UiDesign.dp(this@ConversationToolsActivity, 8),
                0, UiDesign.dp(this@ConversationToolsActivity, 14))
        })
        val choices = ConversationToolId.entries.associateWith { tool ->
            CheckBox(this).apply {
                text = when (tool) {
                    ConversationToolId.END_CALL ->
                        getString(R.string.tools_ui_end_call_title) + "\n" +
                        getString(R.string.tools_ui_end_call_detail)
                }
                isChecked = tool in enabled
                minimumHeight = UiDesign.dp(this@ConversationToolsActivity, 48)
                content.addView(this)
            }
        }
        val feedback = TextView(this)
        content.addView(UiDesign.button(this, getString(R.string.tools_ui_save)) {
            feedback.text = getString(
                if (controller.save(choices.filterValues { it.isChecked }.keys)) {
                    R.string.tools_ui_saved
                } else R.string.tools_ui_failed)
        }.apply { tag = "tools-save-settings" })
        content.addView(feedback)
        status = TextView(this).apply { textSize = 16f }
        content.addView(status)
        content.addView(TextView(this).apply { text = getString(R.string.tools_ui_end_hint) })
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }
}
