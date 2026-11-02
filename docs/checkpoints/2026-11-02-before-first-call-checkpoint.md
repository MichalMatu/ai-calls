# AI Calls — finalny checkpoint PRZED PIERWSZĄ ROZMOWĄ

**Data:** 2026-11-02. **Cel kolejnego czatu:** jedna ręcznie zainicjowana, świadomie nadzorowana rozmowa testowa ze zgodną drugą stroną. Nie zaczynaj od ponownego debugowania STT/Bixby/USB/Gemmy.

## Stan kodu i środowiska

- Repo: `MichalMatu/ai-calls` / `main`. Ostatni zmieniający kod produkcyjny squash-commit: `1d58c51e6414363b3227fdbd05f2ec45dba9e9b0` (PR #22: listener STT przed checkSupport, obsługa błędów i ograniczony timeout). Późniejsze commity dotyczą dokumentacji. **Sprawdź świeży HEAD w nowym czacie**.
- APK `versionName=0.2.0`, debug z tym kodem zainstalowany przez `adb install -r` na rzeczywistym S22 z zachowaniem danych; test APK również zainstalowany.
- Samsung S22+ `SM-S906B`, ADB serial `RFCT70L7E8J`, Android 16 / One UI 8, lokalizacja interfejsu `en-GB`, aplikacyjne STT `pl-PL`; Google Phone `com.google.android.dialer` domyślny.
- Na telefonie potwierdzone: Shizuku Binder i uprawnienia, `RECORD_AUDIO`, Gemma 4 model READY, Google STT engine oraz pakiety `com.google.android.as` (Android System Intelligence) i `com.google.android.as.oss` (Private Compute Services) **ENABLED**.
- `mCallState=0` / `IDLE` przed i po wszystkich potwierdzonych bezrozmownych próbach. Bez rozmów, zmiany SIM, domyślnego dialera, CLIR, API kont czy zewnętrznych skutków.

## Dowody fizyczne — już PASS

| Obszar | Fizyczny wynik / terminalny receipt |
|---|---|
| Naprawa Google STT | `aicalls-s22-restore-google-as-oss-test-stt-20261102-v1` done/0 — po przywróceniu obu pakietów `LOCAL_STT_STARTUP_OUTCOME=ready` |
| Produkcyjna polska transkrypcja | `aicalls-s22-tts-to-stt-production-roundtrip-20261102-v1` done/0, `production_roundtrip_success=true`, dokładna polska transkrypcja z PCM16 16 kHz |
| Lokalny przepływ STT → tekst → TTS | `aicalls-s22-local-speech-text-tts-complete-20261102-v1` done/0, `text_pipeline_success=true` |
| Obsługa błędu usługi Google | `aicalls-s22-install-merged-stt-fix-20261102-v2` done/0: APK zainstalowany; `recognition_error_10` przekazany natychmiast, a nie timeout 40 s; później STT odzyskało sprawność po przywróceniu pakietów |
| Własna Gemma 4 i polityka mowy | `aicalls-s22-autonomous-nocall-gemma-ui-media-20261102-v1` done/0, instrumentacja `AndroidSupervisedGemmaEndToEndContractTest`: `OK (1 test)`, 14,38 s, lokalna inferencja i zatwierdzona wypowiedź |
| Shizuku / mikrofon / urządzenie | Ten sam receipt: `shizuku_binder_available=true`, `shizuku_permission_granted=true`, `record_audio_granted=true`, `live_call_readiness=true` |
| Media bez połączenia | Ten sam receipt: `production_media_off_call_smoke=PASS`, odmowa `AudioManager not IN_CALL`, przejścia `BINDING>PREPARING>STOPPING>FAILED>IDLE` |
| UI bezpieczeństwa | `aicalls-s22-pilot-no-call-button-rejection-20261102-v2` done/0: kliknięcie attach podczas IDLE → `Najpierw rozpocznij rozmowę...`; `SAFE_NO_CALL_ATTACH_REJECTION=PASS`, STOP nieaktywny |

Dokładne logi i granice testów: [checkpoint odzyskania STT](2026-11-02-ai-calls-s22-polish-stt-recovery.md) oraz [checkpoint pilota bez rozmowy](2026-11-02-ai-calls-autonomous-nocall-supervised-acceptance.md). **Nie powtarzać PASS bez zmiany kodu/konfiguracji.**

## Quality gate — pełny Mac Host Control

- Zadanie: `aicalls-20261102-precall-quality-gate-v1`, prywatne `MichalMatu/local-agent:agent-control`, Mac `macbook-michal`, instalacja `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`.
- Sprawdzane źródło `de648700bdd83abb3cf652532f78b75b5f2842dd` (ostatnie zmiany tylko dokumentacyjne).
- `scripts/verify_host.sh`: testy Android/JVM wszystkich modułów, lint wszystkich modułów, `app:assembleDebug`, AndroidTest APK, Python unittest, skany sekretów i zakazanych komend, `git diff --check`. Dodatkowo niedestrukcyjny S22 ADB/IDLE/dialer i kontrola czystości `git status`.
- **WYNIK: PASS — terminalny receipt `done/0`, `QUALITY_SCRIPT_EXIT=0`, `host_quality_gate_green=true`, `QUALITY_GATE_MAIN_PRODUCT_CODE=PASS`.** Trzy uruchomienia Gradle zakończone `BUILD SUCCESSFUL` (1m27s / 2m21s / 49s): testy jednostkowe JVM/Android czterech modułów, lint czterech modułów oraz oba APK. Python: `Ran 199 tests`, `OK`. Skan tajnych danych i zakazanych prymitywów połączeń: PASS; `SAFETY_STATIC_CHECK=PASS`, `CLEAN_CHECKOUT=PASS`, `WORKTREE_CLEAN_AFTER=PASS`. Fizyczny S22 obecny na USB ADB, `mCallState=0`, domyślny `com.google.android.dialer`, oba komponenty `com.google.android.as` i `com.google.android.as.oss` włączone. Bez instalacji APK i bez połączenia. Następne commity w tej sesji modyfikowały **jedynie dokumentację**; źródła aplikacji sprawdzone w quality gate nie zmieniły się.

## Realny test pozostaje OTWARTY

Nie udowodniono jeszcze, czy żywa rozmowa komórkowa dostarcza prawdziwe audio RX przez Shizuku do STT i czy druga strona słyszy TX z TTS/Gemma, ani czy STOP akustycznie zatrzymuje TX. Testy syntetyczne nie rozstrzygają tych granic.

**Sekwencja jednego testu w nowym czacie:**

1. Odczytaj świeży `main`, wynik powyższego quality gate, stan Mac Local Agent, dokładne S22 przez USB, telefon `IDLE`, Shizuku oraz obecność dwóch pakietów Google. Nie instaluj niepotrzebnie nowego APK (kod bez zmian).
2. Użytkownik wybiera **zgadzającą się osobę testową** i **ręcznie wykonuje** zwykłe połączenie w Google Phone. Bez sugerowania/odgadywania numeru; agent ani ADB nie dialują.
3. Użytkownik wraca do AI Calls → **Pierwsze połączenie — test nadzorowany** → świadomie potwierdza attach AI. Gemma READY, bezpieczny skrypt jest fallbackiem. Maksymalnie 3 neutralne tury; żadnych danych osobowych, zakupów, zleceń lub zmian usług.
4. Agent na bieżąco odczytuje bez ujawniania treści `chat-relay-live-call-report.txt`, liczniki `telephony_rx_pcm_bytes`, `turn_*_stt_chars`, `supervised_gemma_replies`/`supervised_script_fallback_replies`, `telephony_tx_pcm_bytes`, `failure_reason` i status. Nie publikuj surowej mowy, PCM, numerów ani prywatnych transkryptów.
5. Druga strona mówi, czy słyszy odpowiedź AI. Użytkownik naciska **STOP AI**: sprawdź cofnięcie autoryzacji do TX i brak dalszego PCM, rozmowa pozostaje pod kontrolą Google Phone; użytkownik **sam kończy** połączenie.
6. Końcowe `IDLE`, tylko faktograficzne PASS/FAIL, konkretny minimalny fix jeśli wada faktycznie wystąpiła. Bez nieuzasadnionego reaudytu całego repo.

## Nieprzekraczalne granice

- **Autonomiczny dial** (w tym Orange `*100` / CLIR) **NADAL ZABLOKOWANY** przez brak kontrolera własności dokładnego połączenia i selektywnego hangup. Nie zdejmuj zabezpieczenia `CampaignCallControllerRegistry`.
- Bez `TelecomManager.endCall()`, `KEYCODE_ENDCALL`, `ACTION_CALL`, zmiany domyślnego dialera/SIM, obejścia zgody UI. Użytkownik decyduje o numerze i bierze udział w akustycznej ocenie.
- Local Agent i GitHub służą do kodu, diagnostyki, odczytu liczników; **nie są product call executor ani authority owner**.
- Przed testem zapoznaj się z [AGENTS.md](../../AGENTS.md), [Handoff](../HANDOFF_NEXT_CHAT.md), [Security](../SECURITY_PRIVACY.md), [Autonomous mode](../AUTONOMOUS_OPERATION_MODE.md), [Roadmap](../ROADMAP.md). Bez Codex, Work, GitHub Actions.

## Porządki dokumentacji

W `main` odchudzono trzy aktualne punkty wejścia: `README.md`, `docs/ROADMAP.md`, `docs/HANDOFF_NEXT_CHAT.md`; usunięto z nich sprzeczne chronologie `latest` i stare stany Shizuku/STT. Historia jest zachowana w Git oraz istniejących checkpointach/sprintach — **nie kasować dowodów bezpieczeństwa**. Jedyny aktywny start nowego czatu: [HANDOFF_NEXT_CHAT.md](../HANDOFF_NEXT_CHAT.md).
