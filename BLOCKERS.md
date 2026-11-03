# ⛔ BLOCKERS — JEDYNY AKTUALNY REJESTR BLOKERÓW AI CALLS

**ZAKAZ TWORZENIA SZTUCZNYCH BLOKAD:** normatywna zasada na samej górze [AGENTS.md](AGENTS.md). Ta tabela jest **jedynym aktualnym źródłem prawdy o statusach**. [Historia audytu](docs/FRICTION_BLOCKER_LEDGER.md) jest historycznym dziennikiem, a nie drugim, konkurencyjnym źródłem stanu.

**WARSTWA 2 — NATYCHMIASTOWY JAWNY REJESTR NARUSZEŃ:** jeśli jakikolwiek agent/test/commit wprowadzi dodatkową bramkę, limit, potwierdzenie, ręczny etap, ukryte obcięcie danych lub regresję ciągłości zadania, **najpierw** dopisz tutaj nowe ID i status `OPEN`. Nawet jeśli od razu to naprawisz, **nie usuwaj zdarzenia z rejestru**: zachowaj przyczynę, lokalizację, wpływ, commit i test. Nie czekaj na koniec sprintu. Jeżeli nie da się usunąć przyczyny, kontynuuj niezależne prace i zostaw jawne `OPEN`.

**Jak czytać status:** `OPEN` = do naprawy lub niewykluczony realny wpływ; `FIXED_IN_SOURCE` = poprawka w Git, bez kompletnego dowodu QA; `VERIFIED` = podany zakres testów przeszedł. PASS na źródłach/off-call **nie oznacza** udanego fizycznego dialogu telefonicznego. Historyczne wpisy pozostają widoczne.

## AKTYWNE — wymagają pracy

