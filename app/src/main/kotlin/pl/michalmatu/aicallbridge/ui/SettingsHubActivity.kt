package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.AdapterView
import android.view.View
import android.widget.ScrollView
import android.widget.Toast
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.ModelVoiceSelectionPolicy
import pl.michalmatu.aicallbridge.conversation.ConversationPromptSettingsActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsActivity
import pl.michalmatu.aicallbridge.session.CallMediaBackendMode
import pl.michalmatu.aicallbridge.session.LocalAdbGsmSetupActivity

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
        destination(R.string.mv_permissions, GeneralSettingsActivity::class.java)
        destination(R.string.mv_title, LanguageModelActivity::class.java)
        // One shared GSM audio transport serves both incoming and outgoing calls.
        if (CallMediaBackendMode.forPackage(packageName) == CallMediaBackendMode.GSM_SHELL_ADB) {
            destination(R.string.local_adb_open, LocalAdbGsmSetupActivity::class.java)
        }
        destination(R.string.incoming_ui_title, IncomingAssistantSettingsActivity::class.java)
        destination(R.string.prompt_settings_title, ConversationPromptSettingsActivity::class.java)
        if (CallMediaBackendMode.forPackage(packageName) != CallMediaBackendMode.GSM_SHELL_ADB) {
            destination(R.string.ui_settings_hub_developer, DeveloperToolsActivity::class.java)
        } else if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            destination(R.string.ui_lab_short, ConversationSkillLabActivity::class.java)
        }
        // The saved speech profile is captured when a new conversation starts.
        val languageCodes = listOf("en", "pl")
        val languageLabels = listOf(
            getString(R.string.ui_language_english),
            getString(R.string.ui_language_polish),
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
                val current = AppLanguage.selected(this@SettingsHubActivity)
                if (requested == current) return
                if (saveConversationLanguage(requested)) {
                    AppLanguage.select(this@SettingsHubActivity, requested)
                } else {
                    Toast.makeText(this@SettingsHubActivity, R.string.eng_ui_save_error,
                        Toast.LENGTH_SHORT).show()
                    languagePicker.setSelection(languageCodes.indexOf(current))
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

    private fun saveConversationLanguage(language: String): Boolean {
        val store = AudioEngineProfileStore(this)
        return runCatching {
            val previous = store.load() ?: return false
            val tag = if (language == "pl") "pl-PL" else "en-US"
            previous.languageTag == tag || store.save(ModelVoiceSelectionPolicy.forLanguage(previous, tag))
        }.getOrDefault(false)
    }
}
