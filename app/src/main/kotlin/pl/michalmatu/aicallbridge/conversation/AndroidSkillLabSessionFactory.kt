package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.NativeModelTiming
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId

/** Android-only model configuration. The Activity never creates or owns a backend. */
internal class AndroidSkillLabSessionFactory(
    private val context: Context,
    contextTokenBudget: Int = 4_096,
) : SkillLabModelSessionFactory {
    init { require(contextTokenBudget > 0) { "lab_invalid_context_budget" } }
    /** Applied to future sessions only; existing workers retain their own snapshot. */
    @Volatile var contextTokenBudget: Int = contextTokenBudget
        set(value) {
            require(value > 0) { "lab_invalid_context_budget" }
            field = value
        }
    @Volatile var onTiming: (NativeModelTiming) -> Unit = {}
    @Volatile var selectedCase: LabCaseRevision? = null
    companion object {
        private val activeLabBackend = AtomicBoolean(false)
    }
    override fun open(presetId: String): SkillLabModelSession {
        val budgetSnapshot = contextTokenBudget
        val timingSnapshot = onTiming
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
            // S22 may spend >2 GiB PSS on one native Gemma context. The USB
            // console and on-screen Lab share a process: never start two.
            check(activeLabBackend.compareAndSet(false, true)) {
                "another_lab_model_session_active"
            }
            try {
                val delegate = provider.createLaboratoryConversationReply(
                    context, instruction, setOf(ConversationToolId.END_CALL),
                    budgetSnapshot, timingSnapshot)
                object : TextCallAgentBackend {
                    private val closed = AtomicBoolean(false)
                    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
                        delegate.generate(userText, listener)
                    }
                    override fun cancel() { delegate.cancel() }
                    override fun close() {
                        if (closed.compareAndSet(false, true)) {
                            try { delegate.close() } finally { activeLabBackend.set(false) }
                        }
                    }
                }
            } catch (error: Throwable) {
                activeLabBackend.set(false)
                throw error
            }
        }
    }
}
