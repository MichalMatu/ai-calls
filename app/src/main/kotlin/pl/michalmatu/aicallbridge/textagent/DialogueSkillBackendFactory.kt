package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider

/**
 * Provider-neutral construction for bounded dialogue-skill classification.
 *
 * Local models receive the same app-owned classifier contract. The returned backend may only
 * select a skill/confidence through [DialogueSkillTextBackend]; it does not gain direct speech or
 * call authority. Exact spoken text remains in [DialogueSkillPolicy].
 */
internal object DialogueSkillBackendFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(
        context: Context,
        provider: TextLlmProvider,
        policy: DialogueSkillPolicy,
        observer: DialogueSkillDecisionObserver? = null,
        edgeGalleryBearerToken: String? = null,
    ): TextCallAgentBackend {
        val prompt = DialogueSkillCatalog.systemPrompt(policy.allowedSkills)
        val classifier = when (provider) {
            TextLlmProvider.LOCAL_PHONE_LLM ->
                LocalPhoneLlmBackendFactory.create(context.applicationContext, prompt)

            TextLlmProvider.EDGE_GALLERY ->
                Gemma4LiteRtTextBackendFactory.createSkillClassifier(
                    context = context.applicationContext,
                    systemInstruction = prompt,
                    allowedSkills = policy.allowedSkills,
                )

            else -> throw IllegalArgumentException(
                "provider_not_enabled_for_dialogue_skills_${provider.name.lowercase()}",
            )
        }
        return DialogueSkillTextBackend(classifier, policy, observer)
    }
}
