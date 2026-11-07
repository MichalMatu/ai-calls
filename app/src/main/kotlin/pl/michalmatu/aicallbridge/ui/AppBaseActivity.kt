package pl.michalmatu.aicallbridge.ui

import androidx.activity.ComponentActivity
import android.content.Context

/** Shared language-aware Activity base; has no phone, grant or model dependencies. */
abstract class AppBaseActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }
}
