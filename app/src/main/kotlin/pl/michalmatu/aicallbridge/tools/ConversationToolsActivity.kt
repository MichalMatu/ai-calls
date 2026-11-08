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
import pl.michalmatu.aicallbridge.ui.UiActionTone

/** Presentation and user interactions only; capability checks remain in app-owned policy. */
class ConversationToolsActivity : UiChromeActivity(AppTab.AGENT) {
    override fun toolbarTitle(): String = getString(R.string.tools_ui_title)
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
        var storedSelection = controller.enabled().toSet()
        val enabled = storedSelection
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@ConversationToolsActivity, 20),
                UiDesign.dp(this@ConversationToolsActivity, 16),
                UiDesign.dp(this@ConversationToolsActivity, 20),
                UiDesign.dp(this@ConversationToolsActivity, 28))
        }
        content.addView(UiDesign.caption(this, getString(R.string.tools_ui_description)),
            UiDesign.spaceAfter(this, 16))
        val choices = ConversationToolId.entries.associateWith { tool ->
            val card = UiDesign.card(this, 12)
            val choice = CheckBox(this).apply {
                text = when (tool) {
                    ConversationToolId.END_CALL -> getString(R.string.tools_ui_end_call_title)
                }
                isChecked = tool in enabled
                minimumHeight = UiDesign.dp(this@ConversationToolsActivity, 48)
            }
            card.addView(choice, UiDesign.spaceAfter(this, 4))
            card.addView(UiDesign.caption(this, when (tool) {
                ConversationToolId.END_CALL -> getString(R.string.tools_ui_end_call_detail)
            }))
            content.addView(card, UiDesign.spaceAfter(this, 12))
            choice
        }
        val feedback = UiDesign.caption(this, "")
        val save = UiDesign.action(this, getString(R.string.tools_ui_save), UiActionTone.PRIMARY) {
            // Listener below owns the latest selected state.
        }.apply { tag = "tools-save-settings"; isEnabled = false }
        save.setOnClickListener {
            val selected = choices.filterValues { it.isChecked }.keys
            val success = controller.save(selected)
            feedback.text = getString(if (success) R.string.tools_ui_saved else R.string.tools_ui_failed)
            if (success) {
                storedSelection = selected.toSet()
                save.isEnabled = false
            }
        }
        choices.values.forEach { choice ->
            choice.setOnCheckedChangeListener { _, _ ->
                save.isEnabled = choices.filterValues { it.isChecked }.keys != storedSelection
            }
        }
        content.addView(save, UiDesign.spaceAfter(this, 10))
        content.addView(feedback, UiDesign.spaceAfter(this, 16))
        content.addView(UiDesign.heading(this, getString(R.string.tools_ui_latest_status)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 8))
        status = UiDesign.caption(this, "").apply { tag = "tools-latest-status" }
        content.addView(status, UiDesign.spaceAfter(this, 10))
        content.addView(UiDesign.caption(this, getString(R.string.tools_ui_end_hint)))
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
