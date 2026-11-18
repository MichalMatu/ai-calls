# AI Calls — agenci

- **Tylko GSM bez Shizuku:** [jedyny plan](docs/PROJECT.md), [handoff](docs/operations/HANDOFF_NEXT_CHAT.md). Po PR #23 kod na `main`; nowe zmiany na małej gałęzi.
- Telefon: exact Call/SIM → `AndroidIncomingAssistant` → `AndroidConversationRuntime` → Gemma/TTS; Binder host UID 2000 przez Mac ADB. Brak samodzielności po reboocie.
- GitHub Connector + Mac Local Agent; Existing Code First → testy → QA → merge. Bez Codex/Work/Actions, schedulerów i SIP/PJSUA2.
- STOP/takeover, UID/generacja, END_CALL po TTS. Fizyczny Call/deploy tylko IDLE, za zgodą i z rollbackiem. Bez PII/WAV w Git. [SECURITY](docs/architecture/SECURITY.md).
