# AI Calls — kolejne sprinty M2 → M3 → M4, checkpoint 2026-11-04

## Źródło, bezpieczeństwo, niezmienniki

- Start `main`: `1f5eb6c4dd55944f0f51531238f299898f57e2b0` (M1 zamknięty off-call). Kolejne commity na `main` `[skip ci]`, bez branchy, Codex/Work/Actions, force-push.
- Brak zmian Samsung/Shizuku PCM RX/TX, Call grant, prompt-skills, STOP, ACK, VAD i czasów końca tury. F-049 wycofany. Brak samowolnych połączeń.
- M1 nadal ostatni **realnie zainstalowany i zweryfikowany off-call** APK, z kodu `a616795e8dc9ec46f049ea700d91f6d97abc9ac6`. Nie mylić Mac QA z dowodem na S22.

## M2 — Parakeet TDT PL

- `a0d0000bd77f569d4151a1bc45d3c79163cc7627`: MediaPipe `tasks-audio:1.2.0` i `AudioTranscriber` z plikiem `parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm` ~615MB CC-BY-4.0, typowany PCM16→float, worker i nieblokująca kolejka, onReady/final/error/STOP, wybór w UI i gotowość zależna od modelu.
- `aicalls-20261104-audio-m2-parakeet-host-qa-v1` **DONE/0 PASS** (Gradle JVM/AndroidTest APK/lint, 248 Python, anti-friction).
- `aicalls-20261104-audio-m2-s22-model-readonly-v1` **FAILED/125 bez wykonania**: `task_budget_exhausted` (`required_budget_seconds=180`, `task_timeout=180`), to nie pomiar modelu. Task v2 z poprawionym budżetem publikowany, jego rezultat musi zostać odczytany osobno. Prywatne pobranie dużego modelu na Mac zlecone taskiem `aicalls-20261104-audio-m2-parakeet-mac-model-cache-v1`; samo zgłoszenie nie dowodzi pobrania ani instalacji.
- **S22 native readiness, pamięć i latencja — OPEN.** Nie twierdzić, że model jest zainstalowany.

## M3 — Piper PL

- `a201d18` → `1a15f1b` → `865b1f3`: jawnie powiązana usługa systemowa Android TTS `dev.ihorshevchuk.piper`, lokalny polski głos, resampling PCM16/16k i wspólne bramki zgody na tekst/TX; niezależny Quick ACK używa wybranego głosu bez zmiany harmonogramu.
- Pierwszy pinned Mac `aicalls-20261104-audio-m3-piper-host-qa-v1` **FAILED/1**, Kotlin: brak `)` w `LocalTtsSpeechOutput` i niedostępny publicznie `currentEngine`. F-052 natychmiast wpisany do BLOCKERS, poprawka `8037eaa402f2a667c1a34401aa4132e782d60a47`: konstruktor poprawny, wybór usługi tylko z publicznego `getEngines()` i głosu offline z `piperVoice`, nigdy milczącego fallbacku.
- Mac `aicalls-20261104-audio-m3-piper-postfix-host-qa-v2` **DONE/0 PASS** całego `scripts/verify_host.sh`, 248 Python, anti-friction.
- **Piper APK/pl-PL voice on S22 i fizyczna synteza offline — OPEN.** Brak bundlowania GPL silnika do AI Calls.

## M4 — Gemma Audio Direct

- `fc91c17705f69969f20642904bd501d73b6344a3` → `cce18b1a49d3b885d8fb80303cddc9db6f3f1a4a`: eksperymentalny, izolowany PCM16 16k mono→WAV→`Content.AudioBytes` LiteRT-LM, `audioBackend=Backend.CPU()`, worker, cancel, do 30 s, testy WAV; adapter podłączony do portu input, ale **profil nadal zablokowany** do kwalifikacji S22. Obecnie odczytuje audio do tekstu, nie jest to jeszcze docelowy multimodalny backend dialogu zachowujący jedną natywną konwersację per-call.
- Mac `aicalls-20261104-audio-m4-gemma-audio-host-qa-v1` zlecony dla `fc91c177`; rezultat **QA_PENDING** dopóki faktycznie opublikowany. S22 native RAM/GPU/30s/inferencja/STOP **OPEN**.

