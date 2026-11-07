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
    private const val PREFS = "ui_language_preferences"
    private const val KEY = "selected_ui_locale"

    fun selected(context: Context): String {
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales?.toLanguageTags()?.substringBefore(',').orEmpty()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "").orEmpty()
        }
        return if (tag.startsWith("pl")) "pl" else if (tag.startsWith("en")) "en" else "auto"
    }

    fun select(activity: Activity, code: String) {
        require(code in setOf("auto", "pl", "en"))
        if (selected(activity) == code) return
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(if (code == "auto") "" else code)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, if (code == "auto") "" else code).apply()
            activity.recreate()
        }
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = selected(base)
        if (tag == "auto") return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(configuration)
    }
}
