# Historia wykrywania sztucznych blokerów AI Calls — ZAPIS ZDARZEŃ

**Aktualne statusy i pełny wykaz: [BLOCKERS.md](../BLOCKERS.md) w katalogu głównym.** To jest archiwalny, niekasowany dziennik zmian i źródeł. Historyczne `OPEN` i `FIXED IN CODE` w tabeli poniżej opisują moment jej sporządzenia, nie najnowszy stan. Nie kopiować ich jako aktualnych statusów. Nadrzędny zakaz: [AGENTS.md](../AGENTS.md) (pierwsze linie).

## 2026-11-03 — druga warstwa, audyt 293 plików i naprawa kolejnych przypadków

- Audit Mac Local Agent `aicalls-20261103-policy-code-deep-audit-v1`: `done/0`, **293** pliki produkcyjne/skrypty, **1197** wzorców kandydujących do klasyfikacji. Skan jest heurystyczny, nie oznacza 1197 błędów.
- `F-002`: per-call 10-minutowe `CallSessionEvidenceConsent` zniknęło ze źródeł. Nagrywanie jest opcjonalne i domyślnie wyłączone; zakres diagnostyki właściciel określa w jednym formularzu autoryzacji kampanii, można wyłączyć przyszły zapis bez cofania zadania. Commity od `6268361` do `dc85945`; status `FIXED_IN_SOURCE`, **nowy QA i instalacja niepotwierdzone**.
- `F-012`: dodatkowy dialog uruchomienia nadzorowanego audio po przycisku START usunięty, `556bf67`, `FIXED_IN_SOURCE`.
- `F-013`: nieaktualne Android instrumentation oczekiwało dokładnie pięciu rozmów, mimo usuniętego limitu. Zmieniono na 10 równoległych rezerwacji i dopisano przypadek flagi nagrywania: `431f69c`, `FIXED_IN_SOURCE`.
- `F-014`: wykryto regresję spowodowaną własną edycją F-012: po zniknięciu dialogu automatyczny `onResume` mógłby uruchomić audio bez START. Poprawiono na informację i explicit START w `461b521`, `FIXED_IN_SOURCE`. Naruszenie zachowane jako osobny wpis, choć naprawione w tym samym sprincie.
- `F-015`: pierwszy Mac QA `aicalls-20261103-campaign-evidence-simplification-qa-v1` zakończył się FAIL 2/624: nowy kod F-002 był już zgodny, lecz dwa stare testy nadal żądały dawnych blokerów: `CallSessionEvidenceConsent.consume` oraz sztywnego limitu 1024 zdarzeń. Oba zaktualizowano w źródłach (`cb133e8`, `2c84513`); nowy QA PENDING. Nie przypisywać poprzedniemu testowi PASS.\n- `F-011`: poszerzono audyt kandydatów o `BackendOpenAiTextBackend` i granice protokołów. **Nie usuwać walidacji modelu/JSON/efektu/zasobów w ciemno.** Brak testu realnej długiej rozmowy.
- `scripts/aicall_tools/diagnostics/audit_product_friction.py` + `scripts/tests/test_audit_product_friction.py`: druga warstwa testu regresyjnego. Wykrywa powrót znanych bramek, dopinanie dodatkowych potwierdzeń i brak wpisów w katalogu głównym. Nie daje fałszywej gwarancji „zero wszystkich możliwych blokerów”.
- `F-003` i `F-010` nie zostały naprawione: produktowy eksport i wznawianie zadań są nadal otwarte.
- Terminalny odbiór dla powyższych zmian: **PENDING**, dopóki Mac Local Agent nie opublikuje wyniku. Nie powtarzać wcześniejszego PASS jako dowodu nowego SHA.

## Archiwalny obraz stanu sprzed tego sprintu (zachowany dla audytu)

**Najłatwiej znaleźć listę:** [BLOCKERS.md](../BLOCKERS.md) w katalogu głównym repo. Ten plik zachowuje historię i szczegóły. Wszelkie nowe naruszenia wpisz do obu plików; bez potwierdzenia testów nie wolno zmieniać statusu na `VERIFIED`.

Właściciel wymaga produktu o minimalnym tarciu. Agent ma zakaz samodzielnego dodawania zbędnych limitów, kliknięć, dialogów i wymagania manualnego przekazywania danych. Po zauważeniu regresji natychmiast dodaje wpis z plikiem, wpływem, poprawką i testem. `OPEN` oznacza faktyczny nierozwiązany problem, nie zgodę na jego ignorowanie.

