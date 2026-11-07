package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.incoming.IncomingSecretaryScenario

/** Android-only model configuration. The Activity never creates or owns a backend. */
internal class AndroidSkillLabSessionFactory(
    private val context: Context,
) : SkillLabModelSessionFactory {
    override fun open(presetId: String): SkillLabModelSession {
        val profile = AudioEngineProfileStore(context).load()
            ?: error(context.getString(R.string.lab_ui_missing_profile))
        val store = AndroidConversationPreparationStore(context)
        val library = store.loadSkillLibrary()
        val preparation = store.load(ConversationPreparation.INCOMING_ID)
            .withSkillSnapshot(library.snapshot(presetId))
        val instruction = ConversationInstructionBuilder.build(
            preparation, IncomingSecretaryScenario.GOAL, ConversationResponseFormat.TOOL_REPLY)
        val provider = DialogueEngineProviders.resolve(profile)
        return SkillLabModelSession {
            provider.createConversationReply(context, instruction, setOf(ConversationToolId.END_CALL))
        }
    }
}
