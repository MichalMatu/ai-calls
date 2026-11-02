# AI Calls — handoff: autonomiczny lokalny agent telefoniczny

## Najnowszy sprint 2026-11-03 — rzeczywisty kod, nie sama dokumentacja

Na `main` wdrożono kod STOP dla nadzorowanego audio i kampanii, bez globalnego rozłączania cudzych połączeń; wprowadzono idempotentną kontrolę zwalniania własnego uchwytu, włączono lokalną Gemmę w pilocie i usunięto zdublowany sprawdzian kontrolera w UI START. Commity: `4c47c308` → `30d2634` → `4b79a302`. Mac `aicalls-20261103-stop-gemma-build-s22-install-v1` DONE/0: Kotlin focused PASS, assembleDebug/lintDebug PASS, dokładny S22 `SM-S906B/RFCT70L7E8J` IDLE, instalacja APK PASS **dla źródła 4c47c308**. Zadanie `aicalls-20261103-stop-final-regression-s22-v1` na późniejszy `4b79a302` musi zostać sprawdzone z dokładnego terminalnego wyniku (bez zgadywania). S22 przejściowo raportował w historii `MODE_IN_COMMUNICATION` mimo przełączeń `com.android.server.telecom`; przyczyna podczas żywej rozmowy nie jest potwierdzona. `CampaignCallControllerRegistry` nadal nie ma backendu exact-call; nie deklarować autonomicznego dial/hangup PASS. Szczegóły i pliki/linie: [checkpoint 2026-11-03](checkpoints/2026-11-03-stop-friction-sprint.md).

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
