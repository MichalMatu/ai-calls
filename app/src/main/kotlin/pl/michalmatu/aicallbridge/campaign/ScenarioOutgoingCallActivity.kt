package pl.michalmatu.aicallbridge.campaign

import android.content.Intent
import android.os.Bundle
import pl.michalmatu.aicallbridge.ui.AppBaseActivity

/** Compatibility destination for existing scenario links. The shared wizard owns approval. */
class ScenarioOutgoingCallActivity : AppBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, PhoneTaskWizardActivity::class.java).apply {
            for (key in listOf(EXTRA_SCENARIO_ID, EXTRA_SCENARIO_TITLE, EXTRA_SCENARIO_GOAL, EXTRA_SCENARIO_BRIEF)) {
                intent.getStringExtra(key)?.let { putExtra(key, it) }
            }
        })
        finish()
    }
    companion object {
        const val EXTRA_SCENARIO_ID = "scenario_outgoing_id"
        const val EXTRA_SCENARIO_TITLE = "scenario_outgoing_title"
        const val EXTRA_SCENARIO_GOAL = "scenario_outgoing_goal"
        const val EXTRA_SCENARIO_BRIEF = "scenario_outgoing_brief"
    }
}