## Niedostępne urządzenie i warunki dalsze

- Mac `aicalls-20261104-audio-m3-m4-s22-readonly-runtime-inventory-v1` **FAILED/1**: `adb: device 'RFCT70L7E8J' not found`. Nie sprawdzono Piper, Gemma modelu ani Parakeet na S22, nie instalowano nowych APK.
- Po odzyskaniu ADB: exact model+serial, 2x IDLE, rola dialera, brak aktywnej kampanii/retry, porównanie grant/skills/audio prefów SHA-256 przed/po. Dopiero po pinned full Mac QA PASS instalacja off-call oraz osobne native STT, TTS i Gemma Audio benchmarki na tym samym S22. Bez rozmowy.
- OPEN niezależne: F-045 (semantyczny RAW release), F-047 (RX_INTERRUPTED po hangup), F-048 (dozwolone fakty IdentityVault) i zewnętrzny sukces CLIR; F-049 wycofany.

## Aktualizacja QA i modelu po pierwszym checkpoint

- M4 Mac `aicalls-20261104-audio-m4-gemma-audio-host-qa-v1` **DONE/0 PASS** na `fc91c177`; combined M2/M3/M4 Mac `aicalls-20261104-audio-m2-m4-combined-final-host-qa-v1` **DONE/0 PASS** na `cce18b1`. Oba obejmują Gradle test/build/lint/AndroidTest APK, 248 Python, anti-friction. Native audio na telefonie nadal niepotwierdzone.
- Mac `aicalls-20261104-audio-m2-parakeet-mac-model-cache-v1` **DONE/0**: model Parakeet pobrany do prywatnego katalogu, **614 588 752 B**. Mac `aicalls-20261104-audio-m2-parakeet-model-identity-v1` **DONE/0**: SHA-256 `66c5a74cf6c2c1d2b990901f6cece5bb53e3214dbceafa0989900a6f0eeb3504`, immutable HF revision `1f78e5eb6175b1b2cce35f16bcf9e2027b83e8bd`, CC-BY-4.0. Nie jest to instalacja na telefon.
- `bf79f71`: exact size/hash/revision pinned w `ParakeetModelFile`, worker sprawdza model **przed natywną inferencją**. `cb2afd1`: HTTPS exact revision downloader ze sprawdzeniem redirect host/size/hash/miejsca, prywatnym plikiem tymczasowym i atomic rename; `c06de45`: jawny przycisk pobierz/anuluj w osobnym UI, informacja 615 MB. Dokładny Mac QA `aicalls-20261104-audio-m2-pinned-downloader-host-qa-v1` ma jeszcze status **QA_PENDING**; nie twierdzić PASS przed receipt.
- S22 model readonly task `aicalls-20261104-audio-m2-s22-model-readonly-v2` **FAILED/1** `adb: device 'RFCT70L7E8J' not found`. Nie instalowano APK, nie testowano natywnego Parakeet/Piper/Gemma Audio i nie inicjowano rozmowy.

## Fizyczny sprint po ponownym podłączeniu S22 — finalne dowody 2026-11-04

