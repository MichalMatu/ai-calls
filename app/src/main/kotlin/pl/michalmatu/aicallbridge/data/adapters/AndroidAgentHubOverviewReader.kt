package pl.michalmatu.aicallbridge.data.adapters

import android.content.Context
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore
import pl.michalmatu.aicallbridge.conversation.ConversationPreparation
import pl.michalmatu.aicallbridge.conversationcore.ScenarioCatalog
import pl.michalmatu.aicallbridge.domain.models.AgentHubOverview
import pl.michalmatu.aicallbridge.domain.models.AgentHubSkillOverview
import pl.michalmatu.aicallbridge.identity.AndroidIdentityVault
import pl.michalmatu.aicallbridge.tools.ConversationToolsSettingsController

/** Android reads only. Call from a worker thread because vault/storage may block. */
internal class AndroidAgentHubOverviewReader(context: Context) {
    private val applicationContext = context.applicationContext

    fun read(): AgentHubOverview {
        // Keystore, legacy migration and the vault can perform disk/crypto work:
        // they must never run on the Activity's main/UI thread.
        val store = runCatching { AndroidConversationPreparationStore(applicationContext) }
        val skills = store.mapCatching { source ->
            source.loadSkillLibrary().let {
                AgentHubSkillOverview(it.receptionistToneVersion(), it.personalInstructions().isNotBlank())
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
        return AgentHubOverview(skills, scenarios, profiles, vault, tools)
    }
}
