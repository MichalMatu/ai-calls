# AI Calls — agenci

- Tylko GSM bez Shizuku. [Plan](docs/PROJECT.md), [QA](docs/operations/DEVELOPMENT.md), [handoff](docs/operations/HANDOFF_NEXT_CHAT.md).
- Architektura: `app` = Android/UI/orkiestracja; `conversation-core` = stany i Lab JVM; `conversation-tools` = kontrakty; `audio-bridge` = PCM; `privileged-helper` = system; `realtime-client` = transport. Core/tools bez Android/UI.
- Praca tylko GitHub + Mac Local Agent: `main` → mała gałąź → testy → `preflight`/`full`/`device-build` → PR. Bez Work/Codex/Actions/schedulerów.
- Test Lab syntetyczny, bez skutków; `lab-ui` tylko emulator. Kompilacja testów nie dowodzi PASS UI/GSM.
- S22+: instalacja i host wyłącznie IDLE, za zgodą i z rollbackiem. Połączenia za osobną zgodą. Exact Call/SIM, STOP/takeover, END_CALL po TTS.
- Bez PII/WAV/kluczy w Git i logach. Bez `FLAG_SECURE` i nieudowodnionych PASS.
