# Rozmowa

Wspólny `AndroidConversationRuntime`: STT → Gemma/Skills → kontrola tekstu/narzędzi → TTS. Callback wymaga session/turn/generation; STOP/barge-in odcina stary TX.

Tekstowy model przy pełnym KV odtwarza natywną sesję z ostatnimi zakończonymi parami USER/MODEL. Najstarsze pary opuszczają okno modelu; pełny prywatny zapis pozostaje. Instrukcja systemowa i bieżąca wypowiedź nie są skracane. Ponowienie dotyczy wyłącznie inferencji, wymaga aktualnej generacji i nie powtarza narzędzi ani połączenia. Nie ma limitu liczby tur rozmowy.

Odpowiedź strukturalna przechodzi walidację przed historią modelu. Błędny format odtwarza sesję z poprawną historią i ponawia wyłącznie inferencję; po nieudanej naprawie aplikacja prosi o powtórzenie, oznaczając wypowiedź SCRIPT_ASSISTED. STOP odcina obie ścieżki. Gemma E2B ma kontekst 4096; budżet 512 dotyczy całej odpowiedzi JSON, nie samej mowy.

SAVE_NOTE lokalne; TASK/CALENDAR/MESSAGE to propozycje. WAV na opt-in. Odbiór wspólnego backendu INCOMING/OUTGOING po poprawkach oraz barge-in/echo i jakość S22 PHYSICAL_OPEN. Pomiar bez połączenia: USB Lab z role=INCOMING; propozycje narzędzi nie są wykonywane.
