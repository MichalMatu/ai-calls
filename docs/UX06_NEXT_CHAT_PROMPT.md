Wznów projekt **AI Calls — UX-06**, w trybie **przeglądu i poprawek kierowanych przeze mnie**.

Repozytorium: `MichalMatu/ai-calls`, gałąź `main`. Używaj GitHub Connector oraz Mac Local Agent do operacji hosta/S22. Bez Codex, ChatGPT Work, GitHub Actions (również ich statusów), nowej gałęzi ani force-push. Commity `[skip ci]`.

**Najpierw preflight, bez modyfikacji:** pobierz świeży `main` HEAD; przeczytaj `AGENTS.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/checkpoints/2026-11-07-ux06-complete-checkpoint-user-directed-next-chat.md`, `docs/checkpoints/2026-11-07-ux06-source-s22-synthetic-acceptance.md`, `docs/UX06_USABILITY_AND_SCENARIO_PLAN.md`, `docs/ROADMAP.md`, `BLOCKERS.md`, `docs/LOCAL_AGENT_HOST_FLOW_2026-11-01.md`. Sprawdź dokładne receipts Mac, stan Local Agenta i ewentualne zmiany kodu po checkpointcie. Krótko podsumuj stan i **poczekaj, aż wskażę pierwszy ekran, element lub zachowanie**. Nie realizuj samodzielnie zaległości z roadmapy.

Gdy wskażę miejsce:
1. Ustal dokładną ścieżkę w interfejsie i odpowiadające jej źródła, realną logikę, przechowywanie stanu oraz skutki przycisków. Sprawdź powiązane ekrany, nazewnictwo PL/EN, lifecycle, błędy, brak uprawnień, powroty, STOP, race conditions i typowe pitfalls.
2. Jeśli napiszę **„sprawdź”**, wykonaj szczegółowy reaudyt i przedstaw konkretny plan, **bez zmian w kodzie**. Jeśli napiszę **„popraw”, „wdrażaj” lub „kontynuuj poprawkę”** odnosząc się do wskazanego punktu, napraw go, uruchom adekwatne testy i opublikuj wynik.
3. Wprowadzaj zmiany minimalnym spójnym zakresem, bez duplikowania wykonawców ani przebudowy stabilnych Skills/danych/STT/TTS/Shizuku bez uzasadnionej zależności. Nie uruchamiaj samowolnie rzeczywistych połączeń ani działań zewnętrznych. Nie myl statusów historycznych, procesu i rzeczywistego Call/rezultatu zadania.
4. Po każdej zmianie: sprawdź HEAD, wynik testów Mac i — tylko gdy uzasadnione i bezpieczne — syntetyczny odbiór S22. Odnotuj nowe błędy w `BLOCKERS.md`, zaktualizuj dokumentację i checkpoint. Odróżniaj `SOURCE_PASS`, `S22_SYNTHETIC_PASS` oraz nieprzeprowadzone testy rzeczywistych rozmów. Nie powtarzaj niezmienionych zaliczonych bramek bez potrzeby.

**Stan bazowy:** kod `25a41a0` HOST_PASS (341 testów Python, JVM/lint/APK), S22 syntetycznie 10/10 PASS, zainstalowany bez wipe. Nowe presety Mechanik/Przychodnia i pozostałe poza CLIR to formularze DRAFT_ONLY, nie działająca telefonia. Otwarte F-122, F-123 (częściowo), F-124, F-080 i pełna integracja scenariuszy.

Po preflight **nie wybieraj za mnie następnego zadania**. Ja wskażę, co teraz sprawdzić lub poprawić.
