# Rozmowa i Test Lab

GSM: PCM → STT → Gemma/Skills → walidacja odpowiedzi → TTS/TX. STOP i barge-in odcinają stary TX. `save_note` zapisuje lokalnie, a `end_call` wymaga bieżącego właściciela i zakończonego TTS. TASK/CALENDAR/MESSAGE są propozycjami.

W Test Lab → Zarządzaj testami edytuj rolę, cel, instrukcje AI, otwarcie i reguły. Fakty i oczekiwania służą do oceny. USB `.s8lab` obsługuje `scripts/bin/aicalls` z `lab-start`, `lab-send`, `lab-status`, `lab-stop` i `lab-save`. Tekst trafia przez stdin, a opcja `lab-start --instructions-stdin` dodaje instrukcję tylko dla tej sesji. Używaj danych syntetycznych. Jakość odpowiedzi oceń na podstawie dialogu.

## Dalsza praca

- `realtime-client` i `CallRealtimeSessionOrchestrator` — wstrzymany Realtime Audio. Najpierw test sesji poza połączeniem.
- `AudioEngineProfile` i `LanguageModelCatalog` — Gemma Audio, Qwen i import LiteRT. Najpierw porównanie offline z profilem GSM na tym samym dialogu.
- `ConversationSkillRouting` — kontekstowe Skills. Najpierw A/B w Lab dla tej samej wersji przypadku i wypowiedzi.
