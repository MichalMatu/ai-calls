package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore
import pl.michalmatu.aicallbridge.conversation.ConversationPreparation
import pl.michalmatu.aicallbridge.conversation.ConversationPreparationActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillsActivity
import pl.michalmatu.aicallbridge.conversation.ScenarioLibraryActivity
import pl.michalmatu.aicallbridge.conversationcore.ScenarioCatalog
import pl.michalmatu.aicallbridge.identity.PhoneEnrollmentActivity
import pl.michalmatu.aicallbridge.tools.ConversationToolsActivity
import pl.michalmatu.aicallbridge.tools.ConversationToolsSettingsController

/** Single Agent entrypoint. No duplicated stores, data values, grants or call operations. */
class AgentAiHubActivity : AppBaseActivity() {
    private val summaries = mutableMapOf<String, TextView>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiDesign.dp(this@AgentAiHubActivity, UiDesign.CONTENT_INSET)
            setPadding(pad, pad, pad, pad)
        }
        body.addView(UiDesign.heading(this, getString(R.string.ux07_agent_title)).apply {
            textSize = 25f
        }, UiDesign.spaceAfter(this, 6))
        body.addView(UiDesign.caption(this, getString(R.string.ux07_agent_subtitle)),
            UiDesign.spaceAfter(this, 18))
        fun destination(key: String, title: Int, target: Class<out Activity>) {
            val card = UiDesign.card(this).apply {
                tag = "agent-destination:" + target.simpleName
                isClickable = true
                isFocusable = true
                contentDescription = getString(title)
                background = UiDesign.touchSurface(this@AgentAiHubActivity)
                setOnClickListener { startActivity(Intent(this@AgentAiHubActivity, target)) }
            }
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(UiDesign.heading(this, getString(title)).apply {
                textSize = 17f
            }, UiDesign.spaceAfter(this, 5))
            val summary = TextView(this).apply {
                textSize = 15f
                setTextColor(UiDesign.primary)
                maxLines = 2
            }
            summaries[key] = summary
            labels.addView(summary)
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = "›"
                textSize = 25f
                setTextColor(UiDesign.secondary)
            })
            card.addView(row)
            body.addView(card, UiDesign.spaceAfter(this, 16))
        }
        destination("skills", R.string.ux07_agent_skills, ConversationSkillsActivity::class.java)
        destination("scenarios", R.string.ux07_agent_scenarios, ScenarioLibraryActivity::class.java)
        destination("profiles", R.string.ux07_agent_preparation, ConversationPreparationActivity::class.java)
        destination("vault", R.string.ux07_agent_vault, PhoneEnrollmentActivity::class.java)
        destination("tools", R.string.ux07_agent_tools, ConversationToolsActivity::class.java)
        AppNavigation.install(this, ScrollView(this).apply {
            isFillViewport = true
            addView(body)
        }, AppTab.AGENT)
        refreshReadOnly()
    }
    override fun onResume() {
        super.onResume()
        if (summaries.isNotEmpty()) refreshReadOnly()
    }
    private fun refreshReadOnly() {
        val records = runCatching { AndroidConversationPreparationStore(this) }.getOrNull()
        summaries["skills"]?.text = records?.let { store ->
            runCatching {
                val library = store.loadSkillLibrary()
                getString(R.string.ux07_agent_active_preset, library.active.name, library.active.versions.size)
            }.getOrNull()
        } ?: getString(R.string.ux07_agent_load_error)
        summaries["scenarios"]?.text = records?.let { store ->
            runCatching { getString(R.string.ux07_agent_scenario_count,
                ScenarioCatalog.templates.size, store.loadPersonalScenarios().items.size) }.getOrNull()
        } ?: getString(R.string.ux07_agent_load_error)
        summaries["profiles"]?.text = records?.let { store ->
            runCatching {
                val profile = store.load(ConversationPreparation.OUTGOING_ID)
                getString(R.string.ux07_agent_profile_count, profile.dataFields.size, profile.skills.size)
            }.getOrNull()
        } ?: getString(R.string.ux07_agent_load_error)
        summaries["vault"]?.text = getString(R.string.ux07_agent_vault_status)
        summaries["tools"]?.text = runCatching {
            getString(R.string.ux07_agent_tools_count,
                ConversationToolsSettingsController(this).enabled().size)
        }.getOrElse { getString(R.string.ux07_agent_load_error) }
    }
}
