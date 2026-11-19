# Rozwój i standard QA

**Jeden flow:** golden tag = rollback; `main` = ostatni zatwierdzony kod. Aktualny HEAD → gałąź jednego celu → mały diff → testy → PR → merge → nowy tag po kwalifikacji. Backlog tylko w [PROJECT](../PROJECT.md).

- **G0 — start:** sprawdź HEAD/main/tag, AGENTS, zakres, IDLE i zgodę na sprzęt; nie przerywaj aktywnego Call.
- **G1 — każda zmiana:** `test-profile.sh focus/changed`, `preflight`, `git diff --check`; zero nowych ostrzeżeń w dotkniętym kodzie.
- **G2 — kod przed merge:** `bash scripts/bin/test-profile.sh full` (JVM, Lint, Python, audio, security) i `device-build`; wymagane 0 błędów Lint i PASS testów. Same dokumenty: `preflight` i linki.
- **G3 — GSM/UI/transport na S22+:** tylko IDLE, backup APK → instalacja `.s8lab` → hash → klucz ADB → statusy → start hosta z APK (bez Mac bootstrapu). Połączenia i PCM RX/TX wyłącznie za osobną zgodą; emulator ≠ test GSM.
- **G4 — golden:** lista PASS/OPEN w [PROJECT](../PROJECT.md), evidence QA w PR, rollback, commit `main` i niezmienny tag; OPEN nigdy nie oznacza PASS.

Bez Actions, Codex, Work, schedulerów i `WRITE_SECURE_SETTINGS`. [Mac Agent](LOCAL_AGENT.md) służy do wykonania QA.
