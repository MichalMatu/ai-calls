# AI Calls — Sprint 6: checkpoint kodu, QA, S22 i granic UX

**Data:** 2026-11-07. **Funkcjonalny HEAD:** `0dface1e86e6bf9bab65090c3af293ff12f9dc7c` (`main`). Ten dokument i późniejsze commity dokumentacyjne nie zmieniają tego HEAD funkcjonalnego. Przed wznowieniem odczytaj aktualny `main`, nie zakładaj SHA z checkpointu.

## 1. Zakres wykonany i zamrożony w źródłach

- **Rozmowy:** przycisk `history-delete-all` u dołu listy, widoczny dla zakończonych, możliwych do usunięcia archiwów; potwierdzenie nieodwracalności. `RecordedConversationHistory.deleteAllCompleted()` ponownie sprawdza znacznik COMPLETE i zdarzenie END, własny katalog kanoniczny i symlinki. Nie usuwa `root`, obcych plików, sesji aktywnych ani nieukończonych. Zwraca liczby deleted/skipped/failed. Indywidualne usuwanie pozostaje w szczegółach.
- **Odsłuch:** nagrane WAV RX/TX uruchamia tapnięcie w dymek, ponowne tapnięcie zatrzymuje; przyciski pod każdym dymkiem usunięte. Pozostałe, niesparowane fragmenty nadal są dostępne w zwijanej sekcji. `ArchivedConversationPlayer` pilnuje tylko jednego aktywnego pliku, uprawnienia i stanu poza połączeniem; nie steruje Telecom.
- **Skills/Audio:** mniej dominująca aktywacja już wybranego presetu, skrócone wtórne etykiety, feedback gotowości przed Zapisem.
- **Laboratorium:** poprawa przewijania po reflow do ostatniej tury, widoczny `lab-stopped-banner`, rozdzielone END_CALL od oceny treści. Heurystyczna liczba pytań jest listą do przeglądu, nie automatycznym dowodem złej odpowiedzi; dodatkowa obsługa polskich zwrotów prośby o dane bez `?`, Unicode regex. Rzeczywisty test dwuturowy uzupełniono o asercję widoczności ostatniej karty i STOP.
- **Kampania:** historyczny kod `targeted_disconnect_requested` przedstawiony przyjaźnie jako **żądanie**, nie potwierdzony disconnect; diagnostyka techniczna pozostaje dostępna.

Nie zmieniano fizycznego Samsung/Shizuku RX/TX, PCM/PFD, zatwierdzenia, własności Call, praw telefonicznych ani polityki STOP/grantu. Żadne realne dane rozmów nie zostały skasowane przez testy.

## 2. Dowody wykonania — rozróżnij 3 stany

| Bramka / task Mac Local Agent | Wynik | Granica dowodu |
|---|---|---|
| `aicalls-20261107-ux-sprint6-source-host-qa-v3` | **DONE / exit 0 / PASS** | 33 celowane Python; `verify_host.sh` 325 PASS; AndroidTest Kotlin compile, Android JVM i APK build PASS; anti-friction PASS; exact source HEAD `0dface1` |
| `aicalls-20261107-ux-sprint6-source-host-qa-v2` | DONE / exit 0 / PASS | Wcześniejsze źródła `8ebfa7d`; nie jest zamiennikiem v3 |
| `aicalls-20261107-ux-sprint6-s22-install-28ui-24visual-42screens-v1` | **FAILED preflight / exit 1 / 0.45 s** | Brak outputu; nie stwierdzono instalacji ani uruchomienia testów |
| `aicalls-20261107-ux-sprint6-s22-install-28ui-24visual-42screens-v2` | **FAILED preflight / exit 1 / 2.15 s** | Mac ma poprawne `main@0dface1`, ale `adb devices -l` zwróciło pustą listę, `s22_adb_discovery_failed=exact_s22_adb_transport_unavailable`; bez instalacji, bez testów UX |
| `aicalls-20261107-ux-sprint6-s22-real-gemma-follow-stop-v1` | **FAILED prerequisite / exit 1 / 602.7 s** | Oczekiwanie na nieistniejący pakiet zdjęć Sprint 6 wyczerpane przed uruchomieniem ADB i modelu; **nie** jest to wynik inferencji Gemmy |

**Status końcowy:** `SOURCE_GREEN` / `S22_QA_PENDING_ADB` / `REAL_GEMMA_SPRINT6_NOT_RUN`. Nie ma zatwierdzonego zestawu 28/28 native UX, 24/24 wizualnych, 42 JPEG ani 46 JPEG po realnej Gemmie dla Sprintu 6. Nie prezentować celu jako otrzymanego wyniku.

