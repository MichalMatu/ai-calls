# Rozmowa i Test Lab

GSM: PCM → STT → Gemma/Skills → walidacja odpowiedzi → TTS/TX. STOP i barge-in odcinają stary TX. `save_note` zapisuje lokalnie, a `end_call` wymaga bieżącego właściciela i zakończonego TTS. TASK/CALENDAR/MESSAGE są propozycjami.

Test Lab ma dialog ręczny i adaptacyjny z edytowalnymi przypadkami. Używaj danych syntetycznych. Raport zawiera tury, wynik wykonania, opóźnienia i propozycje END_CALL. Jakość ocenia człowiek. Dobry przypadek do odbioru obejmuje cel, zmianę ustalenia, sprawdzenie pamięci i pożegnanie.

## Dalsza praca

- `realtime-client` i `CallRealtimeSessionOrchestrator` — wstrzymany Realtime Audio. Najpierw test sesji poza połączeniem.
- `AudioEngineProfile` i `LanguageModelCatalog` — Gemma Audio, Qwen i import LiteRT. Najpierw porównanie offline z profilem GSM na tym samym dialogu.
- `ConversationSkillRouting` — kontekstowe Skills. Najpierw A/B w Lab dla tej samej wersji przypadku i wypowiedzi.
