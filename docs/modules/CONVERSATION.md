# Rozmowa i Test Lab

GSM: PCM → STT → Gemma/Skills → walidacja odpowiedzi → TTS/TX. STOP i barge-in odcinają stary TX. `save_note` zapisuje lokalnie, a `end_call` wymaga bieżącego właściciela i zakończonego TTS. TASK/CALENDAR/MESSAGE są propozycjami.

W `.s8lab` wybierz Ustawienia → Laboratorium offline → Zarządzaj testami. Fakty i oczekiwania służą do oceny dialogu. Przez USB użyj `scripts/bin/aicalls` z `lab-start`, `lab-send`, `lab-status`, `lab-stop` i `lab-save`. Tekst syntetyczny podawaj przez stdin. `lab-start --instructions-stdin` dodaje instrukcję AI dla jednej sesji.

## Dalsza praca

- `realtime-client` i `CallRealtimeSessionOrchestrator` — wstrzymany Realtime Audio. Najpierw test sesji poza połączeniem.
- `AudioEngineProfile` i `LanguageModelCatalog` — Gemma Audio, Qwen i import LiteRT. Najpierw porównanie offline z profilem GSM na tym samym dialogu.
- `ConversationSkillRouting` — kontekstowe Skills. Najpierw A/B w Lab dla tej samej wersji przypadku i wypowiedzi.
