# AI Calls — checkpoint i handoff, 14.11.2026

**Repo:** `MichalMatu/ai-calls`, `main`. Stan źródeł zatwierdzony na hostcie: `ce05a593b0d77b869a8ef21b6ef24b66f8694cf4`. Dalsze commity dokumentacyjne mogą zmienić HEAD bez zmiany APK. Zaczynaj nowy czat od odczytu `AGENTS.md`, `docs/README.md`, `docs/PROJECT.md` i tego pliku; sprawdź HEAD, nie odtwarzaj testów bez powodu.

## Potwierdzone automatycznie

- Mac `macbook-michal` przez Local Agent (`MichalMatu/local-agent`, `agent-control`, instalacja `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`), checkout `/Users/michal/agent-workspace/repos/ai-calls/work`.
- Host preflight, pełne testy 6 modułów, lint, testy Python, gate architektury audio, laboratorium Gemma Audio, budowa aplikacji i testowego APK: **PASS** na `ce05a593`.
- JUnit: **1311 testów**, 0 errors/failures/skipped. Python: **438 testów PASS**, plus 2 w laboratorium Gemma; `GEMMA_AUDIO_HOST_LAB=PASS`.
- APK debug: `245117515` bajtów, SHA-256 `0943c45a37045525bccb927dea14fd0f1c5aeaad0acbfb435ca764e040460f6d`.
- APK androidTest: `2090981` bajtów, SHA-256 `5c3d947c9121fd777248e12279348124b4733791f29d8ebfa451069bf8c7d3e0`.
- Receipt hosta: `MichalMatu/local-agent` / `agent-control` / `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/task-results/aicalls-host-final-all-tests-20261114-v4.json`.
- W ramach sprintu uzupełniono 6 angielskich komunikatów w `values-en/model_voice.xml` i naprawiono limit dokumentacji; nie zmieniano konfiguracji telefonu.

## Emulator

Zlecono bezpieczne testy `medium_phone` na wyłącznym identyfikatorze `emulator-5586` (profile audio, ustawienia modelu/głosu, nawigacja, polski UI, STOP). Wynik: **oczekuje na receipt** `aicalls-emulator-safe-instrumentation-20261114-v1.json`; nie uznawaj ich za PASS bez tego pliku. Nigdy nie kieruj ADB/instrumentacji do prywatnego S22.

## Tor audio i jakość

- Android STT → Gemma 4 E2B → Android TTS PL: bazowy punkt odniesienia.
- Gemma Audio Direct → Gemma 4 E2B → Android TTS PL: jedno rzeczywiste połączenie użytkownika działa jako MVP, ale brak pełnej kwalifikacji; poprawki P1 (limit przechwytywania ~29 s, timeout 90 s, odzyskiwanie błędnej transkrypcji) i wyścig STOP wymagają finalnej weryfikacji na S22.
- Parakeet PL: kod/testy obecne, natywna gotowość i jakość na S22 niepotwierdzona. Piper: silnik może być zainstalowany, lecz **polski głos offline niepotwierdzony**. Nie uznawaj brakujących pakietów za zaliczone testy. Kokoro/Pocket TTS niekwalifikowane dla polskiego.
- Rejestr `benchmarks/audio/AUDIO_ROUTE_QUALITY.md`, implementacja `scripts/aicall_tools/diagnostics/audio_route_quality_report.py`. WER, latencje, naturalność, przerwania i zakończenie połączenia wymagają prawdziwych pomiarów; testów syntetycznych nie mieszaj z rozmowami.

## Ostatni etap — tylko po powrocie S22 i jawnej zgodzie użytkownika

Telefon ma pozostać bez zmian do czasu zgody na instalację APK. W stanie IDLE zweryfikować build, aktualny profil i polski głos TTS, wykonać pojedynczy kontrolowany scenariusz 4–6 wymian: długa mowa/szum/silence, korekta, barge-in, STOP, naturalne „do widzenia” i faktyczne END_CALL; ocenić polską transkrypcję, opóźnienia i odsłuch. Jeśli Piper/Parakeet bez wymaganych plików, zapisać `not_available`, nie wymuszać instalacji. WAV, transkrypty i numery telefonów pozostają prywatne.

**Zasady:** bez nieautoryzowanych połączeń, install/restart, Shizuku, ADB na S22, Action/Codex/Work/schedulera. Nie wymyślaj statusów. Później osobno jakość dialogu i kampanie G6.1 (PHYSICAL_OPEN).
