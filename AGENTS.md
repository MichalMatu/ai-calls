# AI Calls — agenci

- **Jedyny [plan](docs/PROJECT.md): P0 GSM** na działającym Samsung/Shizuku RX/TX + Gemma; **P1 lokalny SIP później**. Archiwa nie są backlogiem.
- ChatGPT: GitHub Connector + [Mac Local Agent](docs/operations/LOCAL_AGENT.md); bez Codex, Work, Actions, schedulerów.
- `main` stabilny; rozwój `feature/s8-no-shizuku`. Sprawdź HEAD/czystość. Existing Code First → patch → test celowany → runtime, bez zbędnych audytów.
- S22: Call/grant/instalacja tylko po zgodzie i przy IDLE, ze sprawdzeniem APK i rollbackiem. Bez globalnego hangup, uninstall, PII/WAV/transkryptów w Git. STOP odcina stary TX. [SECURITY](docs/architecture/SECURITY.md).
