## UX-06 — aktualny stan po syntetycznym S22 (2026-11-07)

**SOURCE_PASS na `25a41a0`:** 341/341 Python, Gradle JVM/lint/APK/AndroidTest compile i friction PASS. **S22 SYNTHETIC_PASS:** 10/10 Android UI, `SM-S906B`, IDLE przed/po, APK wgrane bez wipe. Nowy ekran 10 presetów, wersjonowane własne kopie i formularze w szyfrowanym store, read-only bieżące fazy AI odróżnione od historii z czasem, lepsze opisy Start/Kampanie. **NIEUDOWODNIONE:** manual UX/TalkBack, realne taski poza CLIR, F-122/F-123/F-124/F-080. [Receipt i granice](checkpoints/2026-11-07-ux06-source-s22-synthetic-acceptance.md).

---

**UX-06 drobna poprawka zakresu START:** `d38408ba` nazwa i opis odtąd jawnie wskazują wyłącznie CLIR/skrypt zamiast sugerować wykonywanie dowolnego presetu. Kwalifikowany wybór innych scenariuszy przed START nadal OPEN; nie uruchamiać przykładowych presetów telefonicznie.

## UX-06 — etap 2: wersjonowane scenariusze i historia (w toku)

**Kod:** `704e697` partial draft; `8508d2e` pure personal library; `cf42216` encrypted persistence; `b2a2fc5` copy/rename/goal/revise/restore/delete UI; `a62e9da` izolowany AndroidTest i Python; `b74284b` znacznik czasu historycznych zdarzeń; `3c7a8b5` zabezpieczenie niezapisanych zmian. **HOST:** v3 `a03e98b` DONE/0; v4 `b2a2fc5` DONE/0 (338 Python, JVM/AndroidTest compile/APK/friction PASS); v5 `3c7a8b5` QA_PENDING. **S22:** NOT_RUN dla aktualnego kodu. [Nowy checkpoint](checkpoints/2026-11-07-ux06-personal-scenarios-and-history-receipt.md). **OPEN:** F-122, F-123, F-124, F-080 oraz kwalifikowane połączenia poza CLIR.

---

## UX-06 — poprawa ergonomii i rzetelności stanów (ACTIVE / QA_PENDING, 2026-11-07)

**Nowe zadanie właściciela:** ekran Start i Kampanie, opis ról/przycisków, pięć rozwijanych sekcji (zachować tylko bogatą sekcję nagrań), czytelne szczegóły bez rozwijania, wiarygodne live/status/history. To nowe zgłoszenie po zamkniętym UX-05, bez unieważniania poprzednich PASS.

**Etap scenariuszy (kolejne commity):** `9077669` — 10 szablonów codziennych zadań i test JVM; `a03e98b` — edytowalny ekran formularzy, podgląd i szyfrowany zapis w istniejącym store; nowe scenariusze poza CLIR **DRAFT_ONLY**, połączenia niewdrożone. **QA:** `aicalls-ux06-host-regression-fix-20261107-v2` **DONE/0**, `ec3a280`, 338 host PASS, pełny Gradle/AndroidTest compile/APK/friction PASS. Nowy katalog: `aicalls-ux06-scenarios-source-host-20261107-v3` **QA_PENDING**. [Checkpoint](checkpoints/2026-11-07-ux06-campaign-and-scenario-source-checkpoint.md).

**Źródłowy etap 1:** `3418a3e` — czytelny status roli dialera, wyjaśnienie skutków, bezpośrednie karty Skills/supervised/dialer, widoczne szczegóły, odrębny feedback, synchronizacja wyboru zapisu, zachowanie edycji przy rekreacji i PL/EN; nowy Android UI contract. **Etap 2:** `afbafbfa` — anulowanie OUTGOING bez globalnego STOP INCOMING; `2afdbea` — typowany wynik anulowania w historycznym presenterze i JVM test. **Mac Local Agent:** `aicalls-ux06-campaign-status-host-gate-20261107-v1` zlecony, brak wyniku => **QA_PENDING**. Zakaz ogłaszania HOST_PASS/S22_PASS bez receipts.

