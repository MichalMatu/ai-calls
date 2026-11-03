# AI Calls — current handoff

**2026-11-03 latest QA correction:** Mac `aicalls-20261103-campaign-evidence-simplification-qa-v1` na starszym `556bf67` -> **FAILED, 624 testy/2 błędy**: historyczne asercje `CallSessionEvidenceConsent.consume` i `1024` zdarzeń. Poprawione w kolejnych commitach `cb133e8` i `2c84513`; nowe źródła wymagają świeżego QA. `F-015` w `BLOCKERS.md`.\n\n## 2026-11-03 — NOWY STAN: druga warstwa ochrony przed blokerami (źródła, QA niepotwierdzone)

Aktualna lista wszystkich naruszeń, statusów i klasyfikacja ważnych limitów: [BLOCKERS.md](../BLOCKERS.md). Nadrzędny zakaz: pierwsze linie [AGENTS.md](../AGENTS.md). `docs/FRICTION_BLOCKER_LEDGER.md` przechowuje **historię**, nie konkurencyjne statusy.

- Audyt Mac `aicalls-20261103-policy-code-deep-audit-v1`: `done/0`, 293 pliki źródłowe/skrypty, 1197 dopasowań kandydujących, nie 1197 potwierdzonych błędów.
- `F-002` poprawiony **w źródłach**: usunięte 10-minutowe uzbrojenie jednej rozmowy. W jednym jawnie zatwierdzanym zakresie kampanii użytkownik może opcjonalnie zaznaczyć lokalny zapis RX/TX/transkryptu (domyślnie OFF), a potem wyłączyć zapis kolejnych rozmów. Rozmówcy i ich prawa nadal wymagają właściwej obsługi.
- `F-012`: usunięty redundantny dialog w pilocie audio. `F-014`: naprawione niezamierzone auto-START przy powrocie z dialera. `F-013`: naprawiony stary test limitu pięciu prób.
- Mechaniczna druga warstwa: `scripts/aicall_tools/diagnostics/audit_product_friction.py`, testy Python i rozszerzone testy Kotlin/Android. **Nie uznawać ich za PASS bez terminalnego wyniku Mac**.
- Pozostałe `OPEN`: `F-003` eksport bez technicznego ręcznego przenoszenia, `F-010` pełne autonomiczne wznowienia, `F-011` realny wpływ limitów modelu/transportu. S22 dla tej nowej wersji nie został potwierdzony.
- Wpisy poniżej opisujące nagrywanie jako nadal wymagające osobnego uzbrojenia są **archiwalnym stanem poprzedniego APK**, nie aktualną regułą ani statusem nowego kodu.

## 2026-11-03 — usuwanie sztucznych limitów: build/test/lint/S22 PASS, realna długa rozmowa OPEN

### Druga korekta źródła — 2026-11-03

Mac task `aicalls-20261103-no-artificial-limits-qa-s22-v1` wykrył kompilacyjny błąd: `ChatRelayProbeActivity` nadal odwoływało się do usuniętego `MAX_TURNS`. Naprawiono przez `require(maxTurns > 0)`; nie ustanawia nowego maksimum. Usunięto również sztuczne zakończenie po trzech cichych oknach RX (F-007) oraz gubienie wpisów raportu po 1024 zdarzeniach (F-009). Test `CallDiagnosticEvidenceLongSessionTest` sprawdza 1801 wpisów. **Mac QA tej korekty DONE/0:** `aicalls-20261103-blocker-cleanup-final-qa-s22-v2`: build/unit/lint PASS, oba APK zainstalowane na S22, rola dialera PASS, bez nowej rozmowy.