1. **ADB powróciło**: `aicalls-20261104-audio-s22-reconnected-preflight-v1` DONE/0 PASS, exact `SM-S906B`/`RFCT70L7E8J`, dwa odczyty `IDLE`, rola dialera, brak aktywnej kampanii i retry, czytelny grant; Piper service package **ABSENT**. Bez zmian urządzenia.
2. **Pierwsza instalacja / RED test:** `aicalls-20261104-audio-m2m4-s22-install-baseline-v1` FAILED/30. Oba APK zostały poprawnie zainstalowane po ścisłym preflight, ale `AndroidAudioEngineProfileStoreContractTest` błędnie zakładał, że zintegrowany Parakeet wciąż musi być blokowany (`expected null`, faktycznie zintegrowana preferencja). Incydent F-053 dopisany do jedynego rejestru `BLOCKERS.md`.
3. **RED→GREEN F-053:** commit `0d0fdfe22e9b002c8a9dc557ad5d95a882aa5976`, test ma odrębne przypadki dopuszczonej preferencji Parakeet i zablokowanej Gemma Audio Direct. `aicalls-20261104-audio-native-contract-host-qa-v1` DONE/0 PASS dla pierwszego kodu testów `e5b2e7a`; `aicalls-20261104-audio-f053-host-qa-v1` DONE/0 PASS dla `0d0fdfe` (Gradle build, unit, lint, AndroidTest APK, 248 Python, anti-friction).
4. **Wgranie i native baseline:** `aicalls-20261104-audio-m2m4-s22-install-baseline-v2` DONE/0 PASS pinned `0d0fdfe`: exact S22 i 2× IDLE, brak aktywnego campaign/retry, grant+skills+audio profile SHA-256 sprzed/po bez zmian; obie paczki APK zainstalowane, `S22_STORE_PASS=true` (6 testów), `S22_MENU_PASS=true`, `S22_GEMMA_4_TURNS_PASS=true` (cztery natywne syntetyczne tury RAW), końcowy IDLE. **Nie inicjowano połączenia.**
5. **Model Parakeet na telefonie:** `aicalls-20261104-audio-parakeet-s22-model-transfer-v1` DONE/0 PASS: z prywatnego Mac cache 614 588 752 B, potwierdzony SHA-256 `66c5a74cf6c2c1d2b990901f6cece5bb53e3214dbceafa0989900a6f0eeb3504` po transferze, atomowe przeniesienie do app-owned external models, 2× IDLE, grant/skills/audio profile niezmienione.
6. **M2 native:** `aicalls-20261104-audio-parakeet-s22-native-acceptance-v1` DONE/0 PASS: fizyczny MediaPipe AudioTranscriber init+prewarm i 5 s sztucznej ciszy PCM16/16k, natywny test PASS, `MemAvailable` przed 3 403 144 kB / po 3 485 652 kB (to **nie** pomiar szczytu RSS), final IDLE i prefs bez zmian. Prawdziwa jakość polskiego STT, timestampy/final text i jednoczesny model Gemma **NIEPOTWIERDZONE**.
7. **M4 native:** `aicalls-20261104-audio-gemma-direct-s22-native-v1` DONE/0 PASS: eksperymentalny LiteRT CPU audio backend + `Content.AudioBytes`, syntetyczny PCM16, natywny test PASS, `MemAvailable` przed 3 271 512 kB / po 3 690 168 kB (nie maksymalny RSS), IDLE i grant/skills/profile bez zmian. To **nie** potwierdza gotowego wieloturowego speech-to-speech i M4 wciąż niedostępne w registry.

**Końcowy status:** M1 PASS (Mac/S22 off-call), M2 SOURCE/MAC/S22 synthetic native PASS (polski ASR quality OPEN), M3 SOURCE/MAC PASS (Piper APK i polski głos S22 absent/OPEN), M4 EXPERIMENTAL SOURCE/MAC/S22 synthetic native PASS (full dialogue/open). F-053 VERIFIED na Mac+S22 off-call; F-052 nadal native Piper QA_PENDING, F-045/047/048 i zewnętrzny efekt CLIR OPEN, F-049 wycofany. Nie uruchamiano dzwonienia, nie zmieniono grantów ani Samsung/Shizuku RX/TX, VAD/ACK.

## Kontynuacja jakości polskiego ASR (najnowszy kod po natywnym S22 PASS)

- Commit `7d1057f8e34a9e1ee4f80cd207983bc93132de33`: test `AndroidParakeetPolishRoundTripTest` wykorzystuje lokalny Android TTS do wygenerowania zmyślonej polskiej wypowiedzi, podaje prawdziwe PCM16 16 kHz przez `ParakeetSpeechInputProvider`, sprawdza tekst ASR i minimalną zgodność słów, zapisując tylko liczniki/opóźnienia (nie transkrypt). To test polskiej jakości ASR, a nie połączenie.
- Mac Host Ops `aicalls-20261104-audio-polish-roundtrip-host-qa-v1`: zlecony pinned source, **QA_PENDING** do odczytu terminalnego receipt. Nie przedstawiać nowego kodu jako fizycznego PASS do czasu testu. Pomiędzy `0d0fdfe` a `7d1057f` są tylko dokumenty i nowy `androidTest`; `app/src/main` jest identyczne. Po Mac PASS wystarczy instalacja APK testowego pod pełnym S22 preflight, bez nadpisywania kodu aplikacji.
