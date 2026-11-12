package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.AdapterView
import android.view.View
import android.widget.ScrollView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsActivity

/** Direct settings entries share the Agent menu's presentation. */
class SettingsHubActivity : AppBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = UiDesign.content(this)
        fun destination(title: Int, target: Class<out Activity>) {
            val line = UiDesign.navigationRow(this, getString(title)) {
                startActivity(Intent(this, target))
            }.apply {
                tag = "settings-destination:" + target.simpleName
            }
            content.addView(line, UiDesign.spaceAfter(this, UiDesign.GAP_CARD))
        }
        destination(R.string.mv_title, LanguageModelActivity::class.java)
        destination(R.string.incoming_ui_title, IncomingAssistantSettingsActivity::class.java)
        destination(R.string.mv_permissions, GeneralSettingsActivity::class.java)
        destination(R.string.ui_settings_hub_developer, DeveloperToolsActivity::class.java)
        // Interface language is independent of STT/TTS and belongs after functional settings.
        // One visible native dropdown; selecting a language takes only one tap to open.
        val languageCodes = listOf("auto", "pl", "en")
        val languageLabels = listOf(
            getString(R.string.ui_language_auto),
            getString(R.string.ui_language_polish),
            getString(R.string.ui_language_english),
        )
        val language = UiDesign.card(this).apply { tag = "settings-language-card" }
        language.addView(UiDesign.caption(this, getString(R.string.ui_language_label)),
            UiDesign.spaceAfter(this, UiDesign.GAP_SMALL))
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
        language.addView(languagePicker, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        content.addView(language, UiDesign.spaceAfter(this, 0).apply {
            topMargin = UiDesign.dp(this@SettingsHubActivity, UiDesign.GAP_SMALL)
        })
        AppNavigation.install(this, ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }, AppTab.SETTINGS)
    }

}