- `F-004`: kampania nie ma arbitralnego stopu po dziesiątej turze; rozłączenie/STOP/autoryzacja i rzeczowe zakończenie pozostają.
- `F-005`: usunięto nieuzasadnione ograniczenia długości nagrania, limit 24 MB, limit trzech plików sesji i cięcie tekstu; odzyskiwanie miejsca wyłącznie pod presją rzeczywistej pojemności Androida.
- `F-008`: podgląd transkryptu pokazuje pełne wypowiedzi z pliku, a nie ostatnie 100 linii/24 tys. znaków.
- `F-002`: zgoda na nagrywanie pozostaje wymogiem obecnego kodu; **nie twierdzić, że została usunięta**. Wymaga zgodnego z prawem rozwiązania, nie obejścia odmowy narzędzia.
- `F-007`, `F-009`, `F-010`: niezamknięte, jawnie sklasyfikowane jako dług.
- Terminalny wynik Mac `aicalls-20261103-blocker-cleanup-final-qa-s22-v2` to DONE/0: compile/focused unit/lint i S22 install PASS. Nie jest to dowód rozmowy >10 tur.

Źródło prawdy: `BLOCKERS.md` + `docs/FRICTION_BLOCKER_LEDGER.md`. Zobowiązanie normatywne: pierwsze linie `AGENTS.md`.

## 2026-11-03 — aktualne, POTWIERDZONE S22 i niezamknięte tarcie UX

Fizyczna rozmowa po wyłączeniu Wi-Fi Calling: `MODE_IN_CALL`, pełny RX→STT→Gemma→TTS→TX dla **trzech tur** potwierdzone nowym raportem. Telekom pokazuje zakończenie po stronie zdalnej (`REMOTE`), nie wygaśnięcie grantu. Wyniku aktywacji CLIR nie potwierdzono.

Mac Local Agent `aicalls-20261103-private-duplex-transcript-ui-build-s22-v3` — **DONE/0**: build, skupione testy WAV, lint, oba APK zainstalowane, instrumentowany test roli dialera PASS. HEAD tego wdrożenia: `9f0d035`. Są dostępne: transkrypt osobno rozmówca/AI, lokalny odsłuch RX i TX, prywatne pliki WAV, ręczny eksport na Maca. Nie przeprowadzono jeszcze kolejnego fizycznego testu pełnych plików nagrania.

**F-002 NIEZAMKNIĘTY BLOKER UX:** implementacja nadal ma osobne kliknięcie i 10-minutowe uzbrojenie zapisu przed jedną sesją. Jest to **stan starej implementacji, NIE przyszła zasada ani norma AGENTS.md**. Zmiana nieautoryzująca automatycznie ukrytego nagrywania nie została zaakceptowana przez warstwę wykonawczą; nie obchodź tej odmowy przez inne narzędzie ani skrypt. Zaprojektuj autoryzację diagnostyki jako część jednego legalnego przepływu zlecenia, bez ciągłego powtarzania potwierdzeń.

NADRZĘDNA REGUŁA: `AGENTS.md` → „ZERO SAMOWOLNIE DODANYCH BLOKERÓW”; `docs/FRICTION_BLOCKER_LEDGER.md`. Nowa ręczna bramka, limiter, timeout użytkownika lub zależność od manualnego kopiowania logów bez konkretnej konieczności to regresja. Każdą natychmiast loguj w ledger i naprawiaj. Szczegóły techniczne: `docs/checkpoints/2026-11-03-duplex-audio-and-private-transcript.md`.

## 2026-11-03 — five-call attempt ceiling removed

Owner decision: eliminate the arbitrary maximum number of phone calls as a redundant blocker to repeated authorized testing. `maxAttempts` and `ATTEMPT_LIMIT_REACHED` removed from code and UI. Existing scoped grants migrate without renewal; old `max_attempts` preference is ignored, and `attemptsUsed` remains only an internal reservation marker (never a call limit).

Still enforced: exact `*100` / `SET_SERVICE(CLIR=true)` scope, default SIM, disclosure policy, expiry/revocation, Android role, device readiness, one active owned call, targeted STOP and effect commitment checks. One START initiates one call, not an automatic call loop.