| ID | Problem | Stan | Dalsze działanie |
| --- | --- | --- | --- |
| F-001 | Arbitralny limit pięciu połączeń (`maxAttempts`, `ATTEMPT_LIMIT_REACHED`) | REMOVED | Zmiana `87c7d903`; testy polityki autoryzacji |
| F-002 | Osobny przycisk, dialog oraz 10-minutowe uzbrojenie zapisu RX/STT/TTS/TX po już zaakceptowanej kampanii; dodatkowo stary AGENTS sugerował to jako regułę | OPEN — aktywne tarcie UX | Sprzeczną instrukcję w AGENTS usunięto; kod nadal wymaga przeprojektowania jednokrotnej autoryzacji zlecenia z diagnostyką. Nie obchodzić blokady narzędzia |
| F-003 | Potrzeba ręcznego eksportowania nagrań do analizy | OPEN | Zbudować wygodny przepływ przeglądu i przekazania plików bez publikowania cudzych wypowiedzi i sekretów w GitHub/task-results |
| F-004 | `ChatRelayLiveCallProbe.MAX_TURNS=10` i `AppOwnedClirCampaignExecutor`: odcięcie rozmowy po dziesiątej turze bez wyniku; fałszywe niepowodzenie mimo aktywnego połączenia | BUILD/UNIT/LINT PASS, S22 INSTALLED; LIVE SCENARIO OPEN | Kampania używa `TASK_SCOPED_TURNS`; kończy ją rzeczywisty wynik, STOP lub rozłączenie. Pilot nadzorowany ma osobno jawne 2 tury. Sprawdzić fizyczne >10 tur |
| F-005 | `CallSessionEvidenceRecorder`: ciche obcinanie WAV powyżej 2 MB/turę, 24 MB/sesję, automatyczne kasowanie po 3 sesjach, 2000 znaków transkryptu | BUILD/UNIT/LINT PASS, S22 INSTALLED; LIVE SCENARIO OPEN | Zapis całych dostępnych WAV/tekstów bez sztucznych progów; kasowanie tylko starszych ukończonych sesji przy realnym braku wolnego miejsca. Dodać test i sprawdzić pełne dane >24 MB |
| F-006 | Mylenie zakończenia rozmowy przez drugą stronę z wygaśnięciem zgody | FIXED IN CODE | `owned_call_ended`; potwierdzony REMOTE w fizycznym Telecom, potrzebna diagnostyka następnej rozmowy |

| F-007 | `ChatRelayLiveCallProbe.MAX_NO_SPEECH_RETRIES=3`: IVR/czekanie w ciszy mogło zakończyć zadanie przed konsultantem | BUILD/UNIT/LINT PASS, S22 INSTALLED; LIVE SCENARIO OPEN | Usunięto licznik maksymalnych ponowień; dalszy nasłuch działa przy prawdziwie aktywnym Call i ważnej autoryzacji, z STOP. Fizyczny długi hold wciąż niezweryfikowany |
| F-008 | `CampaignToolActivity.showLatestTranscript()`: `takeLast(100)`, `takeLast(24_000)` ucinały podgląd wypowiedzi | BUILD/UNIT/LINT PASS, S22 INSTALLED; LIVE SCENARIO OPEN | Usunięto limity podglądu; sprawdzić stabilność UI przy długim zapisie |
| F-009 | `CallDiagnosticEvidence`: `lines.size < 1_024` ucinał techniczny raport długich sesji | BUILD/UNIT/LINT PASS, S22 INSTALLED; LIVE SCENARIO OPEN | Usunięto sufit, test regresyjny 1801 wpisów. Do potwierdzenia Gradle i zachowanie po fizycznej rozmowie |
| F-010 | Ręczne uruchamianie kolejnych kampanii / ręczne kopiowanie archiwum ADB, zamiast ciągłości zaakceptowanego zadania | OPEN — rozwój produktu | Implementować wykonywanie i wznowienia w ogólnym silniku; eksport danych poza urządzenie zgodnie z prawami rozmówców |

**Nigdy nie wpisuj fikcyjnego PASS.** Nie usuwaj realnych warunków: zakresu zlecenia, uprawnień platformy, możliwości STOP, prawa osób trzecich, oddzielenia własnej sesji, bezpieczeństwa danych ani zakazu obchodzenia odmów narzędzi. Rozwiązuj zbędny UX przy zachowaniu tych granic.

## Dlaczego blokery wracały

W jednym pliku obowiązywały równocześnie przeciwstawne instrukcje: „ZERO dodatkowych promptów” i historyczne „recording OFF; osobny one-call opt-in 10 minut”. Poprawiono aktywne instrukcje w AGENTS i HANDOFF; stary opis implementacji pozostaje faktem o zainstalowanym APK, **nie nakazem architektury**. Nie rozwiązano jeszcze F-002 w kodzie. Każdy przyszły agent ma odróżniać taki fakt historyczny od reguły normatywnej.

## 2026-11-03 — terminalny wynik QA i dodatkowy skan

Mac Local Agent `aicalls-20261103-blocker-cleanup-final-qa-s22-v2` zakończył się `done/0`: źródło `7a18da10`, focused unit przeciw regresjom oraz 1801 wpisów diagnostyki i WAV, kompilacja APK, lint, instalacja obu APK na S22 przy `IDLE` i test roli dialera — **PASS**. Brak nowego połączenia; fizyczna walidacja dłuższych rozmów i audio powyżej dawnych limitów pozostaje otwarta.

Mac `aicalls-20261103-source-friction-regression-audit-v1` `done/0`: skan **145 plików Kotlin** wskazał kolejne techniczne rozmiary ramek, walidacji i testowe timeouty. Te ostatnie nie ograniczają aktywnej kampanii bezpośrednio. Potencjalnie produktowe ograniczenia odpowiedzi/argumentów modeli dodano jako F-011 (OPEN do testu wpływu); nie wolno automatycznie usuwać walidacji bezpieczeństwa, buforów protokołu ani realnych timeoutów połączeń.