| ID | Warunek i wpływ | Miejsce i przyczyna | Remedium / test | Status |
| --- | --- | --- | --- | --- |
| F-003 | Eksport prywatnych RX/TX/transkryptów nadal wymaga technicznego przenoszenia z ADB/Maca zamiast wygodnego, kontrolowanego przepływu | `scripts/aicall_tools/diagnostics/export_private_call_evidence.sh`, `CampaignToolActivity`; brakuje produktu do lokalnego przeglądu/eksportu | Eksport przez wybór celu użytkownika albo lokalny kontrolowany kanał; nigdy nie wysyłać audio/PII do publicznego GitHub lub wyników agenta. Test eksportu bez połączenia | **OPEN** |
| F-010 | Wcześniej brak kontynuacji zadania po niedokończonej rozmowie i wznowieniu usługi | `CampaignContinuationStore`, `AppOwnedCampaignService`, `AppOwnedClirCampaignExecutor`: stan grantu/SIM, harmonogram kolejnej próby, retry bez kwoty liczbowej i przeżywanie restartu usługi; STOP i cofnięcie grantu kończą bieg | Wymagany końcowy QA nowego HEAD i próba S22 po rzeczywistej rozmowie; restart procesu zależy od reguł Android FGS, brak obietnicy wznowienia po force-stop/reboocie | **VERIFIED (Mac QA v6 source; fizyczny S22 OPEN)** |
| F-011 | Możliwe odrzucenie długich wypowiedzi zamiast segmentacji i kontynuacji | `DialogueActionBackend` (80 argument/2000 JSON), `DialogueSkillTextBackend` (600 gotowa odpowiedź/2000 JSON), `Gemma4LiteRtTextBackend` (128 tokenów wyjścia), `BackendOpenAiTextBackend` i broker Python (wcześniejszy limit 4000 wejścia/2000 tekst; usunięte w nowym HEAD). Nie każdy limit jest rzeczywistym blokerem — część chroni parser/protokół | Sprawdzić realne ścieżki i testy długiego dialogu; rozdzielić stałe ID akcji od tekstu swobodnego; dodać porcjowanie tam, gdzie występuje utrata treści. Nie usuwać walidacji efektów/ujawniania danych | **VERIFIED (Mac v12 Android+broker, Unicode, Python unit); realny długi dialog/S22 pozostaje OPEN** |
| F-016 | Historyczny tekst w aktywnie czytanych dokumentach nadal używa sformułowań „F-002 open / recording workflow must be redesigned”, mimo nowej poprawki w źródłach; przyszły agent mógłby odtworzyć niepotrzebne uzbrajanie | `AGENTS.md` (historyczna sekcja F-002), starsze części `docs/HANDOFF_NEXT_CHAT.md` i `docs/ROADMAP.md`. Sprzeczność normatywna: pierwsze linie AGENTS i ten rejestr mają pierwszeństwo | Doprecyzować wyłącznie nieaktualne opisy do stanu historycznego, bez reintrodukcji osobnych zgód; próba porządkowania tych fragmentów została odrzucona przez kontrolę narzędzia. Nie obchodzić odmowy innym wykonawcą | **OPEN — DOC HYGIENE / TOOL_DENIED** |
| F-017 | Integracja prywatnego eksportu ZIP z ekranem kampanii odrzucona przez kontrolę narzędzia | CampaignToolActivity / F-003. Moduł CampaignEvidenceArchive istnieje, ale nie jest używany w UI; brak testu na S22 | Nie obchodzić odmowy, nie deklarować gotowego eksportu. Wdrożyć interfejs przez dozwoloną ścieżkę po usunięciu ograniczenia; walidować bez rozmowy | **OPEN — TOOL_DENIED** |
| F-018 | Spóźniony callback poprzedniej rozmowy mógł nadpisać diagnostykę STOP lub nowego uruchomienia | `AppOwnedCampaignService.handleAttemptStatus`: brak filtra generacji/statusu; wprowadzony razem z F-010 | Dodano porównanie `continuationGeneration`, `campaignRequested`, `grantId` przed zapisaniem stanu i retry; test contract; weryfikacja nowego HEAD oczekuje | **VERIFIED (Mac QA v6 source; fizyczny S22 OPEN)** |
| F-020 | W trakcie oczekiwania na ponowną próbę CLIR pilot nadzorowany mógł uruchomić inny tor audio i przy zakończeniu niezamierzenie wyczyścić autoryzowaną kontynuację | `AppOwnedCampaignService.startSupervisedAudio`: sprawdzał tylko aktywny dial, nie `campaignRequested`/trwały stan oczekującej kampanii | Nie uruchamiać równoległego pilota podczas już rozpoczętej kampanii; bez dodatkowego dialogu, zachować STOP i zaplanowaną próbę. Test `NoArtificialCampaignBlockersContractTest` i Mac build/unit/lint | **VERIFIED (Mac v12 source/unit/build/lint; S22 off-call pending)** |
| F-021 | Test ochrony kampanii błędnie traktował komentarz `finish()/STOP` jako wywołanie `finish(`, przez co pełne QA v10 miało 1 FAIL na 637 testów | `NoArtificialCampaignBlockersContractTest.supervisedPilotCannotDestroyWaitingCampaignContinuation`, asercja `substringBefore(...).contains("finish(")`; fałszywy alarm skanera tekstu | Sprawdzać tylko wykonywalną linię rozpoczynającą się od `finish(`, wykluczając komentarz; pełny Mac QA nowego HEAD | **VERIFIED (Mac v12 unit; false positive resolved)** |
| F-022 | Po instalacji na S22 bezpołączeniowe testy magazynów kampanii zakończyły się 4/4 FAIL przez NPE: instrumentalny `Context.applicationContext` bywa `null` | `CampaignAuthorizationStore(context)` i `CampaignContinuationStore(context)` bezwarunkowo wywoływały `context.applicationContext.getSharedPreferences`; wynik Mac v13 `APP_INSTALL=PASS`, `TEST_APK_INSTALL=PASS`, `FAILURES!!! Tests run:4, Failures:4` | Stosować `context.applicationContext ?: context` dla testowego i normalnego kontekstu, zachować izolację instrumentacyjnych prefs od rzeczywistego grantu; ponowić tylko Android instrumentation, potem źródłowe QA nowych zmian | **FIXED_IN_SOURCE — DEVICE_QA_PENDING** |
| F-023 | Po F-022 instrumentalny kontekst pakietu testowego nie mógł trwale zapisać SharedPreferences; 4/4 testy FAIL `campaign_grant_persist_failed` / `continuation_stop_persist_failed` mimo kompilacji i instalacji PASS | `AndroidCampaignAuthorizationStoreContractTest` i `CampaignContinuationStoreContractTest`: użycie `InstrumentationRegistry.getInstrumentation().context` z domyślnymi nazwami prefs, nieizolowane źródło danych lub niewspierana prywatna przestrzeń testowego Context | Użyć `targetContext` z dedykowanymi nazwami preferencji w konstruktorach store, nie odczytywać ani nie czyścić prawdziwego `campaign_authorization_grant_v1` czy `campaign_continuation_v1`; powtórzyć 4 bezpołączeniowe testy S22 i potwierdzić brak zmian grantu produkcyjnego | **OPEN — SOURCE_FIX_PENDING** |

