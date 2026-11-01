package pl.michalmatu.aicallbridge.campaign

import pl.michalmatu.aicallbridge.localcall.PhraseMatrix
import pl.michalmatu.aicallbridge.localcall.PhraseMatrixRule
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Exact reviewed low-risk IVR turns run before Gemma. This layer never issues permits,
 * discloses secrets, interprets carrier success, or mutates workflow state.
 * All output still passes the application's normal approval gate.
 */
internal class ClirDeterministicDialogueBackend(
    private val fallback: TextCallAgentBackend,
    private val onMatchedRule: (String) -> Unit = {},
) : TextCallAgentBackend {
    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        val match = MATRIX.match(userText)
        val reply = match?.let { RESPONSES[it.ruleId] }
        if (match != null && reply != null) {
            onMatchedRule(match.ruleId)
            listener.onComplete(reply)
        } else {
            fallback.generate(userText, listener)
        }
    }

    override fun cancel() = fallback.cancel()
    override fun close() = fallback.close()

    private companion object {
        val RESPONSES = mapOf(
            "CLIR_REASON" to "Dzwonię w sprawie włączenia usługi CLIR, czyli blokady prezentacji mojego numeru.",
            "CLIR_REPEAT_REASON" to "Oczywiście. Chodzi o włączenie blokady prezentacji mojego numeru, usługi CLIR.",
            "CLIR_GREETING" to "Dzień dobry. Dzwonię w sprawie usługi CLIR.",
        )
        val MATRIX = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "CLIR_REASON",
                    phrases = setOf(
                        "W jakiej sprawie pan dzwoni?",
                        "W jakiej sprawie pani dzwoni?",
                        "W jakiej sprawie dzwonią?",
                        "Czego dotyczy połączenie?",
                    ),
                    aliases = setOf(
                        "Jak mogę pomóc?",
                        "W czym mogę pomóc?",
                        "Co mogę dla pana zrobić?",
                        "Co mogę dla pani zrobić?",
                    ),
                ),
                PhraseMatrixRule(
                    ruleId = "CLIR_REPEAT_REASON",
                    phrases = setOf(
                        "Proszę powtórzyć",
                        "Czy może pan powtórzyć?",
                        "Czy może pani powtórzyć?",
                        "Nie dosłyszałem",
                        "Nie dosłyszałam",
                    ),
                ),
                PhraseMatrixRule(
                    ruleId = "CLIR_GREETING",
                    phrases = setOf("Dzień dobry", "Witam"),
                ),
            ),
        )
    }
}
