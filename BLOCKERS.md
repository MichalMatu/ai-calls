# ⛔ BLOCKERS — JEDYNY AKTUALNY REJESTR BLOKERÓW AI CALLS

**ZAKAZ TWORZENIA SZTUCZNYCH BLOKAD:** normatywna zasada na samej górze [AGENTS.md](AGENTS.md). Ta tabela jest **jedynym aktualnym źródłem prawdy o statusach**. [Historia audytu](docs/FRICTION_BLOCKER_LEDGER.md) jest historycznym dziennikiem, a nie drugim, konkurencyjnym źródłem stanu.

**WARSTWA 2 — NATYCHMIASTOWY JAWNY REJESTR NARUSZEŃ:** jeśli jakikolwiek agent/test/commit wprowadzi dodatkową bramkę, limit, potwierdzenie, ręczny etap, ukryte obcięcie danych lub regresję ciągłości zadania, **najpierw** dopisz tutaj nowe ID i status `OPEN`. Nawet jeśli od razu to naprawisz, **nie usuwaj zdarzenia z rejestru**: zachowaj przyczynę, lokalizację, wpływ, commit i test. Nie czekaj na koniec sprintu. Jeżeli nie da się usunąć przyczyny, kontynuuj niezależne prace i zostaw jawne `OPEN`.

**Jak czytać status:** `OPEN` = do naprawy lub niewykluczony realny wpływ; `FIXED_IN_SOURCE` = poprawka w Git, bez kompletnego dowodu QA; `VERIFIED` = podany zakres testów przeszedł. PASS na źródłach/off-call **nie oznacza** udanego fizycznego dialogu telefonicznego. Historyczne wpisy pozostają widoczne.

## AKTYWNE — wymagają pracy

| ID | Warunek i wpływ | Miejsce i przyczyna | Remedium / test | Status |
| --- | --- | --- | --- | --- |
| F-003 | Eksport prywatnych RX/TX/transkryptów nadal wymaga technicznego przenoszenia z ADB/Maca zamiast wygodnego, kontrolowanego przepływu | `scripts/aicall_tools/diagnostics/export_private_call_evidence.sh`, `CampaignToolActivity`; brakuje produktu do lokalnego przeglądu/eksportu | Eksport przez wybór celu użytkownika albo lokalny kontrolowany kanał; nigdy nie wysyłać audio/PII do publicznego GitHub lub wyników agenta. Test eksportu bez połączenia | **OPEN** |
| F-010 | Kampania nie wznawia jeszcze autonomicznie całego zadania i sekwencji rozmów po przerwaniu | `AppOwnedClirCampaignExecutor`, TaskGraph, UI; niedokończona orkiestracja produktu | Trwałe wznowienie w ważnym, nieodwołanym zakresie, bez ręcznego startu każdej kontynuacji. Test przerwania i powrotu do IDLE | **OPEN** |
| F-011 | Możliwe odrzucenie długich wypowiedzi zamiast segmentacji i kontynuacji | `DialogueActionBackend` (80 argument/2000 JSON), `DialogueSkillTextBackend` (600 gotowa odpowiedź/2000 JSON), `Gemma4LiteRtTextBackend` (128 tokenów wyjścia), `BackendOpenAiTextBackend` (4000 wejścia/2000 tekst). Nie każdy limit jest rzeczywistym blokerem — część chroni parser/protokół | Sprawdzić realne ścieżki i testy długiego dialogu; rozdzielić stałe ID akcji od tekstu swobodnego; dodać porcjowanie tam, gdzie występuje utrata treści. Nie usuwać walidacji efektów/ujawniania danych | **OPEN — AUDYT WPŁYWU** |

## USUNIĘTE W ŹRÓDŁACH — najnowsze, QA pozostaje otwarte

| ID | Wykryta niepotrzebna przeszkoda / przyczyna | Zmiana i test | Status |
| --- | --- | --- | --- |
| F-002 | Osobny przycisk, drugi dialog, 10-minutowy `CallSessionEvidenceConsent` na jedną rozmowę po autoryzacji kampanii; utrata nagrań kolejnych prób | `CampaignToolActivity`: opcjonalny, domyślnie wyłączony checkbox zapisu w **jednym** jawnym zatwierdzeniu kampanii; `CampaignAuthorizationGrant` + `CampaignAuthorizationStore`: trwała flaga; `AppOwnedClirCampaignExecutor`: odczyt flagi, bez uzbrajania; niezależny przycisk wyłączenia dla kolejnych rozmów. Testy: `NoArtificialCampaignBlockersContractTest`, `AndroidCampaignAuthorizationStoreContractTest` | **FIXED_IN_SOURCE — QA_PENDING** |
| F-012 | Drugi dialog „Uruchomić AI w rozmowie?” po kliknięciu START w pilocie | `SupervisedCallPilotActivity.confirmAttach`: START jest świadomym uruchomieniem pilota, bez drugiego dialogu. Test source-contract | **FIXED_IN_SOURCE — QA_PENDING** |
| F-013 | Stary test nadal wymagał dokładnie pięciu rezerwacji, wymuszałby powrót usuniętego limitu F-001 | `AndroidCampaignAuthorizationStoreContractTest`: 10/10 równoległych rezerwacji, zachowanie autoryzacji. Test instrumentalny jeszcze nieuruchomiony dla nowego HEAD | **FIXED_IN_SOURCE — QA_PENDING** |
| F-014 | **Regresja w bieżącym sprincie:** bez drugiego dialogu `onResume()` mógł sam uruchomić AI po powrocie z dialera, bez naciśnięcia START. Naruszenie granicy świadomego uruchomienia | `SupervisedCallPilotActivity.onResume`: tylko informuje, nie wywołuje `confirmAttach` ani `awaitAudioRoute`. Test source-contract | **FIXED_IN_SOURCE — QA_PENDING** |

| F-015 | Przestarzały test `CallDiagnosticEvidenceTest.evidenceIsBoundedEvenIfAProducerMisbehaves` żądał obcinania po 1024 zdarzeniach (sprzeczny z naprawionym F-009), co blokowało nowe QA | `CallDiagnosticEvidenceTest.kt`: 1100/1100 zdarzeń, nadal ogranicza niezaufaną długość pojedynczego kodu diagnostycznego do 256 znaków. Commit `2c84513`; poprzedni QA `aicalls-20261103-campaign-evidence-simplification-qa-v1` wykazał FAIL 2/624 na `556bf67` | **FIXED_IN_SOURCE — QA_PENDING** |

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
ID: F-016 (kolejny wolny)
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