Physical evidence: 2026-11-03 BT-off app-owned `*100` call and exact owned hangup PASS; Samsung media runtime blocked because Telecom used VoWiFi (`mUseIWLAN=true`, `MODE_IN_COMMUNICATION`). Owner has now disabled Wi-Fi Calling, but no new cellular-audio PASS has been confirmed yet. Source `fbcb53d` for early VoWiFi refusal built/unit/lint PASS and installed on S22 (Mac task `aicalls-20261103-wificalling-failfast-build-s22-v1`). Check current HEAD, IDLE, role, grant and VoWiFi on the next one-call acceptance.

## 2026-11-03 — historical dialing regression and real Telecom controller source

Git history proves a dial regression: before `6e732af`, `AppOwnedClirCampaignExecutor` used `ACTION_CALL` and generic `TelecomManager.endCall()`. Commit `6e732af` added a constant-false ownership check; `c48685b` introduced `CampaignCallControllerRegistry` without a provider; `a019795` separately removed ADB-script automatic dial/hangup. These were real removed paths, but the old hangup did **not** select one specific Call.

New code `0226d61` → `294cc2d` → `33e27cc` → `3ee183c` implements Android default-dialer-role-gated `InCallService`, `TelecomManager.placeCall`, exact matched `Call.disconnect()`, minimal human call controls and one-tap START continuation after real OS role/notification consent. This is **candidate production code**, not a live-call physical PASS. It does not mutate the Google Phone role without OS user selection.

Mac task `aicalls-20261103-controller-build-cache-adb-recover-v3`: **DONE/0**, `EXACT_CONTROLLER_BUILD_LINT_UNIT=PASS` and `APK_CACHE_READY=true` for `3ee183c`; device disappeared from ADB, so **not installed**. Separate `aicalls-20261103-cached-apk-last-s22-usb-diagnose-v2`: DONE/0, cached APK verified, `S22_TRANSPORT_NOT_AVAILABLE=true`, `NO_INSTALL=true`. Real role grant, live dial, RX/STT/Gemma/TTS/TX, selective live teardown, and `MODE_IN_COMMUNICATION` root-cause acceptance remain OPEN. Detailed checkpoint: `docs/checkpoints/2026-11-03-historical-dial-regression-controller-recovery.md`.


## Najnowszy sprint 2026-11-03 — rzeczywisty kod, nie sama dokumentacja

Na `main` wdrożono kod STOP dla nadzorowanego audio i kampanii, bez globalnego rozłączania cudzych połączeń; wprowadzono idempotentną kontrolę zwalniania własnego uchwytu, włączono lokalną Gemmę w pilocie i usunięto zdublowany sprawdzian kontrolera w UI START. Commity: `4c47c308` → `30d2634` → `4b79a302`. Mac `aicalls-20261103-stop-gemma-build-s22-install-v1` DONE/0: Kotlin focused PASS, assembleDebug/lintDebug PASS, dokładny S22 `SM-S906B/RFCT70L7E8J` IDLE, instalacja APK PASS **dla źródła 4c47c308**. Zadanie `aicalls-20261103-stop-final-regression-s22-v1` na `4b79a302` zakończone **DONE/0**: skupione unit/build/lint PASS, instalacja app i androidTest APK PASS, fizyczna instrumentacja dwukrotnego STOP AI na S22 **PASS**, końcowe `IDLE` **PASS**. Nie jest to test STOP podczas żywej rozmowy. S22 przejściowo raportował w historii `MODE_IN_COMMUNICATION` mimo przełączeń `com.android.server.telecom`; przyczyna podczas żywej rozmowy nie jest potwierdzona. `CampaignCallControllerRegistry` nadal nie ma backendu exact-call; nie deklarować autonomicznego dial/hangup PASS. Szczegóły i pliki/linie: [checkpoint 2026-11-03](checkpoints/2026-11-03-stop-friction-sprint.md).

## Nadrzędny cel produktu i decyzja właściciela — 2026-11-02

