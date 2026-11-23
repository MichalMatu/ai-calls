package pl.michalmatu.aicallbridge

import android.app.Application
import android.content.Context
import pl.michalmatu.aicallbridge.ui.AppLanguage

class AiCallsApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppLanguage.initialize(this)
    }
}
