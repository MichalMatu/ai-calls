# Rozwój i standard QA

**Jeden flow:** `main` → mała gałąź → testy → PR → merge; stary golden = rollback. Jedyny backlog: [PROJECT](../PROJECT.md).

- **G0:** sprawdź HEAD, tag, IDLE i zakres zgody; nie przerywaj Call.
- **G1:** `focus/changed`, `preflight`, `git diff --check`; brak nowych ostrzeżeń.
- **G2:** przed merge `full` (JVM, Lint, Python, audio, security, granice modułów) i `device-build`; 0 nowych błędów. Same docs: `preflight`.
- **G3:** UI `lab-ui` wyłącznie emulator; kompilacja AndroidTest nie dowodzi wykonania. S22+ tylko IDLE: kopia APK → instalacja `.s8lab` → podpis/hash → host UID 2000/Binder → smoke; połączenia za osobną zgodą.
- **G4:** PASS/OPEN i SHA w [PROJECT](../PROJECT.md); golden dopiero po odbiorze. OPEN ≠ PASS.

Bez Actions, Work, Codex, schedulerów, Shizuku i `WRITE_SECURE_SETTINGS`. Testy wykonuje [Mac Local Agent](LOCAL_AGENT.md).
