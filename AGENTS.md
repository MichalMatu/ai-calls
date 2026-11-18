# AI Calls — agenci

- **Tylko GSM bez Shizuku:** [stan i QA](docs/PROJECT.md). Działający RX→STT→Gemma→TTS→TX przez Binder host `shell` (UID 2000) z autoryzowanego ADB. SIP/PJSUA2 i eksperymenty poza zakresem.
- ChatGPT: GitHub Connector + [Mac Local Agent](docs/operations/LOCAL_AGENT.md); bez Codex/Work/Actions i schedulerów.
- Kod GSM po odbiorze PR #23 na `main`; nowe zmiany przez małą gałąź i testy. Deploy S8 Lab: `scripts/bin/install-s8-gsm-lab.sh --install`, tylko IDLE, z zachowaniem APK do rollback. Mac `s8_gsm_reconnect.py` uruchamia host po restarcie przez autoryzowane ADB.
- Bezpieczeństwo: exact Call, UID, generacja, STOP, potwierdzony `END_CALL` po TTS; nigdy nie zakładać autonomii po reboocie bez Maca. Testy fizyczne za zgodą, bez PII/WAV w Git. [SECURITY](docs/architecture/SECURITY.md).
