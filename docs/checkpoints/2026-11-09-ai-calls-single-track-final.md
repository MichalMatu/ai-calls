# AI Calls — single-track final: połączenie dwóch czatów w jeden tor (2026-11-09)

## Bieżący stan

Punkt źródłowy przed scaleniem: main@31f016d5468cccdf4418ea306c3584d0805aa178. **Nowe commity wyłącznie dokumentacyjne:** 5bd7473 (single-track handoff i lista modułów), aee8795 (F-169 label), cecc4b2 (status/etykiety licencji). Ten checkpoint i finalny status F-169 dopisano później bez zmiany produkcyjnego kodu. W nowym czacie sprawdzić świeży main, nie wnioskować, że historyczny SHA nadal jest HEAD.

Właściciel zdecydował zakończyć oba równoległe czaty i pracować w jednym nowym czacie. Repo nie zarządza zamykaniem okien ChatGPT; decyzja organizacyjna jest zapisana w NEXT_CHAT_PROMPT i HANDOFF_SINGLE_TRACK_2026-11-09. Tylko jeden master plan: AI_CALLS_MASTER_EXECUTION_PLAN.md. Audyt zewnętrznych źródeł jest dodatkiem z konkretnymi plikami i zasadami licencji, a nie dodatkową roadmapą.

## Co zamknięto w tym zadaniu

- Zachowano obecny Android runtime/Telecom, Samsung/Shizuku PCM, Gemma 4 E2B, CONTEXTUAL Skills, STT, TTS, STOP, bramki autoryzacji, ręczne przejęcie, prywatne archiwum, wszystkie dokumenty i checkpointy. **Żadnej zmiany kodu produkcyjnego, żadnego instalowania S22 ani połączeń.**
- Jedna sekwencja: G6.1 INCOMING (wykryta luka, minimalny Kotlin port Pipecat/call-use) → G6.2 OUTGOING (IVR/outcome) → sprawdzalne rezerwacje → historia/UX → opcjonalnie SIP/VoIP/WhatsApp. Bez drugich runnerów i równoległych sprintów.
- Piny zewnętrznych projektów i aktualne licencje ocenione: Pipecat BSD-2-Clause, call-use MIT, LiveKit Apache-2.0; DeskRoute AGPL-3.0 (nie kopiować bez osobnej decyzji licencyjnej), Clinic Voice Agent bez widocznego LICENSE (tylko wzorce, nie kod). Żaden zewnętrzny kod nie został jeszcze skopiowany do AI Calls; adaptację do Kotlin i notice planować dopiero dla konkretnej brakującej funkcji.
- Ochroniono wyniki bez ponawiania: G3 96/96, G4 dynamic Skills, G5.3 18/18 i 36 WAV, native STOP, 386 Python, 6/6 prywatnych stron oraz historyczne A8→S22 INCOMING.
- Ocena właściciela rozmowy pozytywna, ale 0/6 prywatnych ocen per session i 48 pól nadal UNASSESSED — nie wymyślać recenzji. G6 live E2E pozostaje NOT_CLOSED.

## QA i historia błędów testu dokumentacji

Mac aicalls-single-track-handoff-doc-qa-mac-20261109-v1 FAILED: po poprawnym ff na main@5bd7473 asercja formatu wymagała literalnego SINGLE. v2 FAILED: po ff na main@aee8795 test wymagał literalnego UNASSESSED. Obie wpadki zapisano jako **F-169** — dokumentacyjny false RED, nie wada aplikacji.

**Mac v3 aicalls-single-track-handoff-doc-qa-mac-20261109-v3 DONE/0**: aktualizacja do main@cecc4b2, spójność dokumentów, źródeł/license/statusów i linków, git show --check PASS, audit_product_friction PASS, Git clean (3/3 komend, wszystkie exit 0). Nie uruchamiano Gradle/Android/ADB/G3/G5 kolejny raz, bo nie było zmian produkcyjnych. F-169 VERIFIED_SCOPED. Mac po ostatnim commicie checkpoint będzie doc-only behind; nowy czat ma wykonać zwykły ff z kontrolą clean i świeżego HEAD.

## Dokładnie pierwsza praca nowego czatu

Otworzyć docs/NEXT_CHAT_PROMPT.md, docs/HANDOFF_SINGLE_TRACK_2026-11-09.md, AGENTS.md, master plan i BLOCKERS, sprawdzić HEAD/recents + Local Agent task-results (również czy drugi czat nie zdążył nic dopisać). Zrobić jeden read-only gap check G6.1 z Pipecat SpeculationGate/UserTurnController, call-use event/outcome models kontra istniejące AndroidConversationRuntime, CallRealtimeAudioPump, LocalSpeechGenerationGate, AndroidIncomingAssistant. Wdrożyć **tylko brakującą** funkcję i testy, nie zamieniać frameworków i nie robić pełnego QA dwa razy. Realne Call wymagają konkretnego autoryzowanego celu, SIM i zakresu.
