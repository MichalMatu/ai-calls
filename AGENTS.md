# AI Calls — agenci

- **Tylko GSM bez Shizuku:** [plan](docs/PROJECT.md). Przenieść istniejący Samsung RX/TX do własnego procesu `shell` uruchamianego po zgodzie ADB; SIP/PJSUA2 i reszta prac WSTRZYMANE.
- ChatGPT: GitHub Connector + [Mac Local Agent](docs/operations/LOCAL_AGENT.md); bez Codex/Work/Actions/nowych schedulerów.
- `main` i działający APK nietknięte; kod tylko `feature/s8-no-shizuku`. Existing Code First → mały patch → celowane testy → receipt.
- Bez utraty uprawnień: sprawdź UID, exact Call, generację, STOP i rozłączenie hosta. Shizuku fallback zachować do kwalifikacji S22. Realny Call/instalacja tylko za zgodą, IDLE i rollbackiem. Bez PII/WAV w Git. [SECURITY](docs/architecture/SECURITY.md).
