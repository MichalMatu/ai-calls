package pl.michalmatu.aicallbridge.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import pl.michalmatu.aicallbridge.MainActivityDeveloperProbes
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity

/** Existing diagnostic actions, relocated without changing their callbacks. */
class DeveloperToolsActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.ui_settings_hub_developer)
    private lateinit var probes: MainActivityDeveloperProbes
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = UiDesign.content(this)
        probes = MainActivityDeveloperProbes(this)
        probes.addControls(body)
        body.addView(UiDesign.button(this, getString(R.string.ui_lab_short)) {
            startActivity(Intent(this, ConversationSkillLabActivity::class.java))
        })
        body.addView(UiDesign.button(this, getString(R.string.s8_voip_lab_title)) {
            startActivity(Intent(this, S8VoipLabActivity::class.java))
        }.apply { tag = "developer-s8-voip-lab" })
        body.addView(UiDesign.button(this, getString(R.string.s8_sip_lab_title)) {
            startActivity(Intent(this, S8SipAccountLabActivity::class.java))
        }.apply { tag = "developer-s8-sip-lab" })
        setContentView(ScrollView(this).apply { addView(body) })
        probes.attach()
    }
    override fun onDestroy() {
        if (::probes.isInitialized) probes.close()
        super.onDestroy()
    }
}
