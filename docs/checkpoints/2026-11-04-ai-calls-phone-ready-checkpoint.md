# AI Calls — kompletny checkpoint przed powrotem S22, 2026-11-04

**Cel następnego okna:** bez powtarzania zakończonych audytów/testów uruchomić nowy, bezpieczny test polskiego ASR na dokładnym Samsungu S22+ i zebrać prawdziwe metryki M2/M3/M4. Ten dokument i [HANDOFF_NEXT_CHAT](../HANDOFF_NEXT_CHAT.md) są jedynym aktualnym *punktem startu*; starsze checkpointy są **historyczne**. Wszystkie operacje związane z telefonem w tej sesji dokumentacyjnej były **zakazane i nie zostały wykonane**.

## Tożsamość i stan instalacji

| Element | Prawdziwy, dotychczas potwierdzony stan |
|---|---|
| Repo | `MichalMatu/ai-calls`, `main`; **pre-documentation HEAD** `e1d9d694b93d6700934806341ec0e85c46b6030e`. Po konsolidacji odczytać świeży HEAD — nie traktować tej wartości jako docelowego SHA |
| Wykonanie | **GitHub Connector + Mac Local Agent**. Żadnego Codex/Work/GitHub Actions (również zapytań), nowych branchy, force-push. Direct `main` `[skip ci]` |
| Host | `macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`; wcześniej zaakceptowany Local Agent 4.20.16; jego status w chwili nowego czatu sprawdzić osobno |
| Kolejka | `MichalMatu/local-agent:agent-control`, `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/tasks/<id>.json` → `task-results/<id>.json`; **nie** stara kolejka projektowa `ai-calls:agent-control` |
| Telefon | Samsung Galaxy S22+ `SM-S906B`, rzeczywisty serial `RFCT70L7E8J`, app `pl.michalmatu.aicallbridge`. Aktualnego połączenia/IDLE nie da się zagwarantować bez świeżego ADB |
| Produkcyjny APK na S22 | Source `0d0fdfe22e9b002c8a9dc557ad5d95a882aa5976`; poprzednie pełne off-call STORE 6/6, menu PASS, 4 syntetyczne tury Gemma PASS |
| Diff do pre-documentation HEAD | `0d0fdfe` → `e1d9d694`: **brak zmian produkcyjnych** w `app/src/main`, `audio-bridge/src/main`, `privileged-helper/src/main`, `realtime-client/src/main`, `app/build.gradle`; zmiany tylko testy, narzędzia i dokumentacja |
| Model M2 | `/sdcard/Android/data/pl.michalmatu.aicallbridge/files/models/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm`, **614 588 752 bytes**, SHA-256 `66c5a74cf6c2c1d2b990901f6cece5bb53e3214dbceafa0989900a6f0eeb3504`. Transfer i hash na S22 PASS; nie kopiować modelu ponownie bez potrzeby |
| M3 Piper | `dev.ihorshevchuk.piper` **ABSENT w ostatnim audycie S22**, więc native Piper PL OPEN; nie pobierać/instalować anonimowego APK |
| M4 | `GemmaAudioDirectInputProvider` native AudioBytes syntetyczne S22 PASS; **`GEMMA_AUDIO_DIRECT` nadal disabled**; nie robić cichego fallbacku ani pełnego żywego połączenia |

## Zakończone receipts — czytać, nie ponawiać automatycznie

