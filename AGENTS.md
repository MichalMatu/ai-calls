# AI Calls — instrukcje agentów

- **Jedyny plan:** [PROJECT](docs/PROJECT.md). **P0 GSM** z istniejącym Samsung/Shizuku RX/TX i Gemma; **P1 lokalny SIP dopiero potem**. Checkpointy i benchmarki nie są backlogiem.
- ChatGPT: GitHub Connector + [Mac Local Agent](docs/operations/LOCAL_AGENT.md); bez Codex, Work, Actions ani schedulerów.
- `main` stabilny, prace na `feature/s8-no-shizuku`. Sprawdź HEAD i czystość. Existing Code First → mała poprawka → test celowany → runtime na S22; bez zbędnych audytów.
- Zachowaj działający tor GSM/Shizuku; syntetyczny RTP ≠ fizyczny GSM.
- Call, granty i instalacje na S22 tylko za zgodą, przy IDLE, z kontrolą APK i rollbackiem. Bez globalnego hangup, uninstall ani PII/WAV/transkryptów w Git. STOP odcina stary TX. [SECURITY](docs/architecture/SECURITY.md).
