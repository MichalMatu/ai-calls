package pl.michalmatu.aicallbridge.developerrelay

import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Deliberately narrow first-call audio pilot. There is no model, account mutation,
 * personal-data disclosure or instruction to the other party to change a service.
 * Only a user-controlled existing call can be attached to this dialogue backend.
 */
internal class SupervisedReadOnlyDialogueBackend : TextCallAgentBackend {
    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        val normalized = userText.trim().lowercase()
        val reply = when {
            normalized.contains("powtórz") || normalized.contains("powtorz") ->
                "To jest wyłącznie test jakości dźwięku. Nie zmieniam żadnych ustawień."
            normalized.contains("słychać") || normalized.contains("slychac") ->
                "Tak, testuję odbiór i nadawanie dźwięku. Nie wykonuję żadnych operacji."
            else ->
                "Słyszę wypowiedź. To test połączenia audio bez zmian na koncie."
        }
        listener.onComplete(reply)
    }

    override fun cancel() = Unit
}
