package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore
import pl.michalmatu.aicallbridge.conversation.ConversationPreparation
import pl.michalmatu.aicallbridge.conversation.ConversationPreparationActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillsActivity
import pl.michalmatu.aicallbridge.conversation.ScenarioLibraryActivity
import pl.michalmatu.aicallbridge.conversationcore.ScenarioCatalog
import pl.michalmatu.aicallbridge.identity.AndroidIdentityVault
import pl.michalmatu.aicallbridge.identity.IdentityVaultManagerActivity
import pl.michalmatu.aicallbridge.tools.ConversationToolsActivity
import pl.michalmatu.aicallbridge.tools.ConversationToolsSettingsController

/** One Agent hub. Read-only summaries never grant access or mutate the call runtime. */
class AgentAiHubActivity : AppBaseActivity() {
    private data class SkillSummary(val toneVersion: Int, val customInstructions: Boolean)
    private data class Snapshot(
        val skills: Result<SkillSummary>,
        val scenarios: Result<Pair<Int, Int>>,
        val profiles: Result<Pair<Int, Int>>,
        val vault: Result<Int>,
        val tools: Result<Int>,
    )

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val summaries = mutableMapOf<String, TextView>()
    private val destinations = mutableMapOf<String, LinearLayout>()
    private val titles = mutableMapOf<String, String>()
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiDesign.dp(this@AgentAiHubActivity, UiDesign.CONTENT_INSET)
            setPadding(pad, UiDesign.dp(this@AgentAiHubActivity, 12),
                pad, pad)
        }
        fun destination(key: String, title: Int, target: Class<out Activity>) {
            val titleText = getString(title)
            val card = UiDesign.card(this).apply {
                tag = "agent-destination:" + target.simpleName
                isClickable = true
                isFocusable = true
                background = UiDesign.touchSurface(this@AgentAiHubActivity)
                setOnClickListener { startActivity(Intent(this@AgentAiHubActivity, target)) }
            }
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(UiDesign.heading(this, titleText).apply {
                textSize = 17f
            }, UiDesign.spaceAfter(this, 5))
            val summary = UiDesign.caption(this, getString(R.string.ux07_agent_loading)).apply {
                textSize = 15f
                setTextColor(UiDesign.primary)
                tag = "agent-summary:" + key
                maxLines = 3
            }
            summaries[key] = summary
            destinations[key] = card
            titles[key] = titleText
            card.contentDescription = titleText + ". " + summary.text
            labels.addView(summary)
            row.addView(labels, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = "›"
                textSize = 25f
                setTextColor(UiDesign.secondary)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            card.addView(row)
            body.addView(card, UiDesign.spaceAfter(this, 12))
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

    private fun readSnapshot(): Snapshot {
        // Keystore, legacy migration and the vault can perform disk/crypto work:
        // they must never run on the Activity's main/UI thread.
        val store = runCatching { AndroidConversationPreparationStore(applicationContext) }
        val skills = store.mapCatching { source ->
            source.loadSkillLibrary().let {
                SkillSummary(it.receptionistToneVersion(), it.personalInstructions().isNotBlank())
            }
        }
        val scenarios = store.mapCatching { source ->
            ScenarioCatalog.templates.size to source.loadPersonalScenarios().items.size
        }
        val profiles = store.mapCatching { source ->
            source.load(ConversationPreparation.OUTGOING_ID).dataFields.size to
                source.load(ConversationPreparation.INCOMING_ID).dataFields.size
        }
        val vault = runCatching {
            AndroidIdentityVault.create(applicationContext).availableFields().getOrThrow().size
        }
        val tools = runCatching {
            ConversationToolsSettingsController(applicationContext).enabled().size
        }
        return Snapshot(skills, scenarios, profiles, vault, tools)
    }

    private fun refreshReadOnly() {
        val token = ++generation
        worker.execute {
            val snapshot = readSnapshot()
            main.post {
                if (token != generation || isFinishing || isDestroyed) return@post
                val error = getString(R.string.ux07_agent_load_error)
                val skillSummary = snapshot.skills.getOrNull()?.let { value ->
                    val tone = getString(when (value.toneVersion) {
                        1 -> R.string.skills_tone_natural
                        2 -> R.string.skills_tone_formal
                        else -> R.string.skills_hub_legacy_style
                    })
                    getString(R.string.skills_hub_summary, tone, getString(
                        if (value.customInstructions) R.string.skills_hub_custom_on
                        else R.string.skills_hub_custom_off))
                } ?: error
                val rendered = mapOf(
                    "skills" to skillSummary,
                    "scenarios" to (snapshot.scenarios.getOrNull()?.let {
                        getString(R.string.ux07_agent_scenario_count, it.first, it.second)
                    } ?: error),
                    "profiles" to (snapshot.profiles.getOrNull()?.let {
                        getString(R.string.ux07_agent_profile_count, it.first, it.second)
                    } ?: error),
                    "vault" to (snapshot.vault.getOrNull()?.let {
                        if (it == 0) getString(R.string.ux09_agent_vault_empty)
                        else getString(R.string.ux07_agent_vault_count, it)
                    } ?: error),
                    "tools" to (snapshot.tools.getOrNull()?.let {
                        getString(R.string.ux07_agent_tools_count, it)
                    } ?: error),
                )
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
