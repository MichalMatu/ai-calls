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