**WERYFIKACJA:** Mac task aicalls-20261103-friction-two-layer-source-qa-v3, SHA 338ce662, ANTI_FRICTION=PASS, Python anti-friction PASS, Kotlin unit/build/debugAndroidTest APK/lint PASS (BUILD SUCCESSFUL in 2m 14s). Bez wdrożenia ani rozmowy na S22.

**S22 v13 (f42ce03e):** oba APK zainstalowane PASS na SM-S906B IDLE, bez dial/rekordingu. Android instrumentation 4/4 FAIL (`NullPointerException` z `context.applicationContext == null`), nie jest to PASS. F-022 naprawione w źródłach (store używa `context.applicationContext ?: context`), czeka na ponowny, wyłącznie bezpołączeniowy test z Mac. Nie usuwać historii błędu.

**QA v12 — potwierdzony:** `aicalls-20261103-broker-pilot-sprint-qa-v12`, pinned SHA `f42ce03e14d9be60ef4036d6850971fc27e99832`, Mac DONE/0: ANTI_FRICTION=PASS, 225/225 testów Python, Android unit/build debug APK+AndroidTest APK/lint PASS; `BUILD SUCCESSFUL in 2m 30s`. Test F-021 nie zgłasza już fałszywego FAIL. Nie stanowi to dowodu rozmowy. `aicalls-20261103-s22-install-offcall-instrumentation-v13` uruchomiono na osobnym Mac zadaniu, wynik musi być odczytany przed zmianą statusu urządzenia.

**2026-11-03 nowy sprint:** S22 ponownie widoczny przez ADB (SM-S906B, `mCallState=0`, Mac preflight `aicalls-20261103-s22-check-0643-readonly-v9` PASS). Nowe poprawki F-020 oraz F-011 (Android+broker Python) czekają na pełne QA Mac i fizyczne testy bez rozmowy; do czasu wyniku nie uznawać ich za zweryfikowane.

**NOWE QA SPRINTU 2026-11-03:** Mac `aicalls-20261103-sprint-verified-source-qa-v6` na SHA `32ff901b`: ANTI_FRICTION=PASS, Python 222/222 PASS, jednostkowe Kotlin, build debug APK i androidTest APK, lint PASS (BUILD SUCCESSFUL in 1m 35s). Poprzednie QA v5 633/1 FAIL zostało poprawione (F-019). To PASS źródeł, nie fizycznego dialogu. S22 preflight v7: AUTHORIZED_DEVICE_COUNT=0, instalacja i test live-call PENDING.

## USUNIĘTE W ŹRÓDŁACH — Mac QA v3 PASS

