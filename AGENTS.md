# AI Calls — zasady pracy agentów

- [docs/README.md](docs/README.md) → [PROJECT](docs/PROJECT.md). Czytaj wyłącznie pliki dla zadania; kod to źródło implementacji, Git/receipts to historia. Docs: tylko blokady i nieoczywiste kontrakty.
- Czysty `main`; kod, Git i testy bezpośrednio w Codex. Bez Mac Local Agenta, Work, Actions, branchy, force-push i kolizji zadań.
- Existing Code First; `preflight`, potem celowane testy. APK tylko przed autoryzowaną instalacją. Nie dodawaj sztucznych ograniczeń ani zbędnych potwierdzeń.
- Exact app-owned Call/grant/SIM, STOP i prywatność obowiązkowe. Realny Call/instalacja: jawna zgoda, IDLE, zweryfikowany S22. Bez PII/WAV w Git i logach.
- Bez AndroidTest/`adb uninstall` na prywatnym S22; instrumentacyjne tylko na emulatorze. Bez `FLAG_SECURE`. Brak próby = PHYSICAL_OPEN.
- Bez checkpointów/handoffów; dokumenty aktualizuj tylko po zmianie kontraktu/blokady. [SECURITY](docs/architecture/SECURITY.md).
