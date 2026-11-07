package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import pl.michalmatu.aicallbridge.conversation.ConversationPreparationActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillsActivity
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity
import pl.michalmatu.aicallbridge.identity.PhoneEnrollmentActivity
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsActivity
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity
import pl.michalmatu.aicallbridge.tools.ConversationToolsActivity

/** Direct navigation hub: categories are labels, not one-button accordions. */
class SettingsHubActivity : AppBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@SettingsHubActivity, 20),
                UiDesign.dp(this@SettingsHubActivity, 24),
                UiDesign.dp(this@SettingsHubActivity, 20),
                UiDesign.dp(this@SettingsHubActivity, 28))
        }
        content.addView(UiDesign.heading(this, getString(R.string.ui_settings_hub_title)).apply {
            textSize = 27f
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.ui_settings_hub_caption)
            textSize = 14f
            setTextColor(UiDesign.secondary)
            setPadding(0, UiDesign.dp(this@SettingsHubActivity, 8),
                0, UiDesign.dp(this@SettingsHubActivity, 16))
        })
        fun section(title: Int) {
            content.addView(UiDesign.heading(this, getString(title)).apply {
                textSize = 17f
                setPadding(0, UiDesign.dp(this@SettingsHubActivity, 18),
                    0, UiDesign.dp(this@SettingsHubActivity, 10))
            })
        }
        fun destination(title: Int, description: Int, target: Class<out Activity>) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = UiDesign.dp(this@SettingsHubActivity, 78)
                isClickable = true
                isFocusable = true
                tag = "settings-destination:" + target.simpleName
                contentDescription = getString(title)
                background = UiDesign.surfaceDrawable(this@SettingsHubActivity)
                setPadding(UiDesign.dp(this@SettingsHubActivity, 16),
                    UiDesign.dp(this@SettingsHubActivity, 12),
                    UiDesign.dp(this@SettingsHubActivity, 16),
                    UiDesign.dp(this@SettingsHubActivity, 12))
                setOnClickListener { startActivity(Intent(this@SettingsHubActivity, target)) }
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this).apply {
                text = getString(title)
                textSize = 16f
                setTextColor(UiDesign.ink)
            })
            labels.addView(TextView(this).apply {
                text = getString(description)
                textSize = 12f
                setTextColor(UiDesign.secondary)
                setPadding(0, UiDesign.dp(this@SettingsHubActivity, 4), 0, 0)
            })
            line.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(TextView(this).apply {
                text = "›"
                textSize = 24f
                setTextColor(UiDesign.secondary)
            })
            content.addView(line, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiDesign.dp(this@SettingsHubActivity, 9) })
        }
        section(R.string.ui_language_title)
        content.addView(TextView(this).apply {
            text = getString(R.string.ui_language_hint)
            textSize = 13f
            setTextColor(UiDesign.secondary)
            setPadding(0, 0, 0, UiDesign.dp(this@SettingsHubActivity, 10))
        })
        content.addView(RadioGroup(this).apply {
            tag = "ui-language-group"
            val current = AppLanguage.selected(this@SettingsHubActivity)
            listOf(
                "auto" to R.string.ui_language_auto,
                "pl" to R.string.ui_language_polish,
                "en" to R.string.ui_language_english,
            ).forEach { (code, label) ->
                addView(RadioButton(this@SettingsHubActivity).apply {
                    tag = "ui-language:" + code
                    text = getString(label)
                    isChecked = current == code
                    minimumHeight = UiDesign.dp(this@SettingsHubActivity, 48)
                    setOnClickListener { AppLanguage.select(this@SettingsHubActivity, code) }
                })
            }
        })
        section(R.string.ui_settings_hub_group_ai)
        destination(R.string.ui_settings_hub_general, R.string.ui_settings_hub_general_detail, GeneralSettingsActivity::class.java)
        destination(R.string.ui_settings_hub_engines, R.string.ui_settings_hub_engines_detail, AudioEngineSettingsActivity::class.java)
        destination(R.string.ui_settings_hub_models, R.string.ui_settings_hub_models_detail, LanguageModelActivity::class.java)
        section(R.string.ui_settings_hub_group_calls)
        destination(R.string.ui_settings_hub_answering, R.string.ui_settings_hub_answering_detail, IncomingAssistantSettingsActivity::class.java)
        destination(R.string.ui_settings_hub_preparation, R.string.ui_settings_hub_preparation_detail, ConversationPreparationActivity::class.java)
        destination(R.string.ui_settings_hub_skills, R.string.ui_settings_hub_skills_detail, ConversationSkillsActivity::class.java)
        destination(R.string.ui_settings_hub_identity, R.string.ui_settings_hub_identity_detail, PhoneEnrollmentActivity::class.java)
        section(R.string.ui_settings_hub_group_advanced)
        destination(R.string.ui_settings_hub_developer, R.string.ui_settings_hub_developer_detail, DeveloperToolsActivity::class.java)
        destination(R.string.ui_settings_hub_tools, R.string.ui_settings_hub_tools_detail, ConversationToolsActivity::class.java)
        destination(R.string.ui_settings_hub_usb, R.string.ui_settings_hub_usb_detail, UsbDebugPanelActivity::class.java)
        AppNavigation.install(this, ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }, AppTab.SETTINGS)
    }
}
