package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Language of the UI only; STT, TTS and agent language stay independent. */
internal object AppLanguage {
    private const val DEFAULT_LANGUAGE = "en"
    private const val PREFS = "ui_language_preferences"
    private const val KEY = "selected_ui_locale"

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            if (manager.applicationLocales.isEmpty) {
                manager.applicationLocales = LocaleList.forLanguageTags(legacyLanguage(context))
            }
        }
    }

    fun selected(context: Context): String {
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales?.toLanguageTags()?.substringBefore(',')
                ?.takeIf(String::isNotEmpty) ?: legacyLanguage(context)
        } else {
            legacyLanguage(context)
        }
        return if (Locale.forLanguageTag(tag).language == "pl") "pl" else DEFAULT_LANGUAGE
    }

    private fun legacyLanguage(context: Context): String =
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) == "pl")
            "pl" else DEFAULT_LANGUAGE

    fun select(activity: Activity, code: String) {
        require(code == "en" || code == "pl")
        if (selected(activity) == code) return
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(code)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, if (code == "auto") "" else code).apply()
            activity.recreate()
        }
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = selected(base)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(configuration)
    }
}
