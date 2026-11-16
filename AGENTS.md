# AI Calls — zasady pracy agentów

- [Docs](docs/README.md) → [PROJECT](docs/PROJECT.md); kod = implementacja, Git/receipts = dowody.
- **ChatGPT (czat):** GitHub Connector + istniejący Local Agent na wybranym hoście; komendy/testy przez agenta, bez Codex. [LOCAL_AGENT](docs/operations/LOCAL_AGENT.md).
- **Codex lokalny:** kod, Git i testy bezpośrednio na hoście, bez Local Agenta. [DEVELOPMENT](docs/operations/DEVELOPMENT.md).
- Branche: `main` = C1–C3/G6.1/EN E2E, `feature/s8-no-shizuku` = S8. Czysty checkout, Existing Code First, preflight → testy celowane; bez Work, Actions, force-push, kolizji i nowych schedulerów.
- Exact app-owned Call/grant/SIM, STOP i prywatność. Realny Call/instalacja: zgoda, IDLE, zweryfikowany S22. Bez PII/WAV w Git/logach.
- Bez AndroidTest/`adb uninstall` na S22; instrumentacyjne tylko na emulatorze. Bez `FLAG_SECURE`; brak próby = PHYSICAL_OPEN.
- Bez zbędnych ograniczeń i potwierdzeń. Docs: kontrakty/blokady; [SECURITY](docs/architecture/SECURITY.md).
