# UX-06 — Start/Kampanie statusy + biblioteka scenariuszy: etap źródłowy

Data: 2026-11-07. Repo: `MichalMatu/ai-calls`, `main`. Zakres nowego zadania właściciela po UX-05.

## Wykonane commity

- `3418a3e04e4f74fa2e1b760722e19a76db77076c`: trzy jednodziałaniowe karty zamiast zbędnych accordion, evidence accordion zachowany; szczegóły ostatniej próby dostępne bez rozwijania; odrębny `actionFeedback`, rzeczywisty stan dialera, synchroniczne odczyty grantu/nagrywania, PL/EN; zaktualizowany Android UI contract.
- `afbafbfa1a8ef41ed122ecb4ed8cd23bd04ce4cc`: `ACTION_CANCEL_OUTGOING_TASK` przestała wywoływać globalny STOP INCOMING, ale globalne STOP AI pozostało dostępne.
- `2afdbea3ea7c68610819bac5bed75fc11dd55231`: `CampaignExecutionStatusPresenter.CANCELLED`, bez mylenia anulowania z sukcesem usługi.
- `db519a5e178ea89c23707818c89d380db9c12e4c`: canonical BLOCKERS F-119–F-124, plan i roadmap.
- `ec3a280c4d7f4bc2383c8bf3e3d3756e2ea8651d`: test źródłowy dopasowany do nowego kontraktu diagnostyki i osobny kontrakt zakresu anulowania.
- `dcc6496155ecc543cf02854dbf380b6a382399f9`, `5a8969827d7b791f02686bc77ea91b4ccae6ff8c`: Home: odrębny komunikat akcji, zgodność grantu z bieżącą SIM, spójne opisy PL/EN, poprawne feedback uprawnień.
- `9077669cd16bd259f4da5000b9f815fdd063bd5a`: neutralny katalog 10 codziennych scenariuszy w `conversation-core` + 4 testy JVM, tylko CLIR ma oznaczony istniejący adapter.
- `a03e98b790626502dc6da8b2174dd89d86a9b7a2`: ekran biblioteki presetów z formularzami, edycją, podglądem, zapisem szkiców w istniejącym szyfrowanym `AndroidConversationPreparationStore`, AndroidTest; nowy punkt nawigacji w Kampaniach.

## QA rzeczywiste — nie ekstrapolować

- `aicalls-ux06-campaign-status-host-gate-20261107-v1`: wynik **FAILED**, test źródłowy Sprint6 oczekiwał poprzedniego zapisu `evidenceStatus, runtimeStatus`. Gradle APK/AndroidTest osiągnął sukces, ale Python 337 miał 1 FAIL. Nie relabelować jako PASS.
- `aicalls-ux06-host-regression-fix-20261107-v2`: **DONE/0** na `ec3a280`. 7/7 celowane Python, 338/338 całego host suite, pełne `verify_host.sh`, Gradle assemble/AndroidTest compile, friction audit PASS; clean checkout. Żaden test telefoniczny/S22 nie był wykonywany. `DEVICE_STATUS=NOT_CHECKED`, blokada realnego call nadal obowiązuje.
- `aicalls-ux06-scenarios-source-host-20261107-v3`: zlecony dla kodu katalogu/scenario UI, **QA_PENDING**, nie deklarować wyniku bez terminalnej odpowiedzi.

## Granice wdrożenia i dalsza praca

- Nowe scenariusze poza CLIR są na razie tylko formularzami roboczymi. Nie ma rzeczywistych połączeń, rezerwacji, potwierdzeń terminów czy zewnętrznych efektów dla Mechanik/Przychodnia. Nie oznaczać ich jako wykonawczo gotowe.
- F-122: nagrywanie bieżącego Call trwa po zmianie zgody wyłącznie na przyszłe próby; UI opisuje to uczciwie, ale osobne sterowanie aktualnym rejestratorem i race test nadal OPEN.
- F-123: aktualny typowany stan sesji, odrębny od historycznej wiadomości i grantu, nadal OPEN.
- F-124/F-080: selektor właściwej sesji/pełne nagranie nie zostały zaimplementowane.
- Odrębny lokalny edytor dowolnych własnych scenariuszy, wersje, wybór strategii i snapshot przy START, integracja pełnego Lab i kwalifikowana telefonia dla nowych branż to dalsze kroki.
- Nie zmieniano Samsung/Shizuku RX/TX, IdentityVault, CallExternalEffect/CallCommitmentGate ani uprawnień telefonii. Nie instalowano APK na S22.

## Warunek następnego etapu

Najpierw terminalny receipt v3 na dokładnym commit SHA. Dopiero po zielonym źródłowym QA ocenić bezpieczny preflight S22 `IDLE` i testy nowego ekranu bez telefonów. Potem kontynuować typowane live-status, przegląd kontrolera aktywnego zapisu i dopiero wykonawcze scenariusze z dokładną autoryzacją.
