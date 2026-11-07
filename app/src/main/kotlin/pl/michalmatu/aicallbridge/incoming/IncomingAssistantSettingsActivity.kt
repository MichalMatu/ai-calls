package pl.michalmatu.aicallbridge.incoming

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import pl.michalmatu.aicallbridge.conversation.ConversationPreparationActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillsActivity
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode
import pl.michalmatu.aicallbridge.conversationcore.IncomingAssistantPolicy
import pl.michalmatu.aicallbridge.tools.ConversationToolsActivity
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign

/** Displays incoming-call preferences; all store/Telecom effects live in the controller. */
class IncomingAssistantSettingsActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = IncomingAssistantSettingsController(this)
        val feedback = TextView(this)
        val policy = controller.current().getOrElse {
            feedback.text = getString(R.string.incoming_ui_read_error)
            IncomingAssistantPolicy("unavailable", AnsweringMode.MANUAL, null, true)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@IncomingAssistantSettingsActivity, 24),
                UiDesign.dp(this@IncomingAssistantSettingsActivity, 28),
                UiDesign.dp(this@IncomingAssistantSettingsActivity, 24),
                UiDesign.dp(this@IncomingAssistantSettingsActivity, 28))
        }
        fun description(value: Int) = TextView(this).apply {
            text = getString(value)
            textSize = 15f
            setTextColor(UiDesign.ink)
            setPadding(0, UiDesign.dp(this@IncomingAssistantSettingsActivity, 7),
                0, UiDesign.dp(this@IncomingAssistantSettingsActivity, 12))
        }
        content.addView(UiDesign.heading(this, getString(R.string.incoming_ui_title)))
        content.addView(description(R.string.incoming_ui_intro))
        val mode = Spinner(this).apply {
            tag = "incoming-mode-spinner"
            adapter = ArrayAdapter(this@IncomingAssistantSettingsActivity,
                android.R.layout.simple_spinner_dropdown_item, listOf(
                    getString(R.string.incoming_ui_mode_manual),
                    getString(R.string.incoming_ui_mode_ai)))
            setSelection(if (policy.mode == AnsweringMode.AI) 1 else 0)
            minimumHeight = UiDesign.dp(this@IncomingAssistantSettingsActivity, 48)
        }
        content.addView(mode)
        val accounts = controller.availableAccounts()
        val account = Spinner(this).apply {
            tag = "incoming-sim-spinner"
            adapter = ArrayAdapter(this@IncomingAssistantSettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                accounts.mapIndexed { i, handle ->
                    controller.displayAccount(handle) ?: getString(R.string.incoming_ui_sim_fallback, i + 1)
                }.ifEmpty { listOf(getString(R.string.incoming_ui_no_sim)) })
            setSelection(controller.selectedAccountIndex(accounts, policy.accountKey))
            minimumHeight = UiDesign.dp(this@IncomingAssistantSettingsActivity, 48)
        }
        content.addView(account)
        val recording = CheckBox(this).apply {
            text = getString(R.string.incoming_ui_record)
            isChecked = policy.recordEvidence
            minimumHeight = UiDesign.dp(this@IncomingAssistantSettingsActivity, 48)
        }
        content.addView(recording)
        content.addView(description(R.string.incoming_ui_engines_hint))
        content.addView(UiDesign.button(this, getString(R.string.incoming_ui_save)) {
            val selected = if (mode.selectedItemPosition == 1) AnsweringMode.AI else AnsweringMode.MANUAL
            val chosen = accounts.getOrNull(account.selectedItemPosition)
            feedback.text = getString(when (controller.save(selected, chosen, recording.isChecked)) {
                IncomingAssistantSettingsController.SaveResult.NEED_SIM -> R.string.incoming_ui_choose_sim
                IncomingAssistantSettingsController.SaveResult.SAVED_AI -> R.string.incoming_ui_saved_ai
                IncomingAssistantSettingsController.SaveResult.SAVED_MANUAL -> R.string.incoming_ui_saved_manual
                IncomingAssistantSettingsController.SaveResult.FAILED -> R.string.incoming_ui_save_failed
            })
        }.apply { tag = "incoming-save-mode" })
        content.addView(UiDesign.button(this, getString(R.string.incoming_ui_engines_link)) {
            startActivity(Intent(this, AudioEngineSettingsActivity::class.java))
        })
        content.addView(UiDesign.button(this, getString(R.string.incoming_ui_preparation_link)) {
            startActivity(Intent(this, ConversationPreparationActivity::class.java))
        })
        content.addView(UiDesign.button(this, getString(R.string.incoming_ui_skills_link)) {
            startActivity(Intent(this, ConversationSkillsActivity::class.java))
        })
        content.addView(UiDesign.button(this, getString(R.string.incoming_ui_tools_link)) {
            startActivity(Intent(this, ConversationToolsActivity::class.java))
        })
        feedback.setPadding(0, UiDesign.dp(this, 10), 0, 0)
        content.addView(feedback)
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