**AI Calls nie jest aplikacją do ręcznie nadzorowanych połączeń.** Docelowo użytkownik zleca zadanie lokalnemu AI, które samodzielnie dobiera zatwierdzony cel, dzwoni, prowadzi rozmowę (IVR/STT/Gemma/TTS), wykonuje autoryzowaną czynność lub zbiera informację, weryfikuje rzeczywisty wynik, rozłącza własne połączenie oraz zgłasza rezultat. Także wznowienie, ograniczone ponowienia i praca na danych własnego konta mieszczą się w celu. Aplikacja musi obsługiwać zwykłe, legalne numery wskazane przez użytkownika; Orange CLIR to przypadek testowy, a nie docelowa lista numerów.

**Właściciel udzielił szerokiego mandatu do autonomicznych prac inżynierskich:** odczyt repozytorium, zmiany kodu, testy, budowanie, instalację na zatwierdzonym S22, diagnozę ADB, zbieranie sanityzowanych wyników, naprawy, porządkowanie oraz kontynuowanie zatwierdzonych prób bez dodatkowego pytania o tę samą czynność. Nie przerzucaj logów, komend i drobnych decyzji technicznych na operatora. Jeden prosty ekran konfiguracji/zgody produktu jest preferowany zamiast wielokrotnych potwierdzeń.

**Nie jest to jednak bezwarunkowy, produktowy grant do dzwonienia na nieokreślone numery, zmian na cudzych kontach, wydatków lub ujawniania danych.** Rzeczywiste połączenie i skutek zewnętrzny muszą odpowiadać konkretnemu, ważnemu zakresowi zadania/autoryzacji w aplikacji, Androidowi i regułom prywatności. Zewnętrznych blokad narzędzi nie obchodź inną ścieżką. Zasady: [AGENTS](../AGENTS.md), [Autonomia](AUTONOMOUS_OPERATION_MODE.md), [Architektura](ARCHITECTURE.md), [Autoryzacja](GENERIC_PHONE_TASK_AUTHORITY.md).

## Ostatni zweryfikowany stan

- **Kod autonomicznego dialera NIE jest ukończony:** `AppOwnedClirCampaignExecutor` ma rejestr `CampaignCallControllerRegistry` bez zainstalowanego zweryfikowanego kontrolera konkretnej rozmowy. Globalny `TelecomManager.endCall()` nie identyfikuje własnego połączenia.
- **Fizyczny S22 PASS poza rozmową:** polskie STT/TTS, lokalna Gemma 4 READY, Shizuku, uprawnienia, fail-closed off-call i zapis diagnostyki.
- **Jeden przycisk nadzorowanego AI PASS na S22, tylko IDLE:** `aicalls-one-tap-supervised-s22-sprint-20261102-v1` **DONE/0**: build+lint, instalacja aplikacji i test-APK, instrumentacja naciśnięcia przycisku, prywatny raport `telecom_in_call=false`, `audio_mode=MODE_NORMAL`, `failure_reason=no_active_cellular_call`. To nie dowodzi realnej rozmowy.
- **Zbędny dialog „Attach” z `CampaignToolActivity.kt` usunięty:** commit `bf6597c`; Mac `aicalls-campaign-attach-one-tap-qa-20261102-v1` **DONE/0**, Android build+lint PASS. Nie oznacza to jeszcze ponownej instalacji APK zawierającego *tę* zmianę.
- **Global hangup ADB** `s22_call_control.py hangup --global`: warunkowe jedno `KEYCODE_ENDCALL`, ukierunkowany host unit PASS; fizyczne rozłączenie w rozmowie **NIEZALICZONE**. Oddzielny eksperymentalny `s22_single_service_smoke.py` jest niekwalifikowany; próba zlecenia realnego dial+cleanup została odrzucona przez zewnętrzny safety check.
- **Blokada audio jest realna:** zrzut w trwającej rozmowie pokazał `MODE_IN_COMMUNICATION`, podczas gdy aplikacja i Samsung/Shizuku wymagają `MODE_IN_CALL`. Bez równoczesnego pomiaru warstw i bez akceptacji RX/TX nie uznawaj tego za rozwiązane.
- **Realna automatyczna rozmowa → zadanie → weryfikacja → rozłączenie: OPEN.** Ani kod na GitHubie, ani samo `HOST_GREEN` nie jest fizycznym dowodem.

