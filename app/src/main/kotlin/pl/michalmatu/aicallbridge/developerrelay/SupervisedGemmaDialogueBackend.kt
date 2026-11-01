package pl.michalmatu.aicallbridge.developerrelay

import android.content.Context
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider
import pl.michalmatu.aicallbridge.textagent.DialogueSkillBackendFactory
import pl.michalmatu.aicallbridge.textagent.DialogueSkillId
import pl.michalmatu.aicallbridge.textagent.DialogueSkillPolicy
import pl.michalmatu.aicallbridge.textagent.FailoverTextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Supervised, effect-free local dialogue. Gemma classifies an incoming utterance
 * into a bounded skill; only application-owned phrases are released to the call.
 * Raw generated model prose has no route to cellular TTS. A rejected selection
 * or failed model falls back once to the reviewed three-turn audio script.
 */
internal class SupervisedGemmaDialogueBackend(
    model: TextCallAgentBackend,
    onModelReply: () -> Unit = {},
    onFallbackReply: () -> Unit = {},
) : TextCallAgentBackend {
    constructor(
        context: Context,
        onModelReply: () -> Unit = {},
        onFallbackReply: () -> Unit = {},
    ) : this(
        model = DialogueSkillBackendFactory.create(
            context = context.applicationContext,
            provider = TextLlmProvider.LOCAL_GEMMA_4,
            policy = SupervisedGemmaSpeechPolicy.dialoguePolicy,
        ),
        onModelReply = onModelReply,
        onFallbackReply = onFallbackReply,
    )

    private val fallback = SupervisedReadOnlyDialogueBackend()
    private val delegate = FailoverTextCallAgentBackend(
        primary = object : TextCallAgentBackend {
            override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
                model.generate(userText, object : TextCallAgentBackend.Listener {
                    override fun onComplete(text: String) {
                        val approved = SupervisedGemmaSpeechPolicy.review(text)
                        if (approved == null) {
                            listener.onError("supervised_gemma_output_not_allowlisted")
                        } else {
                            onModelReply()
                            listener.onComplete(approved)
                        }
                    }

                    override fun onError(reason: String) = listener.onError(reason)
                })
            }

            override fun cancel() = model.cancel()
            override fun close() = model.close()
        },
        fallback = object : TextCallAgentBackend {
            override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
                fallback.generate(userText, object : TextCallAgentBackend.Listener {
                    override fun onComplete(text: String) {
                        onFallbackReply()
                        listener.onComplete(text)
                    }

                    override fun onError(reason: String) = listener.onError(reason)
                })
            }

            override fun cancel() = fallback.cancel()
            override fun close() = fallback.close()
        },
    )

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) =
        delegate.generate(userText, listener)

    override fun cancel() = delegate.cancel()
    override fun close() = delegate.close()
}

/**
 * Exact output allowlist: meaning cannot be widened by paraphrases, accents,
 * encodings, phone numbers, injection, or undiscovered commitment synonyms.
 * The model may select a skill, never compose the spoken text.
 */
internal object SupervisedGemmaSpeechPolicy {
    private val responses = mapOf(
        DialogueSkillId.ASK_REPEAT to
            "Przepraszam, proszę powtórzyć ostatnią wypowiedź.",
        DialogueSkillId.ASK_CLARIFY to
            "Czy może Pan lub Pani doprecyzować, o co chodzi?",
        DialogueSkillId.ACKNOWLEDGE_NEUTRAL to
            "Rozumiem, dziękuję za informację.",
        DialogueSkillId.CONFIRM_EXPECTED_SUBJECT to
            "To jest wyłącznie test dźwięku rozmowy, bez zmiany jakichkolwiek usług.",
    )

    val dialoguePolicy = DialogueSkillPolicy(
        allowedResponses = responses,
        minimumConfidence = 0.75,
    )

    private val approvedSpeech = responses.values.toSet()

    fun review(raw: String): String? =
        raw.trim().takeIf { it in approvedSpeech }
}