**Mac:**
- `aicalls-20261104-hostonly-audio-qualitygate-final-postfix-v4`, pinned `2c2dac7f6ea51f85d1dc3fc4db4a5f3ad41c4774` → **DONE/0**: focused Parakeet PCM16 JVM, pełne Gradle test/unit/lint/build/AndroidTest APK, **10/10 testów mutacyjnych**, **258 Python**, audio static gate, anti-friction PASS. F-054/F-055 **VERIFIED Mac**. Wcześniejsze v1/v3 FAIL to zachowana historia RED i poprawione fixture/false-positive.
- `aicalls-20261104-audio-polish-roundtrip-host-qa-v1`, source `7d1057f8e34a9e1ee4f80cd207983bc93132de33` → **DONE/0**, test Android Parakeet Polish RoundTrip zbudowany; **brak uruchomienia na urządzeniu**.
- `aicalls-20261104-audio-m3-piper-postfix-host-qa-v2` → **DONE/0**, naprawa F-052 kompilacyjnie VERIFIED Mac, native Piper jeszcze OPEN.

**S22 (poprzedni sprint, tylko off-call):**
- `aicalls-20261104-audio-m2m4-s22-install-baseline-v2` → **DONE/0**, exact APK `0d0fdfe` zainstalowany, STORE **6/6**, menu PASS, Gemma 4 tury PASS, 2× IDLE, grant/skills/audio prefs niezmienione.
- `aicalls-20261104-audio-parakeet-s22-model-transfer-v1` → **DONE/0**, exact size/hash modelu i końcowy IDLE PASS.
- `aicalls-20261104-audio-parakeet-s22-native-acceptance-v1` → **DONE/0**, realna instancja MediaPipe Parakeet, prewarm i 5 s syntetycznej ciszy PCM PASS.
- `aicalls-20261104-audio-gemma-direct-s22-native-v1` → **DONE/0**, LiteRT AudioBytes z 1 s syntetycznego PCM PASS. Poprzednie liczby dostępnej pamięci before/after **nie są peak RSS**.
- Historyczna żywa Gemma RAW: **8/8** i **7/7** STT→Gemma→lokalne `WRITE_COMPLETE`. Wyniki operatora, CLIR, pełny test wielojęzyczny, słyszalność i naturalne WER nadal niepotwierdzone; transkrypty/WAV tylko prywatnie na Macu/S22.

## Procedura bezpiecznego wznowienia — test sprzętowy nr 1

**Najpierw**, tylko gdy użytkownik wróci i telefon znów jest dostępny:

1. Sprawdź świeży HEAD oraz `AGENTS.md`, `docs/HANDOFF_NEXT_CHAT.md`, `BLOCKERS.md`, bezpieczeństwo, aktualny `local-agent:main` AGENTS/HOST_CONTROL_PLANE/CURRENT_HANDOFF i dokładny `status.json` hosta. Nie resetuj daemona, nie uruchamiaj starych tasków ani testów, które mają już DONE/0 bez zmiany źródeł.
2. Mac Host Ops read-only: `scripts/local_agent/device_readiness.sh` / `s22_adb_discovery.py` (żywy USB lub mDNS, bez zapisanego IP). Potwierdź **ten sam** `SM-S906B`, `RFCT70L7E8J`, `pl.michalmatu.aicallbridge`, **2 oddzielne `IDLE`**, rolę dialera, brak aktywnego `AppOwnedCampaignService`, brak `CampaignContinuationStore` pending retry oraz brak aktywnej rozmowy lub kampanii. Jeśli warunek nie zachodzi — **bez instalacji**, nigdy nie kończ obcego Call.
3. Zapisz prywatnie fingerprint/SHA-256 niejawnych prefów: `campaign_authorization_grant_v1`, `campaign_continuation_v1`, prompt-skills, `audio_engine_profile_v1`; nie publikuj treści/grantu na GitHub. Potwierdź model Parakeet (ścieżka, dokładny SHA/rozmiar), zainstalowany produkcyjny APK/version i miejsce na test APK.
4. Zrób `git diff` / porównanie zmian w `app/src/main` względem `0d0fdfe`. **Jeśli produkcyjny kod i manifest/sygnały build nie zmieniły się**, nie aktualizuj `app-debug.apk`: zbuduj w czystym, przypiętym SHA tylko `:app:assembleDebugAndroidTest` i wgraj **`app-debug-androidTest.apk`**. Jeżeli nie pasują wersje lub sygnatury APK — zatrzymaj i wróć do źródłowego QA, nie wymazuj danych/dialer/grantu.
5. Przy ponownym `IDLE` wykonaj pojedynczą instrumentację (bez wybierania numeru):
   ```bash
   adb -s "$SERIAL" shell am instrument -w \
     -e class 'pl.michalmatu.aicallbridge.audioengines.AndroidParakeetPolishRoundTripTest#recognizesSynthesizedPolishSpeechWithLexicalOverlap' \
     pl.michalmatu.aicallbridge.test/androidx.test.runner.AndroidJUnitRunner
   ```
   Odczytaj terminalny wynik `OK (1 test)` / FAIL, zakończenie procesu, długość PCM, czasy syntezy/ASR i liczbę trafionych tokenów. To syntetyczne zdanie po polsku z lokalnego systemowego TTS i rzeczywisty native Parakeet, **nie WER z realnych rozmów**. Nie zapisuj wygenerowanego tekstu/transkryptu w zwykłym JSON/logach.