| ID | Wykryta niepotrzebna przeszkoda / przyczyna | Zmiana i test | Status |
| --- | --- | --- | --- |
| F-002 | Osobny przycisk, drugi dialog, 10-minutowy `CallSessionEvidenceConsent` na jedną rozmowę po autoryzacji kampanii; utrata nagrań kolejnych prób | `CampaignToolActivity`: opcjonalny, domyślnie wyłączony checkbox zapisu w **jednym** jawnym zatwierdzeniu kampanii; `CampaignAuthorizationGrant` + `CampaignAuthorizationStore`: trwała flaga; `AppOwnedClirCampaignExecutor`: odczyt flagi, bez uzbrajania; niezależny przycisk wyłączenia dla kolejnych rozmów. Testy: `NoArtificialCampaignBlockersContractTest`, `AndroidCampaignAuthorizationStoreContractTest` | **VERIFIED (Mac QA v3 source/unit/build/lint; S22 runtime untested)** |
| F-012 | Drugi dialog „Uruchomić AI w rozmowie?” po kliknięciu START w pilocie | `SupervisedCallPilotActivity.confirmAttach`: START jest świadomym uruchomieniem pilota, bez drugiego dialogu. Test source-contract | **VERIFIED (Mac QA v3 source/unit/build/lint; S22 runtime untested)** |
| F-013 | Stary test nadal wymagał dokładnie pięciu rezerwacji, wymuszałby powrót usuniętego limitu F-001 | `AndroidCampaignAuthorizationStoreContractTest`: 10/10 równoległych rezerwacji, zachowanie autoryzacji. Test instrumentalny jeszcze nieuruchomiony dla nowego HEAD | **VERIFIED (Mac QA v3 source/unit/build/lint; S22 runtime untested)** |
| F-014 | **Regresja w bieżącym sprincie:** bez drugiego dialogu `onResume()` mógł sam uruchomić AI po powrocie z dialera, bez naciśnięcia START. Naruszenie granicy świadomego uruchomienia | `SupervisedCallPilotActivity.onResume`: tylko informuje, nie wywołuje `confirmAttach` ani `awaitAudioRoute`. Test source-contract | **VERIFIED (Mac QA v3 source/unit/build/lint; S22 runtime untested)** |
| F-015 | Przestarzały test `CallDiagnosticEvidenceTest.evidenceIsBoundedEvenIfAProducerMisbehaves` żądał obcinania po 1024 zdarzeniach (sprzeczny z naprawionym F-009), co blokowało nowe QA | `CallDiagnosticEvidenceTest.kt`: 1100/1100 zdarzeń, nadal ogranicza niezaufaną długość pojedynczego kodu diagnostycznego do 256 znaków. Commit `2c84513`; poprzedni QA `aicalls-20261103-campaign-evidence-simplification-qa-v1` wykazał FAIL 2/624 na `556bf67` | **VERIFIED (Mac QA v3 source/unit/build/lint; S22 runtime untested)** |


## QA sprintu — poprawiony fałszywy FAIL

| ID | Objaw | Przyczyna | Naprawa / dowód | Status |
| --- | --- | --- | --- | --- |
| F-019 | Nowy test długiej odpowiedzi porównywał też końcowe spacje, choć polityka od dawna normalizuje `.trim()`; fałszywy FAIL 1/633 unit testów w QA v5 | `DialogueSkillTextBackendTest` (v5 `e057cf8`); nowy test zbyt rygorystyczny względem istniejącego kontraktu | Oczekiwana wartość `approved.trim()`; poprawka `fde032c`; Mac QA v6 PASS | **VERIFIED (Mac QA v6 source; fizyczny S22 OPEN)** |

## WCZEŚNIEJSZE POPRAWKI — dowód źródłowy i granica jego ważności

