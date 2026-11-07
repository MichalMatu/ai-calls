package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.AdapterView
import android.widget.ImageView
import android.view.View
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
        val languageCodes = listOf("auto", "pl", "en")
        val languageLabels = listOf(
            getString(R.string.ui_language_auto),
            getString(R.string.ui_language_polish),
            getString(R.string.ui_language_english),
        )
        val selectedLanguage = AppLanguage.selected(this)
        val languageDetails = LinearLayout(this).apply {
            tag = "ui-language-group"
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, UiDesign.dp(this@SettingsHubActivity, 8),
                0, UiDesign.dp(this@SettingsHubActivity, 4))
        }
        val languagePicker = Spinner(this).apply {
            tag = "ui-language-picker"
            UiSelect.setOptions(this, languageLabels)
            setSelection(languageCodes.indexOf(selectedLanguage).coerceAtLeast(0))
        }
        languageDetails.addView(languagePicker)
        languageDetails.addView(TextView(this).apply {
            text = getString(R.string.ui_language_hint)
            textSize = 12f
            setTextColor(UiDesign.secondary)
            setPadding(0, UiDesign.dp(this@SettingsHubActivity, 8),
                0, UiDesign.dp(this@SettingsHubActivity, 8))
        })
        languagePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val requested = languageCodes[position]
                if (requested != AppLanguage.selected(this@SettingsHubActivity)) {
                    AppLanguage.select(this@SettingsHubActivity, requested)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val languageHeading = LinearLayout(this).apply {
            tag = "ui-language-toggle"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumHeight = UiDesign.dp(this@SettingsHubActivity, 54)
            background = UiDesign.touchSurface(this@SettingsHubActivity)
            setPadding(UiDesign.dp(this@SettingsHubActivity, 14), 0,
                UiDesign.dp(this@SettingsHubActivity, 14), 0)
        }
        languageHeading.addView(TextView(this).apply {
            text = getString(R.string.ui_language_title)
            textSize = 16f
            setTextColor(UiDesign.ink)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        languageHeading.addView(TextView(this).apply {
            tag = "ui-language-current"
            text = languageLabels[languageCodes.indexOf(selectedLanguage).coerceAtLeast(0)]
            textSize = 12f
            setTextColor(UiDesign.secondary)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = UiDesign.dp(this@SettingsHubActivity, 8) })
        val languageChevron = ImageView(this).apply {
            setImageResource(R.drawable.ic_aicalls_chevron_down)
            setColorFilter(UiDesign.secondary)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        languageHeading.addView(languageChevron, LinearLayout.LayoutParams(
            UiDesign.dp(this, 20), UiDesign.dp(this, 20)))
        languageHeading.contentDescription = getString(R.string.ui_language_title)
        languageHeading.setOnClickListener {
            val expanded = languageDetails.visibility != View.VISIBLE
            languageDetails.visibility = if (expanded) View.VISIBLE else View.GONE
            languageChevron.setImageResource(
                if (expanded) R.drawable.ic_aicalls_chevron_up else R.drawable.ic_aicalls_chevron_down)
        }
        content.addView(languageHeading)
        content.addView(languageDetails)
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
