# AI Calls — zamknięcie iteracji UX, Sprint 6, handoff (2026-11-07)

**Cel:** zamknąć iterację UX bez dalszego polerowania i przekazać jeden aktualny stan projektu. **Źródła funkcjonalne:** `main@1b359146d89260e7cffd6d4c851d4c475cee20ac` (Sprint 6 + trzy korekty po 42 screenshotach). Commity dokumentacyjne mogą podnieść `main` bez zmiany kodu; zawsze sprawdź najnowszy HEAD. **Status:** `UX_ITERATION_CLOSED / SOURCE_PASS / S22_PASS / REAL_GEMMA_PASS`. Nie równać `SOURCE_GREEN` z ostatnią fizyczną instalacją hotfixu.

## Dostarczone funkcje

1. **Historia:** przycisk „Usuń wszystkie zakończone rozmowy” na końcu listy, potwierdzenie i ponowny bezpieczny check COMPLETE/END/kanonicznej ścieżki. Nietykalne są aktywne/nieukończone sesje oraz obce katalogi. Nie ma „wyczyść wszystko” obejmującego modele lub ustawienia.
2. **Odtwarzanie:** dotknięcie dymku z istniejącym klipem RX/TX rozpoczyna WAV, drugie zatrzymuje; nie ma dużych przycisków odsłuchu pod dymkami. Niesparowane segmenty mają osobny zwijany obszar. Player nadal działa wyłącznie poza połączeniem.
3. **Laboratorium:** dwuturowy szybki test/full corpus, status tur i STOP, odróżnienie trafności decyzji END_CALL od semantycznej jakości odpowiedzi, ostrożne oznaczanie pytań do ręcznego przeglądu. Po przeglądzie screenshotów usunięto regex z nieobsługiwanymi na Androidzie flagami, zastępując go tokenizacją Unicode; skorygowano auto-follow do końca osi i test widoczności ostatniej karty/STOP.
4. **UI:** uproszczone Skills/Modele/Silniki, bez mnożenia przycisków, status ostatniego wykonania Kampanii oznaczony jako historyczny i „disconnect requested” jako żądanie, nie potwierdzony efekt.
5. **Granice:** żadnych zmian do Samsung/Shizuku PCM/PFD, toru RX/TX, Telecom, Call ownership, grantów, STOP, polityki zakończenia po zatwierdzonym pełnym TX lub ciągłości recorder.

## Dowody, wersje i rzeczywista instalacja

| Co | Identyfikator / kod | Wynik |
|---|---|---|
| Host Sprint 6 oryginalny | `aicalls-20261107-ux-sprint6-source-host-qa-v3`, `0dface1` | **DONE/0**, 325 Python, Kotlin JVM/AndroidTest/APK i anti-friction PASS |
| Fizyczna instalacja + UX Sprint 6 | `aicalls-20261107-ux-sprint6-s22-install-28ui-24visual-42screens-v3` | **DONE/0**: dokładny S22 `SM-S906B/RFCT70L7E8J`, ADB device, IDLE, rola dialera i signer zgodne; APP + TEST APK wgrane `adb install -r -t` bez utraty danych; **28/28 native UX + 24/24 visual PASS**, 42 prywatne JPEG; żadnych realnych rozmów, żadnego kasowania archiwów właściciela |
| Rzeczywisty Lab Sprint 6 **sprzed hotfixu** | `aicalls-20261107-ux-sprint6-s22-real-gemma-follow-stop-v2` | **FAILED**: `PatternSyntaxException` / `ExceptionInInitializerError` z nieobsługiwanych przez Android flag `(?iuU)` w `SkillLabScoring`; test modelu **nie wykazał** dwóch tur ani STOP w tej próbie; to błąd startu klasy/heurystyki, nie dowód błędu Gemmy |
| Przegląd 42 prywatnych zdjęć | `AI-Calls-S22-UX-sprint6-private.zip` dostarczony przez właściciela | **REVIEWED**: interfejs akceptowalny do zakończenia iteracji. Zrzut `history-bulk-confirmation-synthetic.jpg` nie pokazywał modalnego potwierdzenia; `lab-follow-stop-synthetic.jpg` nie pokazywał ostatniej tury/STOP w widocznym obszarze. To braki dowodów wizualnych, mimo PASS testów bazujących na `View.isShown` |
| Source hotfix po audycie wizualnym | `main@1b359146`, `aicalls-20261107-ux-sprint6-visual-review-hotfix-source-qa-v1` | **DONE/0**: 33 celowane Python, 325 Python host, JVM, AndroidTest compile, APK, anti-friction PASS |
| Pierwszy targeted S22 hotfix | `aicalls-20261107-ux-sprint6-visual-review-hotfix-s22-targeted-v1` | **FAILED preflight**: wadliwy guard porównywał zmodyfikowany kod do poprzedniej rewizji, choć nowy HEAD był poprawny; zakończenie przed ADB i instalacją |
| Poprawiony targeted S22 hotfix | `aicalls-20261107-ux-sprint6-visual-review-hotfix-s22-targeted-v2` | **DONE/0 / PASS**: exact S22 ADB/IDLE/rola/signature, APP + TEST APK zainstalowane bez utraty danych; **1/1** bezpieczne okno zbiorczego kasowania, **3/3** syntetyczne stany Laboratorium; 44 prywatne JPEG i ZIP. Zero realnych rozmów/kasowania właściciela |
| Końcowa rzeczywista Gemma po hotfixie | `aicalls-20261107-ux-sprint6-hotfix-real-gemma-final-v1` | **DONE/0 / PASS**: realny lokalny model 2 tury, widoczna ostatnia tura i STOP; ponowna kontrola bezpieczeństwa S22 PASS; +4 prywatne JPEG, **łącznie 46**. Nie wykonywano prawdziwych połączeń ani END_CALL |

