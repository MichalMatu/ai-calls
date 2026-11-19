# AI Calls — agenci

- Tylko GSM bez Shizuku. [Plan](docs/PROJECT.md) to jedyna roadmapa; [QA](docs/operations/DEVELOPMENT.md), [handoff](docs/operations/HANDOFF_NEXT_CHAT.md).
- S22+ Android 16: host Binder UID 2000 przez lokalne ADB; po reboocie debugowanie włącza użytkownik.
- Kod: main → gałąź → celowane testy → gate → PR → merge/tag. GitHub + Mac Local Agent; bez Codex/Work/Actions/schedulerów/SIP/PJSUA2.
- GSM/deploy tylko IDLE, za zgodą i z rollbackiem. Exact Call/SIM, STOP/takeover, END_CALL po TTS. Bez PII/WAV/kluczy w Git i FLAG_SECURE.