**Otwarte:** F-122 zatrzymanie nagrywania aktywnej sesji, F-123 live-state/attempt/result, F-124 wybór właściwego archiwum i wąskie regex WAV; szczegółowy plan i scenariusze w [UX06 plan](UX06_USABILITY_AND_SCENARIO_PLAN.md). Jedna zatwierdzona operacja CLIR pozostaje rzeczywistą ścieżką zewnętrzną; przykładowych presetów wizyt nie przedstawiać jako produkcyjnych rozmów.

---

# AI Calls — roadmapa (Sprint 6 i UX-01–05 zamknięte, 2026-11-07)

**Stan:** `UX_ITERATION_CLOSED / UX05_VERIFIED`. Najnowszy kod UX `main@1b359146`, **325 testów hosta PASS**, S22 **28/28 native + 24/24 visual**, hotfix targeted **1/1 + 3/3 PASS**, realna Gemma **2 tury + STOP PASS**, finalny ZIP **46 screenshotów**. [Dokładne receipts](checkpoints/2026-11-07-ux-sprint6-final-handoff.md). Nowa lokalizacja UX-05 zamknięta; nie wracać do mikro-poprawek UX bez nowego zgłoszenia lub regresji.

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
4. **UX-04: DONE / SOURCE_PASS.** `LanguageModelStatusPresenter` (model installed/ready/active, transfer busy/postęp/feedback) i `AudioEngineActiveProfilePresenter` (zapisany profil → odczytowe podsumowanie) są czyste, typowane; istniejące store, probes, usługi pobierania i TTS pozostają jedynymi właścicielami efektów. **13 JVM, 332 Python, friction, host, AndroidTest compile/APK PASS**, Mac clean na `7df2c33`; **S22 nowego kodu NOT_RUN**. [Checkpoint](checkpoints/2026-11-07-ux04-model-and-audio-presentation-closeout.md).
5. **UX-05 / F-103: DONE / VERIFIED (automated UI).** PL/EN 553 wspólne klucze; Home, Campaign, Skills, Engine, modele, dialer, pilot i chat przetłumaczone. 337 Python, 10 focused, APK/AndroidTest/host/friction PASS, **S22 2/2 natywne + 4/4 prywatne zrzuty PL/EN PASS**, przywrócony język `pl`. [Checkpoint](checkpoints/2026-11-07-ux05-pl-en-accessibility-closeout.md). Osobny manual TalkBack nie był wykonywany.

**UX-01–04 są zachowane jako odrębne sprawdzone zakresy, UX-05 zamknięto jednym kolejnym sprintem.** Uzasadnienie i architektura: [SAFE_MODULARIZATION_PLAN](SAFE_MODULARIZATION_PLAN.md) oraz [ARCHITECTURE](ARCHITECTURE.md).

## P2 — funkcjonalność produktu i niezależne bramki

- **F-080:** rzeczywisty *ciągły* recorder i pełny player sesji z osią czasu. Obecne dymki odtwarzają tylko istniejące WAV RX/TX z tur, nie nagranie całego Call.
- **F-091 i rozmowy:** osobna realna kontrola end_call / naturalnego zakończenia / przejęcia / powrotu AI / STOP / timeoutu, **wyłącznie** z ważną autoryzacją i kontrolą S22.
- **Jakość Gemmy:** oddzielna walidacja semantyki, poprawności pytań, kompletności wiadomości i naturalności; licznik `END_CALL 2/2` nie jest jakością odpowiedzi.
- **F-103:** **CLOSED / VERIFIED** dla PL/EN i automatycznej dostępności UI z fizycznym odbiorem S22; manualny TalkBack osobną opcjonalną kontrolą, nie dowodem już wykonanym.
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
