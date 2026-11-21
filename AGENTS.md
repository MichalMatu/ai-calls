# AI Calls — agenci

- MVP: GSM na S22+/Android 16 bez Shizuku. [Stan](docs/PROJECT.md) · [QA](docs/operations/DEVELOPMENT.md).
- Moduły: `app` (Android/UI), `conversation-core` (JVM/stany), `conversation-tools` (kontrakty), `audio-bridge` (PCM), `privileged-helper` (system), `realtime-client` (transport). Core/tools bez Androida.
- Tryb: GitHub + Mac Local Agent; bez Codex, Work, Actions i schedulerów. Stałe gałęzie: `main` (kod) i `agent-control` (zadania/raporty). [Agent](docs/operations/LOCAL_AGENT.md).
- Przed pracą sprawdź SHA, czystość lokalnego repo i świeży raport agenta. Nie nadpisuj zmian; QA: preflight → JVM/Lab → S22+ → raport przypięty do SHA.
- GSM: dokładny Call/SIM, IDLE przed instalacją/hostem, STOP/takeover, END_CALL po TTS, rollback; połączenia tylko po zgodzie.
- PII, WAV i klucze poza Git i logami. Bez nieudowodnionych PASS.
