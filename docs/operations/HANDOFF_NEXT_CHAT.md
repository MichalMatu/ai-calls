# AI Calls — handoff

`MichalMatu/ai-calls`: PR #29 `18f4c72b` przeszedł pełny gate; golden checkpoint po scaleniu w `main`. [Plan](../PROJECT.md), [system](../architecture/SYSTEM.md), [security](../architecture/SECURITY.md).

S22+ Android 16: fizyczne GSM RX/TX/Gemma/TTS/END_CALL PASS z wcześniejszego odbioru. 19.11 parowanie przez powiadomienie PASS, status ADB/GSM działa, klucz przetrwał reboot. Po restarcie debugowanie bezprzewodowe wymaga ręcznego włączenia.

**PASS host i QA:** po reboot (ADB ON) przycisk w AI Calls sam uruchomił UID 2000 Binder; bez Macowego bootstrapu. 1370 testów Gradle i 463 Python PASS, Lint 0 errors, preflight PASS. APK z backupem i zgodnym SHA na S22+. Dług: ostrzeżenia lint. Bez `WRITE_SECURE_SETTINGS`.

GitHub Connector + [Local Agent](LOCAL_AGENT.md), bez Codex/Work/Actions/schedulerów/SIP/PJSUA2. GSM tylko przy IDLE i zgodzie, z rollbackiem.
