# Rozmowa

`AndroidConversationRuntime`: PCM → STT → Gemma/Skills → JSON `reply/tool/note/kind` → kontrola narzędzi → TTS/TX. Exact Call, turn, generacja i STOP odrzucają spóźnione callbacki; barge-in odcina stary TX.

`save_note`: szyfrowany lokalny zapis; `end_call`: po TTS przez bieżącego właściciela. `IncomingCallerFarewellPolicy` obsługuje jednoznaczne „do widzenia”, ale nie cytaty, pytania ani nowe ustalenia. Model nie wykonuje rezerwacji; `TASK/CALENDAR/MESSAGE` to propozycje.

KV odtwarza zakończone pary dialogu, bez naruszania bieżącej tury; po błędzie JSON ponawia tylko inferencję. LiteRT/Parakeet/Piper: test off-call ≠ PASS GSM. WAV wyłącznie opt-in.