Dowody: [pre-call](checkpoints/2026-11-02-before-first-call-checkpoint.md), [S22 no-click](checkpoints/2026-11-02-s22-no-touch-call-readiness-installed.md), [global endcall](checkpoints/2026-11-02-s22-explicit-global-endcall-lab-boundary.md), [one button](checkpoints/2026-11-02-one-tap-supervised-single-button-sprint.md).

## Następny priorytet — nie redukować celu do ręcznego pilota

1. Odczytaj świeży `main`, `README.md`, `AGENTS.md`, [Autonomia](AUTONOMOUS_OPERATION_MODE.md), [Roadmap](ROADMAP.md), [Architektura](ARCHITECTURE.md), [Autoryzacja](GENERIC_PHONE_TASK_AUTHORITY.md), [Bezpieczeństwo](SECURITY_PRIVACY.md). Odczytaj aktualny stan Mac Local Agent i zamknięte task-results. **Nie powtarzaj zaliczonych testów bez regresji.**
2. Pracuj nad faktycznym autonomicznym połączeniem z powiązaniem celu/konkretnej sesji oraz bezpiecznym zakończeniem, zamiast traktować brak kontrolera jako definicję produktu. Zachowaj ważny zakres autoryzacji i kontrolę `IDLE`; nie deklaruj gotowości na podstawie globalnej flagi telefonu.
3. Ustal i napraw przyczynę `MODE_IN_COMMUNICATION` za pomocą pomiarów obu warstw podczas właściwie autoryzowanej, rzeczywistej rozmowy; nie fałszuj `MODE_IN_CALL` ani nie wyłączaj ochrony zamrożonego helpera bez dowodów.
4. Docelowa akceptacja: użytkownik zleca **jedno konkretne zadanie** lokalnemu AI, telefon sam wykonuje autoryzowaną próbę, realnie rozmawia, zdobywa wynik, rozłącza **właściwą** rozmowę, wraca do `IDLE`, generuje sanityzowany raport. W przypadku ograniczenia narzędzia wykonaj niezablokowane zadania i podaj dokładny bloker; nie próbuj go ominąć.
5. Pilot nadzorowany i jego pojedynczy przycisk pozostają tylko możliwością diagnozy. Jeśli fizyczny test wymaga udziału osoby po drugiej stronie, zapewnij stosowną zgodę na udział AI; nie obiecuj niepotwierdzonego odsłuchu.

## Tryb pracy

- Wyłącznie **GitHub Connector + Mac Local Agent**, bez Codex, Work i GitHub Actions.
- Repo `MichalMatu/ai-calls`, gałąź `main`; HEAD ustal z GitHub, nie ze starego handoffu.
- Host `macbook-michal`, instalacja `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`; kolejka `MichalMatu/local-agent:agent-control`. Sprawdź osobno status hosta i **dokładny receipt**. [Instrukcja](LOCAL_AGENT_HOST_FLOW_2026-11-01.md).
- Sprzęt: S22+ `SM-S906B`, serial `RFCT70L7E8J`, Android 16 / One UI 8, obecny dialer Google Phone. Przed zmianą APK sprawdź model, serial i `IDLE`. Nie zmieniaj roli dialera bez świadomego wyboru użytkownika.
- Dane w logach: tylko liczniki, stany i anonimowe metadane; nie kopiuj treści rozmów, numerów klientów, PCM ani sekretów do repo.

**Przypomnienie:** pełen produkt to autonomiczne zadanie telefoniczne. Ochrona uprawnień i danych jest częścią jakości aplikacji; ręczne kliknięcia i testy częściowe nie mogą być celem końcowym.
