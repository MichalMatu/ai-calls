package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.data.adapters.AndroidAgentHubOverviewReader
import pl.michalmatu.aicallbridge.presentation.agent.AgentHubSummaryFormatter
import pl.michalmatu.aicallbridge.presentation.agent.AgentSummaryTextKey
import pl.michalmatu.aicallbridge.conversation.ConversationPreparationActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillsActivity
import pl.michalmatu.aicallbridge.conversation.ScenarioLibraryActivity
import pl.michalmatu.aicallbridge.identity.IdentityVaultManagerActivity
import pl.michalmatu.aicallbridge.tools.ConversationToolsActivity

/** One Agent hub. Read-only summaries never grant access or mutate the call runtime. */
class AgentAiHubActivity : AppBaseActivity() {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val summaries = mutableMapOf<String, TextView>()
    private val destinations = mutableMapOf<String, LinearLayout>()
    private val titles = mutableMapOf<String, String>()
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = UiDesign.content(this)
        fun destination(key: String, title: Int, target: Class<out Activity>) {
            val titleText = getString(title)
            val summary = UiDesign.caption(this, getString(R.string.ux07_agent_loading)).apply {
                tag = "agent-summary:" + key
            }
            val card = UiDesign.navigationRow(this, titleText, summary) {
                startActivity(Intent(this, target))
            }.apply {
                tag = "agent-destination:" + target.simpleName
            }
            summaries[key] = summary
            destinations[key] = card
            titles[key] = titleText
            body.addView(card, UiDesign.spaceAfter(this, UiDesign.GAP_CARD))
        }
        destination("skills", R.string.ux07_agent_skills, ConversationSkillsActivity::class.java)
        destination("scenarios", R.string.ux07_agent_scenarios, ScenarioLibraryActivity::class.java)
        destination("profiles", R.string.ux07_agent_preparation, ConversationPreparationActivity::class.java)
        destination("vault", R.string.ux07_agent_vault, IdentityVaultManagerActivity::class.java)
        destination("tools", R.string.ux07_agent_tools, ConversationToolsActivity::class.java)
        AppNavigation.install(this, ScrollView(this).apply {
            isFillViewport = true
            addView(body)
        }, AppTab.AGENT)
    }

    override fun onResume() {
        super.onResume()
        refreshReadOnly()
    }

    private fun refreshReadOnly() {
        val token = ++generation
        worker.execute {
            val snapshot = AndroidAgentHubOverviewReader(applicationContext).read()
            main.post {
                if (token != generation || isFinishing || isDestroyed) return@post
                val rendered = AgentHubSummaryFormatter { key, args ->
                    getString(when (key) {
                        AgentSummaryTextKey.LOAD_ERROR -> R.string.ux07_agent_load_error
                        AgentSummaryTextKey.TONE_NATURAL -> R.string.skills_tone_natural
                        AgentSummaryTextKey.TONE_FORMAL -> R.string.skills_tone_formal
                        AgentSummaryTextKey.TONE_LEGACY -> R.string.skills_hub_legacy_style
                        AgentSummaryTextKey.SKILLS_SUMMARY -> R.string.skills_hub_summary
                        AgentSummaryTextKey.CUSTOM_ON -> R.string.skills_hub_custom_on
                        AgentSummaryTextKey.CUSTOM_OFF -> R.string.skills_hub_custom_off
                        AgentSummaryTextKey.SCENARIO_COUNT -> R.string.ux07_agent_scenario_count
                        AgentSummaryTextKey.PROFILE_COUNT -> R.string.ux07_agent_profile_count
                        AgentSummaryTextKey.VAULT_EMPTY -> R.string.ux09_agent_vault_empty
                        AgentSummaryTextKey.VAULT_COUNT -> R.string.ux07_agent_vault_count
                        AgentSummaryTextKey.TOOLS_COUNT -> R.string.ux07_agent_tools_count
                    }, *args)
                }.render(snapshot)
                rendered.forEach { (key, value) ->
                    summaries[key]?.text = value
                    destinations[key]?.contentDescription = titles[key] + ". " + value
                }
            }
        }
    }

    override fun onDestroy() {
        generation++
        worker.shutdown()
        super.onDestroy()
    }
}
