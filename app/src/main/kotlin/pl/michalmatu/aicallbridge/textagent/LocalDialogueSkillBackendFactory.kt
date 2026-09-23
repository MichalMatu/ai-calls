package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider

/**
 * Reviewed composition for bounded dialogue-skill classification on phone-local models.
 *
 * Both Qwen/llama.cpp and Edge Gallery/Gemma receive the same skill-only system contract. The
 * selected model returns only skill id/confidence/reason; [DialogueSkillTextBackend] maps that onto
 * exact application-owned response text. This factory grants no dialing, disclosure, commitment,
 * completion or direct speech authority.
 */
internal object LocalDialogueSkillBackendFactory {
    fun create(
        context: Context,
        provider: TextLlmProvider,
        policy: DialogueSkillPolicy,
        observer: DialogueSkillDecisionObserver? = null,
    ): TextCallAgentBackend {
        val systemPrompt = DialogueSkillCatalog.systemPrompt(policy.allowedSkills)
        val classifier = when (provider) {
            TextLlmProvider.LOCAL_PHONE_LLM ->
                LocalPhoneLlmBackendFactory.create(context.applicationContext, systemPrompt)

            TextLlmProvider.EDGE_GALLERY ->
                EdgeGalleryTextBackend(
                    baseUrl = EdgeGalleryTextBackendFactory.BASE_URL,
                    expectedModelId = EdgeGalleryTextBackendFactory.MODEL,
                    systemPrompt = systemPrompt,
                )

            TextLlmProvider.OPENAI_TEXT,
            TextLlmProvider.LOCAL_MAC_LLM,
            -> throw IllegalArgumentException(
                "dialogue_skill_provider_not_phone_local_${provider.name.lowercase()}",
            )
        }
        return DialogueSkillTextBackend(
            classifierBackend = classifier,
            policy = policy,
            observer = observer,
        )
    }
}
