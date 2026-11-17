# AI Calls — zasady agentów

- **Jedyny aktywny plan:** [PROJECT](docs/PROJECT.md). **P0 GSM na S22+** z istniejącym Samsung/Shizuku RX/TX i STT → Gemma → TTS; **P1 lokalny SIP dopiero po GSM**. Żaden checkpoint, benchmark ani handoff nie tworzy nowego backlogu.
- ChatGPT: GitHub Connector + Mac Local Agent ([protokół](docs/operations/LOCAL_AGENT.md)); bez Codex, Work, Actions ani schedulerów.
- `main` stabilny, prace na `feature/s8-no-shizuku`; sprawdź HEAD i czysty checkout. Existing Code First → mały patch → test celowany → wynik na S22. Bez zbędnych audytów.
- Zachowaj działający tor GSM/Shizuku; nie zastępuj go VoIP. Nie deklaruj pełnego GSM PASS na podstawie syntezy.
- Testy rzeczywistych połączeń, grantów i instalacji tylko po zgodzie, przy IDLE, z kontrolą APK/rollback. Bez globalnego hangup, uninstall, PII, WAV i transkryptów w Git. STOP odcina stare audio. [SECURITY](docs/architecture/SECURITY.md).
