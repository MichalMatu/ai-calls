# AI Calls — handoff

`MichalMatu/ai-calls`: `main` `6910caba`, PR #29 `541b12f6` (nie scalać przed gate). [Plan](../PROJECT.md), [system](../architecture/SYSTEM.md), [security](../architecture/SECURITY.md).

S22+ Android 16: fizyczne GSM RX/TX/Gemma/TTS/END_CALL PASS z wcześniejszego odbioru. 19.11 parowanie przez powiadomienie PASS, status ADB/GSM działa, klucz przetrwał reboot. Po restarcie debugowanie bezprzewodowe wymaga ręcznego włączenia.

**Host PASS:** po reboot host offline, przycisk AI Calls uruchomił go bez Mac ADB. **QA OPEN:** naprawiono 89 tłumaczeń, Lint 0 errors; domykane testy Python. Bez `WRITE_SECURE_SETTINGS`.

GitHub Connector + [Local Agent](LOCAL_AGENT.md), bez Codex/Work/Actions/schedulerów/SIP/PJSUA2. GSM tylko przy IDLE i zgodzie, z rollbackiem.
