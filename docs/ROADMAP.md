# AI Calls — roadmapa (po zamknięciu Sprintu 6, 2026-11-07)

**Stan:** `UX_ITERATION_CLOSED`. Najnowszy kod UX `main@1b359146`, **325 testów hosta PASS**, S22 **28/28 native + 24/24 visual**, hotfix targeted **1/1 + 3/3 PASS**, realna Gemma **2 tury + STOP PASS**, finalny ZIP **46 screenshotów**. [Dokładne receipts](checkpoints/2026-11-07-ux-sprint6-final-handoff.md). Nie wracać do mikro-poprawek UX bez nowego zgłoszenia lub regresji.

## P0 — zamknięte, nie ponawiać

| Zakres | Stan |
|---|---|
| Sprint 6 źródła/325 testów i Android APK | **DONE / PASS** |
| Pełny odbiór S22/UI (28+24) | **DONE / PASS**, data preserved |
| Hotfix Android scoring, STOP/scroll, dialog | **DONE / PASS**, 4/4 celowane na S22 |
| Realna lokalna Gemma 2 tury i STOP | **DONE / PASS**, 46 prywatnych screenshotów |
| Ręczny przegląd bazowych 42 screenshotów | **DONE**, decyzja właściciela: UX wystarczająco dobry |

## P1 — odsprzęgnąć UX od efektów (osobne, małe zmiany)

1. **UX-01: DONE / SOURCE_PASS.** Read-only `CampaignExecutionStatusPresenter` (typowane stany i PL/EN), `CampaignToolActivity` tylko podaje historyczny snapshot i renderuje; raw diagnostics pozostają w accordion. JVM 6 PASS, 325 Python PASS, AndroidTest compile/APK/friction PASS. Fizyczny S22 **NOT_RUN** dla nowego SHA. [Checkpoint](checkpoints/2026-11-07-ux01-campaign-status-presenter-closeout.md). Nie powtarzać.
2. **UX-02: DONE / SOURCE_PASS.** `ConversationHistoryController` (typowany `HistoryScreenState` i wyniki kasowania), jeden serializowany executor, lista/detail z generacją odrzucającą przestarzałe callbacki oraz ukończone-only single/bulk delete po potwierdzeniu. `RecordedConversationHistory` pozostaje wyłącznym właścicielem plików, `ArchivedConversationPlayer` Android off-call. **7 JVM, 325 Python, APK/AndroidTest compile i friction PASS**, Mac clean. **S22 nowego kodu NOT_RUN.** [Checkpoint](checkpoints/2026-11-07-ux02-conversation-history-controller-closeout.md). Nie powtarzać.
3. **UX-03: DONE / SOURCE_PASS.** `SkillLabRunner` przejął worker/model, 120 s timeout, STOP, late callbacks i score; `SkillLabUiState` immutable, Activity tylko renderuje. `END_CALL` jest wyłącznie obserwacją, nie semantyczną oceną ani efektem Telecom. **9 JVM, 328 Python, friction, host, AndroidTest compile i oba APK PASS**, Mac clean na `b53cb820`; **S22 nowego kodu NOT_RUN**. [Checkpoint i receipts](checkpoints/2026-11-07-ux03-skill-lab-runner-closeout.md). Nie powtarzać.
4. **UX-04: pozostałe ustawienia.** Stopniowo przenieść odczyt/zapis/probe z `AudioEngineSettingsActivity` i wybory Modeli do dedykowanych istniejących serwisów. Nie tworzyć drugiego magazynu danych ani kolejnego download service.
5. **UX-05: F-103 pełne PL/EN.** Przejść po aktualnych ekranach, usunąć widoczne hardcoded PL w Kotlinie, test parytetu kluczy i Android locale screenshots. Po rozdzieleniu model/widok jest to prostsze.

**Nie robić wszystkiego w jednym sprincie.** Uzasadnienie i architektura: [SAFE_MODULARIZATION_PLAN](SAFE_MODULARIZATION_PLAN.md) oraz [ARCHITECTURE](ARCHITECTURE.md).

## P2 — funkcjonalność produktu i niezależne bramki

- **F-080:** rzeczywisty *ciągły* recorder i pełny player sesji z osią czasu. Obecne dymki odtwarzają tylko istniejące WAV RX/TX z tur, nie nagranie całego Call.
- **F-091 i rozmowy:** osobna realna kontrola end_call / naturalnego zakończenia / przejęcia / powrotu AI / STOP / timeoutu, **wyłącznie** z ważną autoryzacją i kontrolą S22.
- **Jakość Gemmy:** oddzielna walidacja semantyki, poprawności pytań, kompletności wiadomości i naturalności; licznik `END_CALL 2/2` nie jest jakością odpowiedzi.
- **F-103:** kompletna lokalizacja i dostępność na fizycznym telefonie (w zakresie UI).
- Pozostałe nieskończone obszary: [jedyny rejestr BLOCKERS](../BLOCKERS.md), [plan produktu](PRODUCT_ASSEMBLY_PLAN.md); nie wznawiać zamkniętych historycznych testów bez regresji.

## Zamknięte — nie rozpoczynać od nowa

| Obszar | Wynik referencyjny |
|---|---|
| Modularizacja etapy 0–1 | PASS: zależności, IncomingIdleTimeout → JVM `conversation-core` |
| Etap 2 | NO_CHANGE / PASS: typowane wyniki mają uzasadnioną własność w app |
| Etap 3 krok 1 | PASS: `ConversationSessionRegistry` + testy w `conversation-core` |
| Sprint 5 | S22 27/27 native, 23/23 wizualne, 43 JPG, realna Gemma 2 tury + STOP PASS |
| Sprint 6 | **CLOSED / PASS**: 325 host, 28 native, 24 visual, 4 targeted, real Gemma 2 tury + STOP, 46 prywatnych JPG |

Historyczne szczegóły są zachowane w [checkpointach](checkpoints/), a nie rozwijane jako kolejne instrukcje w tej roadmapie.

## Bramka definicji ukończenia

Dla każdego nowego kroku: świeży `main`, clean worktree, zadanie Local Agenta bez kolizji, **jedna odpowiedzialność**, test regresyjny RED→GREEN, `scripts/verify_host.sh`, friction audit i odpowiedni AndroidTest compile. Fizyczny S22 tylko gdy zmiana dotyczy zachowania mobilnego i urządzenie jest dostępne. Wpis PASS i checkpoint dopiero z dokładnym receipt. Nie zmieniać zasad grant/STOP/Call ownership/PCM/TX w UX refaktorze.
