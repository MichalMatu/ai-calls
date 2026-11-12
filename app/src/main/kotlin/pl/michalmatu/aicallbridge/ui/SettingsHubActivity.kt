package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.AdapterView
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsActivity

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
            ).apply { bottomMargin = UiDesign.dp(this@SettingsHubActivity, 16) })
        }
        // One visible native dropdown; selecting a language takes only one tap to open.
        val languageCodes = listOf("auto", "pl", "en")
        val languageLabels = listOf(
            getString(R.string.ui_language_auto),
            getString(R.string.ui_language_polish),
            getString(R.string.ui_language_english),
        )
        content.addView(TextView(this).apply {
            text = getString(R.string.ui_language_label)
            textSize = 16f
            setTextColor(UiDesign.ink)
            setPadding(0, UiDesign.dp(this@SettingsHubActivity, 8),
                0, UiDesign.dp(this@SettingsHubActivity, 8))
        })
        val languagePicker = Spinner(this).apply {
            tag = "ui-language-picker"
            contentDescription = getString(R.string.ui_language_label)
            UiSelect.setOptions(this, languageLabels)
            setSelection(languageCodes.indexOf(AppLanguage.selected(this@SettingsHubActivity))
                .coerceAtLeast(0))
        }
        languagePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val requested = languageCodes[position]
                if (requested != AppLanguage.selected(this@SettingsHubActivity)) {
                    AppLanguage.select(this@SettingsHubActivity, requested)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        content.addView(languagePicker, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, UiDesign.dp(this, 52)
        ).apply { bottomMargin = UiDesign.dp(this@SettingsHubActivity, 8) })
        section(R.string.ui_settings_hub_group_ai)
        destination(R.string.mv_title, R.string.mv_intro, LanguageModelActivity::class.java)
        section(R.string.ui_settings_hub_group_calls)
        destination(R.string.mv_permissions, R.string.mv_permissions_hint, GeneralSettingsActivity::class.java)
        destination(R.string.ui_settings_hub_answering, R.string.ui_settings_hub_answering_detail, IncomingAssistantSettingsActivity::class.java)
        section(R.string.ui_settings_hub_group_advanced)
        destination(R.string.ui_settings_hub_developer, R.string.ui_settings_hub_developer_detail, DeveloperToolsActivity::class.java)
        destination(R.string.ui_settings_hub_usb, R.string.ui_settings_hub_usb_detail, UsbDebugPanelActivity::class.java)
        AppNavigation.install(this, ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }, AppTab.SETTINGS)
    }

}
