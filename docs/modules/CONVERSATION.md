# Rozmowa

Jeden `AndroidConversationRuntime`: STT → LLM/Skills → kontrola narzędzi → TTS. Każdy callback musi należeć do sesji/tury/generacji. STOP/barge-in odcina stary TX.

Po zapełnieniu KV model odtwarza ostatnie zakończone pary USER/MODEL, nie skraca system promptu ani bieżącej wypowiedzi; pełna prywatna historia zostaje. Ponawia tylko inferencję, nigdy narzędzi/Call. Brak limitu tur.

JSON: reply/tool/note/kind (bez notatki: note puste, kind=NOTE). Walidacja przed historią. Błąd: naprawa inferencji z poprawną historią, potem prośba o powtórzenie jako SCRIPT_ASSISTED. STOP kończy obie ścieżki. Gemma E2B: 4096 kontekstu; 512 tokenów całego JSON.

SAVE_NOTE lokalnie; TASK/CALENDAR/MESSAGE to propozycje. USB Lab off-call bez skutków. WAV opt-in; Telecom/echo/barge-in: PHYSICAL_OPEN.
