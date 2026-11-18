# AI Calls — agenci

- **Tylko GSM bez Shizuku:** [plan](docs/PROJECT.md). Przenieść istniejący Samsung RX/TX do własnego procesu `shell` uruchamianego po zgodzie ADB; SIP/PJSUA2 i reszta prac WSTRZYMANE.
- ChatGPT: GitHub Connector + [Mac Local Agent](docs/operations/LOCAL_AGENT.md); bez Codex/Work/Actions/nowych schedulerów.
- `main` nietknięte; kod tylko `feature/s8-no-shizuku`. Deploy przez `scripts/bin/install-s8-gsm-lab.sh --install` (host+UID gate); po reboocie nasłuch Mac `s8_gsm_reconnect.py` (ADB z autoryzacją) lub ręczny bootstrap. Existing Code First → testy → receipt.
- Bez utraty uprawnień: sprawdź UID, exact Call, generację, STOP i rozłączenie hosta. Shizuku fallback zachować do kwalifikacji S22. Realny Call/instalacja tylko za zgodą, IDLE i rollbackiem. Bez PII/WAV w Git. [SECURITY](docs/architecture/SECURITY.md).
