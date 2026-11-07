package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.MainActivityDeveloperProbes
import pl.michalmatu.aicallbridge.R

/** Existing diagnostic actions, relocated without changing their callbacks. */
class DeveloperToolsActivity : UiChromeActivity(AppTab.SETTINGS) {
    private lateinit var probes: MainActivityDeveloperProbes
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@DeveloperToolsActivity, 20),
                UiDesign.dp(this@DeveloperToolsActivity, 24),
                UiDesign.dp(this@DeveloperToolsActivity, 20),
                UiDesign.dp(this@DeveloperToolsActivity, 24))
        }
        body.addView(UiDesign.heading(this, getString(R.string.ui_settings_hub_developer)))
        body.addView(TextView(this).apply {
            text = getString(R.string.ui_developer_description)
            textSize = 14f
            setTextColor(UiDesign.secondary)
        })
        val status = TextView(this).apply { setTextColor(UiDesign.ink); textSize = 13f }
        probes = MainActivityDeveloperProbes(this, status)
        probes.addControls(body)
        body.addView(status)
        setContentView(ScrollView(this).apply { addView(body) })
        probes.attach()
    }
    override fun onDestroy() {
        if (::probes.isInitialized) probes.close()
        super.onDestroy()
    }
}
