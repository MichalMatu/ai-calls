package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId

/** Android-only model configuration. The Activity never creates or owns a backend. */
internal class AndroidSkillLabSessionFactory(
    private val context: Context,
) : SkillLabModelSessionFactory {
    @Volatile var selectedCase: LabCaseRevision? = null
    override fun open(presetId: String): SkillLabModelSession {
        val profile = AudioEngineProfileStore(context).load()
            ?: error(context.getString(R.string.lab_ui_missing_profile))
        val store = AndroidConversationPreparationStore(context)
        val library = store.loadSkillLibrary()
        val selected = selectedCase
        val initial = when (selected?.role ?: LabRole.GENERAL) {
            LabRole.INCOMING -> store.load(ConversationPreparation.INCOMING_ID)
            LabRole.OUTGOING -> store.load(ConversationPreparation.OUTGOING_ID)
            LabRole.GENERAL -> ConversationPreparation.supervised()
        }
        // Never include owner's vault references in an offline test prompt.
        val preparation = ConversationPreparation(
            "lab-offline", "Laboratorium", initial.direction,
            "", initial.skills, emptySet(), library.snapshot(presetId))
        val goal = selected?.goal ?: "Prowadź naturalny dialog testowy. Brak skutków zewnętrznych."
        val instruction = ConversationInstructionBuilder.build(
            preparation, goal, ConversationResponseFormat.TOOL_REPLY)
        val provider = DialogueEngineProviders.resolve(profile)
        return SkillLabModelSession {
            provider.createLaboratoryConversationReply(context, instruction, setOf(ConversationToolId.END_CALL))
        }
    }
}