6. Po testach 2× `IDLE`, brak retry, kampanii, porównanie poprzednich fingerprintów preferencji i stanu aplikacji. Przy jakiejkolwiek regresji `FAIL/OPEN`, zbierz prywatny minimalny dowód i napraw z nowym RED→GREEN. **Żadne połączenie** podczas tego sprintu.

Nie założyć dostępności ADB teraz. Nie uruchamiać telefonu, nie instalować Piper i nie zmieniać profilu użytkownika podczas przygotowywania dokumentacji.

## Dalsza kwalifikacja po teście nr 1

1. **M2**: polska jakość na kontrolowanych sztucznych/prywatnych materiałach z prawidłową podstawą, WER/CER, duplikaty final segments, first/final latency, porównanie Android STT; równoczesny Parakeet + Gemma E2B na dokładnym S22 (RSS peak/termika/bateria). Natywny test ciszy nie stanowi dowodu rozpoznawania.
2. **M3**: tylko zaufane źródło zewnętrznego Piper APK i legalna licencja/polski głos offline, oddzielnie wymagany pakiet i `Voice.features` marker, test rzeczywistego PCM/16k i błędów bez fallbacku. Bez pakietu nie oznaczać jako PASS ani instalować podejrzanego artefaktu.
3. **M4**: osobna kwalifikacja kompletnego audio→tekst→Gemma dialogu na jednym owned Call, bez równoległych konfliktów pamięci, z pomiarem native RAM/GPU/termiki, bez naruszania STOP i autoryzacji; **disabled** dopóki testy nie przejdą.
4. **Autonomiczne efekty** (osobny etap): F-045 semantyczna zgodność RAW, F-047 RX_INTERRUPTED po hangup, F-048 kontrolowane fakty IdentityVault. **F-049 wycofane** (zwykłe dwuczęściowe powitanie). Nawet prawidłowy TX lub wybór modelu nie potwierdzają modyfikacji CLIR.

## Testy host-only i formalny koniec kolejnego sprintu

```bash
bash scripts/verify_host.sh
python3 scripts/aicall_tools/diagnostics/audit_audio_engine_quality.py
python3 scripts/aicall_tools/diagnostics/audit_product_friction.py
```

Host-only QA uruchomić ponownie wyłącznie po nowej zmianie kodu, nie przy samym wznowieniu dokumentacji. Po prawdziwym teście uzupełnić receipt GitHub Local Agent, `BLOCKERS.md` (jedyny rejestr statusów), `ROADMAP`, niniejszy checkpoint i handoff; PASS fizyczny odróżniać od hostowego. Nie trzymać surowego WAV, PII, numerów i transkryptów w commitach, task JSON lub zwykłym logu. Zabronione Codex/Work/GitHub Actions oraz samowolne połączenia. Freeze: [PHASE2D](../PHASE2D_FREEZE_2026-09-18.md).