**Granica stanu telefonu:** hotfix źródłowy **`1b359146` jest już zainstalowany** na fizycznym S22 z potwierdzonym zachowaniem danych. Rzeczywista lokalna Gemma wykonała po naprawie 2 tury + STOP, co potwierdza osobny DONE/0 receipt. Nie powtarzać 28+24 ukończonych testów bez nowej przesłanki; ewentualne sprawdzenie tylko dotkniętych obszarów.

**Prywatne dowody z S22** (nie umieszczać w GitHub): `~/Library/Application Support/AI-Calls/UI-audit/2026-11-07-ux-sprint6/AI-Calls-S22-UX-sprint6-private.zip`. Lokalna kopia pliku wysłana w czacie była oceniona. Po końcowym hotfixie powstał ZIP zawierający **46 screenshotów** (42 bazowe i 4 z realnej Gemmy; celowane screenshoty nadpisują wcześniej użyte nazwy). Ani WAV, ani prawdziwe transkrypty, numery i dane osobiste nie trafiają do publicznego logu/repo.

## Stan jakości i znane granice

- **PASS:** trwała historia i lokalne segmenty, podstawowe bezpieczne bulk delete, bubble play/stop, UI/menu na S22, host build i testy z najnowszego kodu.
- **PASS:** rzeczywisty dwuturowy model offline + STOP po hotfixie, także kontrola widoczności ostatniej tury; syntetyczne testy scoring/auto-follow/modal dialog na S22 PASS. **Nie pozostaje żadna otwarta bramka odbioru Sprintu 6.** Realny wynik Sprintu 5 jest historyczny i nie zastępuje tej próby.
- **Nieblokujące UX (odłożone zgodnie z decyzją właściciela):** dalsze szlifowanie proporcji, gęstości ustawień, paneli technicznych i pojedynczych zrzutów; nie otwierać kolejnego sprintu estetyki.
- **Inne niezależne produktowe OPEN:** `F-103` kompletne PL/EN, `F-080` ciągły zapis i odtwarzanie pełnej sesji (obecne dymki mają tylko segmenty), ręczne przejęcie/oddanie AI i osobne wymagające autoryzacji testy rzeczywistego call, pełna ocena semantyki/naturalności Gemmy. `END_CALL 2/2` nie mierzy tych cech.

## Definicja zamknięcia i kolejny projektowy krok

**UX Sprint 6 kończymy teraz jako iterację funkcjonalną.** Nie czekać z handoffem na kolejne ozdobne poprawki. Targeted S22 v2 jest **PASS**; udokumentowano dokładny SHA i zakres 1/1 + 3/3. Nie zmieniać historycznych dowodów. Ostatnia rzeczywista Gemma po naprawie jest **PASS**. Sprint 6 nie wymaga kolejnej automatycznej próby; nie odtwarzać pełnej matrycy bez nowego defektu.

**Następna iteracja (oddzielny chat, dopiero po decyzji): decoupling UX od logiki.** Rozpocząć od `UX-01 CampaignExecutionStatusPresenter` — read-only mapowanie historycznych statusów na typowany `ScreenState` bez `Context`, I/O lub prawa do efektów. Następnie oddzielne małe sprinty: `UX-02 ConversationHistoryController` (IO, kasowanie, stale callbacks), `UX-03 SkillLabRunner` (model/cancel/timeout), `UX-04` modele/ustawienia, `UX-05` F-103 lokalizacja. **Nie przepisywać całości i nie robić jednego wielkiego refaktoru.** Działające kontrolery ogólnych, incoming i tools settings pozostają.

**Najbliższy start nowego chatu:** sprawdzić świeży `main`, Local Agent `task-results`, zwłaszcza `aicalls-20261107-ux-sprint6-hotfix-real-gemma-final-v1` (**DONE/0 PASS**); nie powtarzać tego testu. Potem, wyłącznie w nowej iteracji, UX-01, bez powtarzania Sprint 5/Sprint 6 baseline, bez zmiany Telecom/STOP/Shizuku.

## Operacje repo i bezpieczeństwo

Tryb wyłącznie **GitHub Connector + Mac Local Agent**, `main`, commity `[skip ci]`; bez Codex/Work/GitHub Actions, nowych gałęzi, force-push, nieuprawnionych połączeń i czyszczenia realnych rozmów. Repo Mac `/Users/michal/agent-workspace/repos/ai-calls/work`, host `macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. Wymagane: dokładny HEAD, czysta baza, użycie istniejących store/providerów, `scripts/verify_host.sh` i audit product friction tylko dla nowego kodu. Zachować archiwalne checkpointy, czerwone testy i `BLOCKERS.md`; usunąć **powielone bieżące statusy**, nie unikalne dowody.

### Techniczna synchronizacja lokalnej kopii Mac — nieblokująca

Zaktualizowane dokumenty i checkpoint są zatwierdzone na **zdalnym `main`**. Osobne zadanie synchronizacji Mac (`aicalls-20261107-ux-sprint6-final-handoff-doc-sync-v1`) nie wystartowało: `task_budget_exhausted` / exit 125; skrócony wariant `...doc-sync-short-v2` odrzucony jako `invalid_task_file`. **Nie ma potwierdzenia finalnego HEAD/clean worktree lokalnego Maca po commitach dokumentacyjnych.** Nie wpływa to na zaliczony kod/QA/S22. Przy kolejnym wznowieniu wykonać zwykły `git fetch` + `git merge --ff-only origin/main` i `git status --porcelain` w istniejącym repo (bez testów i bez ingerencji w urządzenie). Nie tworzyć nowych prób w bieżącej iteracji.