**Ostatni zaakceptowany fizyczny baseline:** Sprint 5, `0bd17da`: 27/27 native UI, 23/23 wizualnych, 43 prywatne JPEG i realna dwuturowa Gemma+STOP PASS. [Checkpoint Sprint 5](2026-11-07-ux-sprint5-live-lab-s22-acceptance.md); te wyniki nie dowodzą nowych zmian Sprintu 6.

## 3. Bezpieczna procedura zamknięcia QA_PENDING

1. Zweryfikować fizyczne podłączenie i autoryzację ADB dokładnego `SM-S906B` / `RFCT70L7E8J`; sprawdzić `adb devices -l` na Macu (stan `device`, a nie `unauthorized`).
2. Po odzyskaniu transportu uruchomić tylko niezakończoną bramkę S22 na exact `main`; bez czyszczenia danych, bez prawdziwych rozmów, z kontrolą IDLE/roli/signera/grantu/retry. Oczekiwany zakres: **28 native UX + 24 wizualne + min. 42 prywatne JPEG**, w tym syntetyczne dymki i potwierdzenie bulk-delete (test tylko **anuluje**, nigdy nie potwierdza usuwania realnego archiwum).
3. Dopiero po ZIP wykonać osobny realny, syntetyczny test modelu **2 tury + widoczność ostatniej karty + STOP**; oczekiwane 4 dodatkowe prywatne JPEG, łącznie min. 46. Przekazać ZIP przez właściciela do ręcznej oceny.
4. Po rzeczywistych receipts podnieść QA_PENDING do PASS albo naprawić konkretny defekt. Nie ponawiać `325` testów hosta bez zmiany źródeł.

## 4. Nadal otwarte i nieprzeniesione do Sprintu 6

- `F-103`: niekompletna lokalizacja PL/EN starszych ekranów i brak pełnej separacji prezentacji od efektów.
- `F-080`: brak ciągłego nagrania całego Call i odtwarzacza całej rozmowy; dymki obsługują jedynie istniejące fragmenty WAV.
- Ocena semantyki Gemmy, naturalności dialogu i rzeczywista telekomunikacja to inne bramki — licznik decyzji END_CALL nie zastępuje ich.

## 5. Kolejny kierunek: UX bez własności logiki

Obecnie `ConversationHistoryActivity.kt` (~19 KB), `ConversationSkillLabActivity.kt` (~21 KB), `CampaignToolActivity.kt` (~25 KB), `AudioEngineSettingsActivity.kt` (~19 KB) równocześnie renderują UI i organizują operacje. Działające separacje już istnieją: `GeneralSettingsController`, `IncomingAssistantSettingsController`, `ConversationToolsSettingsController`; nie tworzyć ich dubli.

**Zalecana kolejność, każdy krok osobno:** 
A. Pure/read-only `CampaignExecutionStatusPresenter` (kod historyczny → jawny tekst i status bez uprawnienia do zmiany zadania), testy PL/EN i zgodności z raw diagnostic.
B. `ConversationHistoryController` / `HistoryScreenState` (list/load/count/delete completed, typed result, kolejka IO i race/lifecycle), Player nadal Android off-call, nie zmieniać fizycznego recorder.
C. `SkillLabRunner` / `SkillLabUiState` (model/cancel/timeout/obserwacje), Activity tylko rysuje; istniejące `SkillLabScoring` pozostaje czystą logiką. Testy spóźnionych callbacków, STOP i snapshotu.
D. Pozostałe Settings/Models i pełne i18n F-103, dopiero po odpowiednich bramkach.

Jedna odpowiedzialność, test RED→GREEN i clean `main` na każdy krok. Zakaz rozpoczynania całego refaktoru jednocześnie. [Bezpieczna modularizacja](../SAFE_MODULARIZATION_PLAN.md), [Architektura](../ARCHITECTURE.md), [BLOCKERS](../../BLOCKERS.md).

## 6. Porządek dokumentacji

Handoff i roadmapa są dokumentami bieżącymi; stare append-only przeglądy nie są już „następnymi krokami”. Dowody historyczne pozostają w `docs/checkpoints/` i historii Git; `BLOCKERS.md` to rejestr i nie został skasowany. Nie usuwać prywatnych dowodów, starych RED ani kodu bez analizy użyć.
