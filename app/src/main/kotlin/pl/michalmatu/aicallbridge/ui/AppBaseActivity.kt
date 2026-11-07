package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.content.Context

/** Shared language-aware Activity base; has no phone, grant or model dependencies. */
abstract class AppBaseActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }
}
