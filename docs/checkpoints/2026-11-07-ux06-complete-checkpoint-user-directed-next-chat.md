# AI Calls — UX-06: kompletny checkpoint i handoff do dalszych poprawek wskazywanych przez właściciela

**Data:** 2026-11-07  
**Repo:** `MichalMatu/ai-calls`; gałąź `main`; bez nowej gałęzi, force push, GitHub Actions, Codex i Work.  
**Sprawdzony kod:** `25a41a081be032cf717c6a0f7e78f6a704488611`.  
**HEAD przed tym dokumentem:** `97b329315fc2f08ff2239fda4a4563dca660c0ea` — po przetestowanym kodzie występuje wyłącznie commit dokumentacyjny `97b3293`. Przed kolejnymi zmianami pobierz bieżący `main` HEAD; nie zakładaj, że SHA pozostał taki sam.  
**Wynik:** `UX06_SOURCE_PASS / HOST_PASS / S22_SYNTHETIC_10_OF_10_PASS / REAL_CALL_NOT_RUN / PRODUCT_SCOPE_PARTIAL`.  
**Tryb kolejnego czatu:** **USER_DIRECTED** — użytkownik będzie kolejno wskazywał miejsca do przeglądu i/lub poprawienia. **Nie inicjuj autonomicznie nowego sprintu ani zaległych F-122/123/124/080 bez wskazania zakresu przez użytkownika**.

## 1. Źródła prawdy i obowiązkowy start

1. `AGENTS.md`, `README.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md`, `BLOCKERS.md`.
2. Ten checkpoint; `docs/UX06_USABILITY_AND_SCENARIO_PLAN.md`; `docs/checkpoints/2026-11-07-ux06-source-s22-synthetic-acceptance.md`; poprzedni `docs/checkpoints/2026-11-07-ux06-personal-scenarios-and-history-receipt.md`.
3. `docs/LOCAL_AGENT_HOST_FLOW_2026-11-01.md`, `docs/CONVERSATION_RUNTIME.md`, `docs/SKILLS_AND_LAB.md`, `docs/PRODUCT_ASSEMBLY_PLAN.md`, `docs/TEST_LAB_SCOPE.md`.
4. GitHub Connector: potwierdź bieżący `main` HEAD, drzewo, dokładne pliki i ostatnie commity. Mac Local Agent: potwierdź właściwy host/installation, wynik ostatnich zadań, brak konfliktów i czystość checkoutu **przed** modyfikacją.
5. Najpierw przedstaw krótkie preflight/stan — **poczekaj na wskazanie pierwszego miejsca przez właściciela**.

## 2. Narzędzia, transport i sprzęt

