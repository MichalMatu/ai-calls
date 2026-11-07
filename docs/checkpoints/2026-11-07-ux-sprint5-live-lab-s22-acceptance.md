# AI Calls — Sprint 5 UX, dwuturowy Skills Lab i odbiór S22

Data: **2026-11-07**. Produkcyjny kod na `main@0bd17da4c2de81996ae00a153ee6a1d3559d6a6f`; commity `[skip ci]`. GitHub Connector + Mac Local Agent. Brak Codex, Work, GitHub Actions, nowych gałęzi i rzeczywistych rozmów testowych.

## Zakres zmian

- Skills: rozdzielone akapity wprowadzenia PL/EN; wyłączona akcja aktywacji wybranego już presetu; przyciski pomocnicze w grupie.
- Start: rozróżniono tryb audio, dostawcę tekstowego LLM, model dialogowy i gotowość jego pliku.
- Kampanie: ostatni **zapisany** status wykonania jest widoczny bez otwierania diagnostyki; rozdzielone role START, STOP i Anuluj; skrócone etykiety odsłuchu i transkryptu.
- Silniki: Zapis konfiguracji przed opcjonalną diagnostyką, pobieraniem i odsłuchem; istniejące readiness gate zachowane.
- Modele: wtórny styl przycisków wyboru, metadane techniczne w osobnych polach.
- Historia: akcja usuwania przeniesiona do szczegółów rozmowy; istniejące potwierdzenie i polityka ochrony archiwum zachowane.
- Narzędzia AI: oddzielono nazwę możliwości od opisu.
- Developer: spójne PL etykiety sond urządzenia i Shizuku.
- Skills Lab: wybór szybkiego testu **2 tur** lub pełnego korpusu, bieżące karty wiadomości i odpowiedzi, oddzielne oczekiwana/rzeczywista decyzja END_CALL oraz czas, obsługa STOP i stanu błędu/timeoutu.

## Bramki i dowody

- `aicalls-20261107-ux-sprint5-final-source-qa-v3` — **DONE / exit 0** na `0bd17da`. **319 testów hosta PASS**, Kotlin AndroidTest compile, JVM test, APK i anti-friction PASS. 377/377 kluczy PL/EN bez braków lub duplikatów.
- `aicalls-20261107-ux-sprint5-s22-safe-install-39-screens-v3` — **DONE / exit 0**, dokładny S22 SM-S906B / RFCT70L7E8J. IDLE, niezmieniona rola dialera, certyfikat zgodny, app + test APK `adb install -r -t` **bez utraty danych**. **27/27 native UI, 15/15 podstawowych ekranów, 6/6 rozwiniętych stanów, 2/2 syntetycznych stanów Skills Lab PASS**. 39 prywatnych JPEG.
- `aicalls-20261107-ux-sprint5-s22-real-gemma-2turn-stop-v5` — **DONE / exit 0**. Rzeczywisty lokalny LiteRT model (wybrany w zapisanym profilu) wykonał **2 syntetyczne tury**; STOP działa; stan IDLE również po teście. **4 prywatne screenshoty** `lab-real-waiting`, `lab-real-first-turn`, `lab-real-two-turns`, `lab-real-stopped`.
- Łącznie **43 prywatne zdjęcia** w `~/Library/Application Support/AI-Calls/UI-audit/2026-11-07-ux-sprint5/AI-Calls-S22-UX-sprint5-private.zip`. Materiałów, numerów, transkryptów i audio nie publikowano w repo.

## Ważne rozróżnienia

- Syntetyczny zrzut `lab-timeout-synthetic.jpg` testuje **prezentację błędu**, nie prowokuje rzeczywistego 120-sekundowego timeoutu modelu.
- Rzeczywisty dwuturowy test offline wykazuje generowanie i STOP, lecz **nie** testuje telekomunikacji, pełnego 12-turowego korpusu, jakości merytorycznej i stabilności pod długim obciążeniem.
- Interfejs renderuje decyzje `END_CALL`, **nie wykonuje** ich w Laboratorium.
- Automatyczne przejście 39 ekranów oraz 4 dodatkowe zdjęcia nie zastępują jeszcze ręcznego przeglądu finalnego ZIP po przesłaniu przez właściciela.

## Dalej

Ręczny przegląd wizualny ZIP Sprint 5 i ewentualne mikro-poprawki na podstawie realnych 43 screenshotów. Osobno, poza tym sprintem: pełna kontrola jakości funkcjonalnej rozmów, pełne PL/EN wszystkich historycznych modułów oraz szeroki recorder/projektowe blokery — zgodnie z bieżącym `BLOCKERS.md`.
