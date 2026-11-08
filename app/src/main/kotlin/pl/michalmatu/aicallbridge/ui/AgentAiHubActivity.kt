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
import pl.michalmatu.aicallbridge.identity.AndroidIdentityVault
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.identity.IdentityVaultManagerActivity
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
                tag = "agent-summary:" + key
                maxLines = 4
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
        destination("vault", R.string.ux07_agent_vault, IdentityVaultManagerActivity::class.java)
        destination("tools", R.string.ux07_agent_tools, ConversationToolsActivity::class.java)
        body.addView(UiDesign.caption(this, getString(R.string.ux09_agent_scope_warning)),
            UiDesign.spaceAfter(this, 12))
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
    private fun visibleSkillName(id: String, fallback: String): String = when (id) {
        "ConversationStyle" -> getString(R.string.skills_ux05_name_style)
        "MessageCapture" -> getString(R.string.skills_ux05_name_capture)
        "Clarification" -> getString(R.string.skills_ux05_name_clarify)
        "NaturalClosure" -> getString(R.string.skills_ux05_name_closure)
        else -> fallback
    }

    private fun visibleFieldName(field: IdentityFieldId): String = getString(when (field) {
        IdentityFieldId.PHONE -> R.string.prep_ui_phone
        IdentityFieldId.FIRST_NAME -> R.string.prep_ui_first_name
        IdentityFieldId.LAST_NAME -> R.string.prep_ui_last_name
        IdentityFieldId.EMAIL -> R.string.prep_ui_email
        IdentityFieldId.ADDRESS -> R.string.prep_ui_address
        IdentityFieldId.DATE_OF_BIRTH -> R.string.prep_ui_birth
        IdentityFieldId.PESEL -> R.string.ux09_agent_pesel
    })

    private fun namedScope(fields: Set<IdentityFieldId>): String =
        if (fields.isEmpty()) getString(R.string.ux09_agent_none)
        else fields.sortedBy { it.ordinal }.joinToString(" · ", transform = ::visibleFieldName)

    private fun refreshReadOnly() {
        val records = runCatching { AndroidConversationPreparationStore(this) }.getOrNull()
        summaries["skills"]?.text = records?.let { store ->
            runCatching {
                val library = store.loadSkillLibrary()
                val preset = library.active
                val title = when (preset.id) {
                    "natural" -> getString(R.string.skills_ux05_default_natural)
                    "formal" -> getString(R.string.skills_ux05_default_formal)
                    "message" -> getString(R.string.skills_ux05_default_message)
                    else -> preset.name
                }
                val activeNames = library.skills.filter { it.id in preset.versions.keys }
                    .sortedBy { it.section.ordinal }
                    .map { visibleSkillName(it.id, it.name) }
                getString(R.string.ux09_agent_named_skills, title, activeNames.size,
                    activeNames.joinToString(" · "))
            }.getOrNull()
        } ?: getString(R.string.ux07_agent_load_error)
        summaries["scenarios"]?.text = records?.let { store ->
            runCatching { getString(R.string.ux07_agent_scenario_count,
                ScenarioCatalog.templates.size, store.loadPersonalScenarios().items.size) }.getOrNull()
        } ?: getString(R.string.ux07_agent_load_error)
        summaries["profiles"]?.text = records?.let { store ->
            runCatching {
                val outgoing = store.load(ConversationPreparation.OUTGOING_ID)
                val incoming = store.load(ConversationPreparation.INCOMING_ID)
                getString(R.string.ux09_agent_named_profiles,
                    namedScope(outgoing.dataFields),
                    namedScope(incoming.dataFields))
            }.getOrNull()
        } ?: getString(R.string.ux07_agent_load_error)
        summaries["vault"]?.text = runCatching {
            val fieldIds = AndroidIdentityVault.create(this).availableFields().getOrThrow()
            if (fieldIds.isEmpty()) getString(R.string.ux09_agent_vault_empty)
            else getString(R.string.ux09_agent_vault_fields,
                fieldIds.size, namedScope(fieldIds))
        }.getOrElse { getString(R.string.ux07_agent_load_error) }
        summaries["tools"]?.text = runCatching {
            getString(R.string.ux07_agent_tools_count,
                ConversationToolsSettingsController(this).enabled().size)
        }.getOrElse { getString(R.string.ux07_agent_load_error) }
    }
}
