# AI Calls — checkpoint i handoff, 14.11.2026

> **ARCHIWUM — bez aktywnych zadań.** Plan: [PROJECT](../../docs/PROJECT.md). Dawne PHYSICAL_OPEN/S2–S7 nie ustalają priorytetów; nowsze wyniki: [checkpoint 15.11](CHECKPOINT_HANDOFF_2026-11-15.md).

**Repo:** `MichalMatu/ai-calls`, `main`. Kod produkcyjny sprawdzony na hostcie: `ce05a593b0d77b869a8ef21b6ef24b66f8694cf4`. Testy instrumentacyjne z poprawionymi kontraktami: `50234e953a97b91f38aed6243f3ea6cc4bcc428a`. Commit tego checkpointu zmieni HEAD bez zmiany APK. To archiwalny snapshot stanu kodu, a nie instrukcja wznowienia.

## Potwierdzone automatycznie

- Mac `macbook-michal` przez Local Agent (`MichalMatu/local-agent`, `agent-control`, instalacja `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`), checkout `/Users/michal/agent-workspace/repos/ai-calls/work`.
- Host preflight, pełne testy 6 modułów, lint, testy Python, gate architektury audio, laboratorium Gemma Audio, budowa aplikacji i testowego APK: **PASS** na `ce05a593`.
- JUnit: **1311 testów**, 0 errors/failures/skipped. Python: **438 testów PASS**, plus 2 w laboratorium Gemma; `GEMMA_AUDIO_HOST_LAB=PASS`.
- APK debug: `245117515` bajtów, SHA-256 `0943c45a37045525bccb927dea14fd0f1c5aeaad0acbfb435ca764e040460f6d`.
- APK androidTest (końcowe): SHA-256 `671619362191331532ab9d001515a5c97d224dc863cb316f7999bbc080ece1a9`.
- Receipt hosta: `MichalMatu/local-agent` / `agent-control` / `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/task-results/aicalls-host-final-all-tests-20261114-v4.json`.
- W ramach sprintu uzupełniono 6 angielskich komunikatów w `values-en/model_voice.xml` i naprawiono limit dokumentacji; nie zmieniano konfiguracji telefonu.

## Emulator

**PASS: 29/29** bezpiecznych testów instrumentacyjnych na wyizolowanym `medium_phone` / `emulator-5586`: profile audio, ustawienia głosu, nawigacja, polski UI, STOP, szyfrowany magazyn i syntetyczne podglądy Lab. 0 failures, 0 errors. Testowy APK zbudowano z `50234e95`. Emulator uruchomiony i zamknięty bez wycieku procesu; prywatnego S22 nigdy nie podłączono ani nie wskazano przez ADB. Receipt: `MichalMatu/local-agent` / `agent-control` / `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/task-results/aicalls-emulator-safe-instrumentation-20261114-v4.json`.

## Tor audio i jakość

- Android STT → Gemma 4 E2B → Android TTS PL: bazowy punkt odniesienia.
- Gemma Audio Direct → Gemma 4 E2B → Android TTS PL: jedno rzeczywiste połączenie użytkownika działa jako MVP, ale brak pełnej kwalifikacji; poprawki P1 (limit przechwytywania ~29 s, timeout 90 s, odzyskiwanie błędnej transkrypcji) i wyścig STOP wymagają finalnej weryfikacji na S22.
- Parakeet PL: kod/testy obecne, natywna gotowość i jakość na S22 niepotwierdzona. Piper: silnik może być zainstalowany, lecz **polski głos offline niepotwierdzony**. Nie uznawaj brakujących pakietów za zaliczone testy. Kokoro/Pocket TTS niekwalifikowane dla polskiego.
- Rejestr `benchmarks/audio/AUDIO_ROUTE_QUALITY.md`, implementacja `scripts/aicall_tools/diagnostics/audio_route_quality_report.py`. WER, latencje, naturalność, przerwania i zakończenie połączenia wymagają prawdziwych pomiarów; testów syntetycznych nie mieszaj z rozmowami.

## Historyczny brak odbioru GSM

W dniu checkpointu nie potwierdzono pełnej rozmowy GSM (długi RX, barge-in, STOP, zakończenie i odsłuch). Ten zapis nie jest sprintem ani backlogiem. Zadania są wyłącznie w [PROJECT](../../docs/PROJECT.md).