| ID | Problem | Stan i dowód |
| --- | --- | --- |
| F-001 | Arbitralny maksymalny limit pięciu połączeń | **VERIFIED (source/unit)** — `87c7d903`, test polityki; brak limitu prób. Fizyczna wielokrotna kampania niepotwierdzona |
| F-004 | Automatyczny koniec po dziesiątej turze | **VERIFIED (build/unit/lint/S22 install)** — `TASK_SCOPED_TURNS`; fizyczne >10 tur nadal OPEN |
| F-005 | WAV 2 MB/turę, 24 MB/sesję, trzy sesje, obcięte transkrypty | **VERIFIED (build/unit/lint/S22 install)** — brak sztywnych limitów; fizyczne >24 MB nadal OPEN |
| F-006 | Błędne przypisanie zdalnego rozłączenia do wygaśnięcia grantu | **FIXED_IN_SOURCE, S22 REMOTE observed** — `owned_call_ended`; pełny test kolejnej rozmowy otwarty |
| F-007 | Koniec po trzech oknach ciszy | **VERIFIED (build/unit/lint/S22 install)** — nasłuch w ważnym własnym połączeniu; długi hold OPEN |
| F-008 | Podgląd ograniczony do 100 wpisów/24 tys. znaków | **VERIFIED (build/unit/lint/S22 install)** — pełny transkrypt; długi UI OPEN |
| F-009 | Koniec raportu po 1024 zdarzeniach | **VERIFIED (build/unit/lint/S22 install)** — test 1801 zdarzeń; realna długa rozmowa OPEN |

## KANDYDACI SKLASYFIKOWANI, NIE USUWAĆ W CIEMNO

- `ClirCampaignSpec.GRANT_TTL_MS = 24 h`: ograniczenie czasu zakresu autoryzacji; wymaga decyzji produktu, nie automatycznej likwidacji.
- `ChatRelayEnvelope.MAX_TURN_ID = 10_000`, `MAX_TEXT_CHARS = 4_000`: granice protokołu; sprawdzić długie sekwencje, bez fałszywego PASS.
- `CallRealtimeOutputResponseBuffer`: limity pamięci pending audio/transkryptu; chronią zasoby i zatwierdzanie wypowiedzi, sprawdzić czy przy przeciążeniu jest poprawny backpressure, a nie cicha utrata.
- `SupervisedCallPilotActivity` (trzy tury), `BidirectionalMediaProbe`, `s22_call_control.py`: czas/limit w izolowanych **testach fizycznych**, nie normalnym runtime kampanii.
- `AppOwnedClirCampaignExecutor.CALL_ACTIVE_TIMEOUT_MS = 30_000`: watchdog aktywacji; sprawdzić telephony edge case, ale nie eliminować mechanizmów STOP ani ochrony obcego połączenia.

## OBOWIĄZKOWE POLA KAŻDEGO NOWEGO NARUSZENIA

```text
ID: F-024 (kolejny wolny)
Wykryto: data + commit/agent
Ścieżka i symbol: dokładny kod
Warunek stopu: pełny warunek logiczny lub ograniczenie UX
Wpływ: jakie uprawnione zadanie/dane/test przestają działać
Źródło: kto/co wprowadziło i dlaczego
Klasyfikacja: A zbędne tarcie | B brak implementacji | C realna granica prawna/zgody | D platforma/zasoby
Naprawa: usunąć przyczynę, nie maskować inną bramką
Test antyregresyjny: plik, scenariusz, wynik
Commit naprawy i wersja wdrożona: dwa osobne SHA albo jawne BRAK
Status: OPEN | FIXED_IN_SOURCE | VERIFIED
```

**Mechaniczny test drugiej warstwy:** `PYTHONPATH=scripts python3 -m unittest scripts.tests.test_audit_product_friction` lub `python3 scripts/aicall_tools/diagnostics/audit_product_friction.py` oraz `NoArtificialCampaignBlockersContractTest`. Skaner wykrywa regresje znanych blokerów i brak wpisów; nie dowodzi nieobecności wszystkich możliwych problemów. Przy każdej zmianie wpływającej na flow ręcznie klasyfikuj nowe `MAX_*`, `return false`, dialogi, przedwczesne `finish`, timeouty i ręczne zależności.

**Realne granice pozostają:** świadoma autoryzacja zadania i diagnostyki, prawa rozmówców, Android, kontrola wyłącznie własnego `Call`, STOP, ochrona danych i odmowy narzędzi. Żadnych obejść tych granic.
