# AI Calls — agenci

- Tylko GSM bez Shizuku: [plan](docs/PROJECT.md), [handoff](docs/operations/HANDOFF_NEXT_CHAT.md). `main` = PR #23; kandydat PR #29 na gałęzi, nie scalać bez finalnego QA.
- S22+ Android 16: exact Call/SIM → STT → lokalna Gemma/TTS. Binder host wymaga UID 2000 (`shell`) z autoryzowanego ADB, nie uprawnień APK.
- GitHub Connector + Mac Local Agent; Existing Code First → testy → gate → checkpoint. Bez Codex/Work/Actions/schedulerów/SIP/PJSUA2.
- Fizyczne GSM/deploy wyłącznie IDLE, za zgodą, z rollbackiem. STOP/takeover; END_CALL po TTS; bez PII/WAV i kluczy w Git. [SECURITY](docs/architecture/SECURITY.md).
