# Rozmowa

Wspólny `AndroidConversationRuntime`: STT → Gemma/Skills → kontrola tekstu/narzędzi → TTS. Callback wymaga session/turn/generation; STOP/barge-in odcina stary TX.

Tekstowy model przy pełnym KV odtwarza natywną sesję z ostatnimi zakończonymi parami USER/MODEL. Najstarsze pary opuszczają okno modelu; pełny prywatny zapis pozostaje. Instrukcja systemowa i bieżąca wypowiedź nie są skracane. Ponowienie dotyczy wyłącznie inferencji, wymaga aktualnej generacji i nie powtarza narzędzi ani połączenia. Nie ma limitu liczby tur rozmowy.

SAVE_NOTE lokalne; TASK/CALENDAR/MESSAGE to propozycje. WAV na opt-in. Dłuższy INCOMING po poprawce KV, barge-in/echo i jakość S22 PHYSICAL_OPEN.