- Kod i dokumentacja: GitHub Connector w `MichalMatu/ai-calls`, praca na `main`, commity z `[skip ci]`. Bez GitHub Actions (nawet statusu), Codex, ChatGPT Work, nowej gałęzi ani force push.
- Fizyczne operacje, Gradle/ADB/S22: **Mac Local Agent**, prywatny repozytorium transportowe `MichalMatu/local-agent:agent-control`; host `macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. Checkout `/Users/michal/agent-workspace/repos/ai-calls/work`.
- Zadania hosta: `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/tasks/<unique-id>.json`; receipts: równoległy katalog `task-results/<unique-id>.json`, status w `status.json`. Nie utożsamiaj historycznej daty status.json z aktualnym potwierdzeniem; używaj wyników dokładnych zadań.
- Samsung S22: `SM-S906B`, USB serial `RFCT70L7E8J`. Każde ADB wymaga potwierdzenia właściwego urządzenia i `IDLE`, braku aktywnej kampanii, zanim wykonasz testy. Instalacja `adb install -r -t` zachowuje dane; nie odinstalowywać ani nie kasować.
- Zamrożony tor Samsung/Shizuku/PCM/PFD, realny `Call` ownership, uprawnienia, `STOP AI`, granice IdentityVault, decyzje skutków zewnętrznych nie podlegają przypadkowemu refaktoringowi.
- Nie wykonywać rzeczywistych połączeń, rezerwacji, operacji konta ani ujawniania danych bez dokładnego autoryzowanego zadania i jawnego uzasadnienia testu. Screenshoty pozostają możliwe; nie stosować `FLAG_SECURE`. Prywatne WAV, obrazy, transkrypty, numery i dane nie mogą trafić do GitHub/logów.

## 3. Co zostało wdrożone

| Obszar | Faktyczny rezultat | Granica |
|---|---|---|
| **Start** | Rozdzielone informacje o konfiguracji, autoryzacji, feedback akcji i fazie procesu AI. Kontrola zgodności upoważnienia z SIM. | START wciąż uruchamia **wyłącznie autoryzowane CLIR w trybie skrypt + AI**, teraz jawnie opisane. To nie jest wspólny START dla presetów. |
| **Kampanie** | Role dialera z czytelnym stanem i skutkami, bezpośrednie karty Skills/testu AI; wieloprzyciskowa sekcja nagrań nadal rozwijana; szczegóły historyczne widoczne bez accordion. | Nie wszystkie przejścia stanów mają identyfikator próby i zdarzenie Telecom. |
| **Przyciski i STOP** | Rozdzielono komunikaty akcji od stanu; `Anuluj zadanie` korzysta z oddzielnej akcji OUTGOING, nie wyłącza INCOMING. Globalny STOP pozostaje globalny. | Testy syntetyczne nie zastępują regresji fizycznych połączeń. |
| **Nagrywanie** | Synchronizacja checkboxa zapisu przyszłych prób oraz jasne wyjaśnienie jego zakresu. | Wyłączenie przyszłych prób **nie zatrzymuje aktywnego rejestratora** (F-122 OPEN). |
| **Historia techniczna** | Typowany prezenter wyniku (w tym `CANCELLED`) i atomowo zapisana data ostatniego komunikatu serwisu. | Historia nie dowodzi stanu live ani powodzenia zadania. |
| **Bieżąca faza** | Wspólny `CampaignLiveStateResolver` pokazuje PREPARING/WAITING_FOR_PHONE/ATTEMPT_IN_PROGRESS/SUPERVISED_AUDIO/INACTIVE na Start/Kampaniach. | To obserwacja bieżącego **procesu**, nie trwałe zdarzenie Call/Telecom (F-123 częściowo OPEN). |
| **Scenariusze** | 10 fabrycznych presetów: CLIR, mechanik, przychodnia, dentysta, fryzjer, restauracja, urząd, reklamacja, dostawca, wycena. Własne kopie: nazwa, cel, edycja, rewizje, restore, delete, podgląd. | Wszystkie poza CLIR są **DRAFT_ONLY**, nie umieją dzwonić ani potwierdzać rezerwacji. |
| **Dane i Skills** | Szkice i osobiste scenariusze korzystają z istniejącego szyfrowanego `AndroidConversationPreparationStore` (Keystore-AEAD, AtomicFile, no-backup); Skills/sejf nie zostały przebudowane. | Pełny snapshot scenariusza/Skills/strategii na START i edycja warunków przejścia jeszcze niegotowe. |
| **Formularz Mechanik** | Cel: diagnostyka; termin: przyszły tydzień rano; budżet: 300 zł; instrukcja: bez zatwierdzania dodatkowych napraw. | Przykładowe wartości formularza, bez zlecenia rzeczywistej wizyty. |

**Podstawowe pliki:** `MainActivity.kt`, `campaign/CampaignToolActivity.kt`, `campaign/AppOwnedCampaignService.kt`, `campaign/CampaignLiveStateResolver.kt`, `campaign/CampaignRuntimeStatusStore.kt`, `conversation/ScenarioLibraryActivity.kt`, `conversation/AndroidConversationPreparationStore.kt`, `conversation-core/.../ScenarioCatalog.kt`, `conversation-core/.../ScenarioPersonalLibrary.kt`.

## 4. Wyniki testów i rzeczywiste dowody

| Receipt | Źródłowy SHA | Wynik |
|---|---|---|
| `aicalls-ux06-scenarios-source-host-20261107-v3` | `a03e98b` | DONE/0: nowe presety/JVM PASS, pełny host + 338 Python PASS. |
| `aicalls-ux06-personal-scenarios-host-20261107-v4` | `b2a2fc5` | DONE/0: wersjonowanie/JVM, Gradle/APK, 338 Python, friction PASS. |
| `aicalls-ux06-personal-scenario-encrypted-status-host-20261107-v5` | `3c7a8b5` | DONE/0: 340 Python, source/JVM, AndroidTest compile/APK, friction PASS. |
| `aicalls-ux06-live-state-source-qa-20261107-v6` | `25a41a0` | FAILED **przed testami**: samodzielne Gradle bez SDK path; nie jest to dowód błędu implementacji. |
| `aicalls-ux06-live-state-host-sdk-fixed-20261107-v7` | `25a41a0` | **DONE/0: 341/341 Python, JVM/Gradle/lint/APK/AndroidTest compile, friction PASS; czysty checkout Mac.** |
| `aicalls-ux06-s22-idle-preflight-20261107-v1` | sprzęt | DONE/0: dokładny SM-S906B, USB, `mCallState=0`, kampania nieuruchomiona. |
| `aicalls-ux06-s22-safe-synthetic-ui-acceptance-20261107-v1` | `25a41a0` | APK i test APK zainstalowane bez wipe, Android zwrócił `OK (10 tests)`; wrapper błędnie oznaczył FAILED z powodu nieobecnego `INSTRUMENTATION_CODE:-1`. |
| `aicalls-ux06-s22-safe-synthetic-ui-acceptance-20261107-v2` | `25a41a0` | **DONE/0: 10/10 syntetycznych testów UI/prywatnego zapisu PASS**, preflight i postcheck IDLE, brak serwisu kampanii, czysty checkout. |

**Testy S22 v2 obejmowały:** `AndroidUiAccordionContractTest`, `AndroidUiPolishContractTest`, `AndroidScenarioLibraryContractTest`, `AndroidPersonalScenarioStoreContractTest`, `AndroidCampaignRuntimeStatusRecordTest`. Nie wykonywano prawdziwego Call/IVR, ocen manualnych TalkBack, jakości audio, testu potwierdzenia rezerwacji ani pełnego visual audit z prywatnymi screenshotami dla tego etapu. Nie powtarzać zielonych testów bez zmiany dotkniętego kodu lub uzasadnionego ryzyka regresji.

## 5. Otwarte zaległości — NIE są automatycznym poleceniem wykonania

- **F-122:** brak zatrzymania **aktywnego** recordera po zmianie zgody na przyszły zapis; wymaga osobnej operacji, race test i kontroli prywatności.
- **F-123:** brakuje trwałego, skorelowanego `attemptId` / faktycznego stanu Call/Telecom / rezultatu; bieżący stan procesu i datowana historia już wdrożone, ale nie rozstrzygają wyniku zewnętrznego.
- **F-124:** Kampanie wybierają „najnowszy” katalog zamiast wskazanej sesji, a dawny odczyt plików ma ograniczenia długości tury.
- **F-080:** pełne ciągłe nagranie i odtwarzanie całej rozmowy z osią czasu osobno OPEN.
- **Scenariusze:** pozostały pełny edytor warunków/przejść, wybór przed START, adaptery taskowe, kontrolowane efekty, integracja Skills/snapshot i scenariuszowe Laboratorium.
- **UX jakościowy:** ręczna ocena PL/EN, TalkBack, długie teksty, brak miejsca, utrata procesu, aktywne nagrywanie, przypadki wielokrotnych kliknięć.
- Statusów starszych wpisów `BLOCKERS.md` nie zmieniać na VERIFIED bez precyzyjnego dowodu odpowiadającego danemu przypadkowi; nie usuwać historycznych czerwonych receipts.

## 6. Tryb nowego czatu: właściciel wyznacza każdy punkt

Po wklejeniu [promptu](../UX06_NEXT_CHAT_PROMPT.md) wykonaj tylko preflight: świeży HEAD, przywołanie checkpointu, host status i dokładne receipts, dotychczasowe granice. Następnie **czekaj na opis ekranu/funkcji od użytkownika**, który będzie wybierał punkty na bieżąco. `Sprawdź` = audyt kodu, stanu UI, konsekwencji i testów bez zmian; `Popraw/Wdróż` = najpierw precyzyjny reaudyt i zakres, następnie implementacja, adekwatne testy, aktualizacja `BLOCKERS.md` i checkpoint. Nie odbiegaj na inne otwarte zadania, o ile nie ma potwierdzonego twardego sprzężenia. Każda poprawka musi zachować rzeczywistą autoryzację i rozróżnienie `SOURCE_PASS / SYNTHETIC_S22_PASS / REAL_CALL_NOT_RUN`.

## 7. Stan końcowy tego przekazania

Ten dokument jest checkpointem **bez zmian w kodzie aplikacji**. Ostatni wynik wykonania był zielony na testowanym source SHA `25a41a0`. Stan repozytorium może się zmienić po zapisaniu dokumentacji; zawsze sprawdź świeży HEAD. Następne działanie: **pierwszy punkt wskazany przez użytkownika**.
